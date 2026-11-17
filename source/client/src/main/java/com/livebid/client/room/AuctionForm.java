package com.livebid.client.room;

import com.livebid.common.dto.AuctionDraft;

/**
 * Kiểm tra dữ liệu ADMIN nhập trên form tạo/sửa phiên và đổi thành {@link AuctionDraft}.
 *
 * <p>Lớp thuần, không phụ thuộc Swing: form lấy chuỗi trong ô nhập và epoch ms từ bộ chọn ngày giờ rồi gọi
 * {@link #toDraft}. Quy tắc ở đây trùng với kiểm tra phía server ({@code AuctionServiceImpl#validateDraft}):
 * tên 1–200 ký tự, mô tả tối đa 5.000 ký tự, giá khởi điểm và bước giá từ 1đ đến
 * {@value #MAX_PRICE}đ, giờ kết thúc sau giờ bắt đầu và ở tương lai. Server vẫn kiểm tra lại; lớp này chỉ
 * giúp báo lỗi ngay trên form.
 */
public final class AuctionForm {

    /** Độ dài tối đa của tên sản phẩm, khớp {@code AuctionServiceImpl.MAX_TITLE_LENGTH}. */
    public static final int MAX_TITLE_LENGTH = 200;

    /** Độ dài tối đa của mô tả, khớp {@code AuctionServiceImpl.MAX_DESCRIPTION_LENGTH}. */
    public static final int MAX_DESCRIPTION_LENGTH = 5_000;

    /** Giá trị tiền tối đa, khớp {@code AuctionSettings.MAX_PRICE}. */
    public static final long MAX_PRICE = 1_000_000_000_000L;

    private AuctionForm() {
    }

    /**
     * Đổi dữ liệu form thành {@link AuctionDraft} đã chuẩn hoá.
     *
     * @param title         tên sản phẩm, được cắt khoảng trắng hai đầu
     * @param description   mô tả, được cắt khoảng trắng hai đầu
     * @param startingPrice giá khởi điểm dạng người dùng gõ ({@code 280000}, {@code 280.000}, …)
     * @param minIncrement  bước giá dạng người dùng gõ
     * @param startsAt      thời điểm bắt đầu (epoch ms)
     * @param endsAt        thời điểm kết thúc (epoch ms)
     * @param now           giờ hiện tại (epoch ms) để kiểm tra giờ kết thúc ở tương lai
     * @throws IllegalArgumentException khi một trường không hợp lệ; câu lỗi bằng tiếng Việt
     */
    public static AuctionDraft toDraft(String title, String description, String startingPrice,
            String minIncrement, long startsAt, long endsAt, long now) {
        String cleanTitle = title == null ? "" : title.strip();
        String cleanDescription = description == null ? "" : description.strip();
        if (cleanTitle.isEmpty()) {
            throw new IllegalArgumentException("Tên sản phẩm không được để trống");
        }
        if (cleanTitle.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Tên sản phẩm tối đa " + MAX_TITLE_LENGTH + " ký tự");
        }
        if (cleanDescription.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Mô tả tối đa " + MAX_DESCRIPTION_LENGTH + " ký tự");
        }

        long start = MoneyFormat.parse(startingPrice);
        checkPrice(start, "Giá khởi điểm");
        long increment = MoneyFormat.parse(minIncrement);
        checkPrice(increment, "Bước giá");

        if (endsAt <= startsAt) {
            throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu");
        }
        if (endsAt <= now) {
            throw new IllegalArgumentException("Giờ kết thúc phải ở tương lai");
        }
        return new AuctionDraft(cleanTitle, cleanDescription, start, increment, startsAt, endsAt);
    }

    private static void checkPrice(long value, String name) {
        if (value <= 0 || value > MAX_PRICE) {
            throw new IllegalArgumentException(name + " phải từ 1đ đến "
                    + MoneyFormat.format(MAX_PRICE));
        }
    }
}
