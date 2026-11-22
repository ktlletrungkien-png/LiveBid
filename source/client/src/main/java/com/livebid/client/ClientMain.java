package com.livebid.client;

import com.livebid.client.ui.MainFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

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
        com.livebid.client.ui.UiTheme.install();
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
}
