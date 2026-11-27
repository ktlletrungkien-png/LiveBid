package com.livebid.client.ui;

import com.livebid.client.image.ClientImages;
import com.livebid.client.image.ImageFiles;
import com.livebid.client.room.AuctionForm;
import com.livebid.common.dto.AuctionDraft;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.Role;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.border.EmptyBorder;

/**
 * Hộp thoại tạo hoặc sửa phiên, giống form {@code #/create} của web.
 *
 * <p>Ngày giờ nhập bằng {@link JSpinner} với {@link SpinnerDateModel} theo định dạng
 * {@code dd/MM/yyyy HH:mm}; giá trị trả về là epoch ms. Nút lưu kiểm tra dữ liệu bằng
 * {@link AuctionForm#toDraft} và chỉ đóng khi hợp lệ, nên lỗi hiện ngay trong hộp thoại.
 *
 * <p><b>USER</b> đăng phiên: giờ bắt đầu phải cách hiện tại ít nhất {@value #USER_LEAD_MINUTES} phút và phiên
 * được quản trị viên duyệt trước khi lên sàn. <b>ADMIN</b> không bị giới hạn này.
 *
 * <p>Ảnh đại diện (không bắt buộc): chọn file là tải lên ngay để lấy mã ảnh, có xem trước và nút bỏ ảnh;
 * không cho lưu khi ảnh còn đang tải. Khi sửa mà không đụng tới ảnh thì bản nháp không mang trường ảnh, nên
 * server giữ ảnh cũ.
 *
 * <p>Hộp thoại chỉ thu thập dữ liệu: việc gọi {@code CREATE_AUCTION}/{@code UPDATE_AUCTION} do
 * {@link MainFrame} làm để giữ một chỗ xử lý lỗi nghiệp vụ.
 */
final class AuctionFormDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final String DATE_PATTERN = "dd/MM/yyyy HH:mm";

    /** USER phải đăng phiên bắt đầu sau hiện tại ít nhất ngần này phút. */
    static final int USER_LEAD_MINUTES = 30;

    private static final long USER_LEAD_MILLIS = USER_LEAD_MINUTES * 60_000L;
    private static final long ONE_HOUR = 3_600_000L;

    private final JTextField titleField = UiTheme.field(new JTextField(30));
    private final JTextArea descriptionArea = UiTheme.field(new JTextArea(4, 30));
    private final JTextField startingPriceField = UiTheme.field(new JTextField(14));
    private final JTextField incrementField = UiTheme.field(new JTextField(14));
    private final JSpinner startsAtSpinner;
    private final JSpinner endsAtSpinner;
    private final JLabel messageLabel = new JLabel(" ");
    private final Role role;
    private final transient ClientImages images;
    /** Ảnh của phiên lúc mở form (null khi tạo mới hoặc chưa có ảnh) và ảnh đang chọn. */
    private final String initialImageId;
    private String imageId;
    private final ImageThumb imagePreview;
    private final JButton chooseImageButton = UiTheme.secondary("Chọn ảnh");
    private final JButton removeImageButton = UiTheme.secondary("Bỏ ảnh");
    private final JLabel imageStatus = UiTheme.muted(" ");
    private boolean uploading;

    private AuctionDraft result;

    private AuctionFormDialog(Window owner, String heading, AuctionView existing, long now, Role role,
            String submitLabel, ClientImages images) {
        super(owner, heading, Dialog.ModalityType.APPLICATION_MODAL);
        this.role = role;
        this.images = images;
        this.initialImageId = existing == null ? null : existing.imageId();
        this.imageId = initialImageId;
        this.imagePreview = new ImageThumb(images, 220, 140);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        if (existing == null) {
            long start = now + (role == Role.ADMIN ? 60_000L : USER_LEAD_MILLIS + 5 * 60_000L);
            startsAtSpinner = dateSpinner(start);
            endsAtSpinner = dateSpinner(start + ONE_HOUR);
        } else {
            titleField.setText(existing.title());
            descriptionArea.setText(existing.description());
            startingPriceField.setText(Long.toString(existing.startingPrice()));
            incrementField.setText(Long.toString(existing.minIncrement()));
            startsAtSpinner = dateSpinner(existing.startsAt());
            endsAtSpinner = dateSpinner(existing.endsAt());
        }
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(UiTheme.CANVAS);
        content.setBorder(new EmptyBorder(22, 26, 18, 26));

        JLabel title = new JLabel(heading);
        title.setFont(UiTheme.sans(Font.BOLD, 20f));
        title.setForeground(UiTheme.INK);
        content.add(title, BorderLayout.NORTH);
        content.add(buildForm(), BorderLayout.CENTER);

        messageLabel.setForeground(UiTheme.RUBY);
        messageLabel.setFont(UiTheme.sans(Font.PLAIN, 13f));

        JPanel footer = new JPanel(new BorderLayout(8, 6));
        footer.setOpaque(false);
        footer.add(messageLabel, BorderLayout.NORTH);
        if (role != Role.ADMIN) {
            footer.add(UiTheme.muted("Phiên sẽ được quản trị viên duyệt trước khi lên sàn."), BorderLayout.CENTER);
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancelButton = UiTheme.secondary("Hủy");
        JButton saveButton = UiTheme.primary(submitLabel);
        buttons.add(cancelButton);
        buttons.add(saveButton);
        footer.add(buttons, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);

        cancelButton.addActionListener(e -> dispose());
        saveButton.addActionListener(e -> save());
        chooseImageButton.addActionListener(e -> chooseImage());
        removeImageButton.addActionListener(e -> {
            imageId = null;
            imagePreview.showPreview(null, null);
            imageStatus.setText(" ");
            updateImageButtons();
        });
        imagePreview.show(imageId);
        updateImageButtons();
        getRootPane().setDefaultButton(saveButton);

        setContentPane(content);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /**
     * Mở hộp thoại tạo phiên; trả về dữ liệu đã kiểm tra nếu người dùng bấm nút lưu.
     *
     * @param owner cửa sổ cha (có thể {@code null})
     * @param now   giờ hiện tại (epoch ms) để đặt giờ mặc định và kiểm tra tương lai
     * @param role  vai trò người đăng: USER bị giới hạn giờ bắt đầu và phải chờ duyệt
     * @param images nguồn ảnh (tải ảnh lên, xem trước)
     */
    static Optional<AuctionDraft> showCreate(Window owner, long now, Role role, ClientImages images) {
        String submit = role == Role.ADMIN ? "Tạo phiên" : "Gửi phiên";
        return new AuctionFormDialog(owner, "Đăng phiên đấu giá", null, now, role, submit, images).open();
    }

    /** Mở hộp thoại sửa phiên (ADMIN sửa phiên SCHEDULED, USER sửa phiên của mình đang chờ duyệt). */
    static Optional<AuctionDraft> showEdit(Window owner, AuctionView auction, long now, Role role,
            ClientImages images) {
        return new AuctionFormDialog(owner, "Sửa phiên #" + auction.auctionId(), auction, now, role,
                "Lưu thay đổi", images).open();
    }

    private Optional<AuctionDraft> open() {
        setVisible(true);
        return Optional.ofNullable(result);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 0, 2, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridx = 0;
        gbc.gridwidth = 2;

        int row = 0;
        row = addField(form, gbc, row, "Tên sản phẩm", titleField);
        row = addField(form, gbc, row, "Mô tả", new JScrollPane(descriptionArea));

        gbc.gridwidth = 1;
        gbc.insets = new Insets(6, 0, 2, 8);
        row = addPair(form, gbc, row, "Giá khởi điểm (đ)", startingPriceField, "Bước giá (đ)", incrementField);
        row = addPair(form, gbc, row, "Giờ bắt đầu", startsAtSpinner, "Giờ kết thúc", endsAtSpinner);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(8, 0, 0, 0);
        form.add(UiTheme.muted("Tiền nhập như 280000, 280.000 hoặc 280 000."), gbc);

        gbc.insets = new Insets(6, 0, 2, 0);
        addField(form, gbc, row + 1, "Ảnh đại diện (không bắt buộc)", buildImagePanel());
        return form;
    }

    private JPanel buildImagePanel() {
        JPanel panel = new JPanel(new BorderLayout(14, 0));
        panel.setOpaque(false);
        panel.add(imagePreview, BorderLayout.WEST);

        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new javax.swing.BoxLayout(side, javax.swing.BoxLayout.Y_AXIS));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.add(chooseImageButton);
        buttons.add(javax.swing.Box.createHorizontalStrut(8));
        buttons.add(removeImageButton);
        side.add(buttons);
        side.add(javax.swing.Box.createVerticalStrut(8));
        JLabel hint = UiTheme.muted("JPEG, PNG hoặc WebP, tối đa 2 MB.");
        hint.setAlignmentX(LEFT_ALIGNMENT);
        side.add(hint);
        side.add(javax.swing.Box.createVerticalStrut(4));
        imageStatus.setAlignmentX(LEFT_ALIGNMENT);
        side.add(imageStatus);
        panel.add(side, BorderLayout.CENTER);
        return panel;
    }

    private void updateImageButtons() {
        chooseImageButton.setText(imageId == null ? "Chọn ảnh" : "Đổi ảnh");
        chooseImageButton.setEnabled(!uploading);
        removeImageButton.setVisible(imageId != null);
        removeImageButton.setEnabled(!uploading);
    }

    /** Chọn file, kiểm tra nhanh, hiện xem trước rồi tải lên; thành công thì ghi nhận mã ảnh. */
    private void chooseImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn ảnh đại diện");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("Ảnh (JPEG, PNG, WebP)", ImageFiles.EXTENSIONS));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            ImageFiles.check(file.getName(), file.length());
        } catch (RuntimeException e) {
            imageStatus.setForeground(UiTheme.RUBY);
            imageStatus.setText(e.getMessage());
            return;
        }
        imagePreview.showPreview(readPreview(file), "Không xem trước được");

        uploading = true;
        updateImageButtons();
        imageStatus.setForeground(UiTheme.INK_MUTE);
        imageStatus.setText("Đang tải ảnh lên...");
        String previous = imageId;
        images.upload(file.toPath(), uploadedId -> {
            uploading = false;
            imageId = uploadedId;
            imageStatus.setForeground(UiTheme.SUCCESS);
            imageStatus.setText("Đã tải ảnh lên.");
            updateImageButtons();
        }, error -> {
            uploading = false;
            imageStatus.setForeground(UiTheme.RUBY);
            imageStatus.setText(error);
            imagePreview.showPreview(null, null);
            imagePreview.show(previous); // quay về ảnh trước đó
            updateImageButtons();
        });
    }

    /** Ảnh xem trước đã thu nhỏ; {@code null} nếu Java không đọc được file. */
    private static BufferedImage readPreview(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            return image == null ? null : ImageFiles.shrink(image, 480);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static int addField(JPanel form, GridBagConstraints gbc, int row, String label, Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        form.add(caption(label), gbc);
        gbc.gridy = row + 1;
        form.add(field, gbc);
        return row + 2;
    }

    private static int addPair(JPanel form, GridBagConstraints gbc, int row, String leftLabel, Component left,
            String rightLabel, Component right) {
        gbc.gridx = 0;
        gbc.gridy = row;
        form.add(caption(leftLabel), gbc);
        gbc.gridx = 1;
        form.add(caption(rightLabel), gbc);
        gbc.gridx = 0;
        gbc.gridy = row + 1;
        form.add(left, gbc);
        gbc.gridx = 1;
        form.add(right, gbc);
        return row + 2;
    }

    private static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTheme.sans(Font.BOLD, 12f));
        label.setForeground(UiTheme.INK);
        return label;
    }

    private void save() {
        long now = System.currentTimeMillis();
        long startsAt = valueOf(startsAtSpinner);
        try {
            if (role != Role.ADMIN && startsAt < now + USER_LEAD_MILLIS) {
                throw new IllegalArgumentException(
                        "Giờ bắt đầu phải cách hiện tại ít nhất " + USER_LEAD_MINUTES + " phút");
            }
            if (uploading) {
                throw new IllegalArgumentException("Ảnh đang được tải lên, vui lòng đợi");
            }
            result = withImage(AuctionForm.toDraft(titleField.getText(), descriptionArea.getText(),
                    startingPriceField.getText(), incrementField.getText(),
                    startsAt, valueOf(endsAtSpinner), now));
        } catch (IllegalArgumentException e) {
            messageLabel.setText(e.getMessage());
            pack();
            return;
        }
        dispose();
    }

    /**
     * Gắn ảnh vào bản nháp theo quy tắc của server: không đổi ảnh thì không gửi gì (server giữ ảnh cũ),
     * đổi thì gửi {@code imageId}, bỏ thì gửi {@code removeImage}.
     */
    private AuctionDraft withImage(AuctionDraft draft) {
        if (java.util.Objects.equals(imageId, initialImageId)) {
            return draft;
        }
        return new AuctionDraft(draft.title(), draft.description(), draft.startingPrice(), draft.minIncrement(),
                draft.startsAt(), draft.endsAt(), imageId, imageId == null);
    }

    private static JSpinner dateSpinner(long value) {
        JSpinner spinner = new JSpinner(new SpinnerDateModel(new Date(value), null, null, Calendar.MINUTE));
        spinner.setEditor(new JSpinner.DateEditor(spinner, DATE_PATTERN));
        spinner.setBorder(BorderFactory.createLineBorder(UiTheme.HAIRLINE_INPUT));
        spinner.setFont(UiTheme.sans(Font.PLAIN, 14f));
        spinner.setBackground(Color.WHITE);
        return spinner;
    }

    private static long valueOf(JSpinner spinner) {
        Object value = spinner.getValue();
        return value instanceof Date date ? date.getTime() : 0L;
    }
}
