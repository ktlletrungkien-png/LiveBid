package com.livebid.server.settlement;

import com.livebid.common.rmi.SettlementService;

/**
 * Cách lấy stub {@link SettlementService}. Tách khỏi {@link SettlementClient} để test không cần RMI Registry
 * thật: test chỉ cần trả về một đối tượng giả.
 *
 * <p>Mỗi lần gọi có thể chậm hoặc ném lỗi (Registry chưa chạy, Settlement đã tắt), nên
 * {@code SettlementClient} chỉ gọi nó trên luồng {@code settlement-client} hoặc luồng HTTP, <b>không bao giờ</b>
 * trên luồng executor nghiệp vụ.
 */
@FunctionalInterface
public interface SettlementServiceLocator {

    /** Trả stub RMI đang hoạt động; ném lỗi nếu Settlement/Registry không chạy. */
    SettlementService lookup() throws Exception;
}
