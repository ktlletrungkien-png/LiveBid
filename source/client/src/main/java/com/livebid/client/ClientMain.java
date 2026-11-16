package com.livebid.client;

import com.livebid.client.ui.MainFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Điểm khởi động desktop client (Swing).
 *
 * <p>Đọc {@code config/client.properties} rồi mở {@link MainFrame}. Mọi việc dựng giao diện chạy trên
 * Event Dispatch Thread; việc mạng nằm trong {@code ServerConnection} và {@code UdpDiscoveryClient}.
 *
 * <p>Chạy: {@code cd source && mvn -q -pl client exec:java}
 */
public final class ClientMain {

    private ClientMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientMain::start);
    }

    private static void start() {
        useSystemLookAndFeel();
        ClientConfig config;
        try {
            config = ClientConfig.load();
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(),
                    "LiveBid - Lỗi cấu hình", JOptionPane.ERROR_MESSAGE);
            return;
        }
        new MainFrame(config).setVisible(true);
    }

    private static void useSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException e) {
            // Giữ look-and-feel mặc định của Java; giao diện vẫn dùng được.
        }
    }
}
