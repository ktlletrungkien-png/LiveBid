package com.livebid.client.ui;

import com.livebid.client.ClientConfig;
import com.livebid.client.net.UdpDiscoveryClient;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

/**
 * Màn hình đầu tiên: tìm server bằng UDP broadcast hoặc nhập IP thủ công rồi kết nối TCP.
 *
 * <p>Ô IP/cổng luôn hiện vì mạng trường và Wi-Fi công cộng hay chặn broadcast. Nếu
 * {@code server.host} trong {@code client.properties} có sẵn thì điền trước.
 *
 * <p>Việc tìm server và kết nối chạy trong {@link SwingWorker}, giao diện không bị đứng khi mạng chậm.
 */
final class ConnectPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MainFrame mainFrame;
    private final ClientConfig config;

    private final JTextField hostField = new JTextField(18);
    private final JTextField portField = new JTextField(6);
    private final JButton discoverButton = new JButton("Tìm server");
    private final JButton connectButton = new JButton("Kết nối");
    private final JLabel messageLabel = new JLabel(" ");

    ConnectPanel(MainFrame mainFrame, ClientConfig config) {
        this.mainFrame = mainFrame;
        this.config = config;
        setBorder(new EmptyBorder(24, 24, 24, 24));
        setLayout(new GridBagLayout());

        hostField.setText(config.serverHost());
        portField.setText(Integer.toString(config.tcpPort()));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.LINE_START;

        JLabel title = new JLabel("Kết nối tới server LiveBid");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        add(title, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1;
        add(new JLabel("Địa chỉ IP:"), gbc);
        gbc.gridx = 1;
        add(hostField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        add(new JLabel("Cổng TCP:"), gbc);
        gbc.gridx = 1;
        add(portField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JPanel buttons = new JPanel();
        buttons.add(discoverButton);
        buttons.add(connectButton);
        add(buttons, gbc);

        gbc.gridy = 4;
        messageLabel.setFont(messageLabel.getFont().deriveFont(Font.PLAIN));
        add(messageLabel, gbc);

        discoverButton.addActionListener(e -> discover());
        connectButton.addActionListener(e -> connect());
        hostField.addActionListener(e -> connect());
        portField.addActionListener(e -> connect());

        if (config.autoDiscover()) {
            messageLabel.setText("Chưa biết địa chỉ server: bấm \"Tìm server\" hoặc nhập IP thủ công.");
        }
    }

    /** Chạy tìm server trên luồng nền, xong thì điền IP/cổng tìm được. */
    private void discover() {
        setBusy(true, "Đang tìm server trong mạng LAN...");
        int discoveryPort = config.discoveryPort();
        int timeoutMillis = config.discoveryTimeoutMillis();
        int retries = config.discoveryRetries();
        UdpDiscoveryClient client = new UdpDiscoveryClient();

        new SwingWorker<Optional<UdpDiscoveryClient.DiscoveredServer>, Void>() {
            @Override
            protected Optional<UdpDiscoveryClient.DiscoveredServer> doInBackground() {
                return client.discover(discoveryPort, timeoutMillis, retries);
            }

            @Override
            protected void done() {
                setBusy(false, " ");
                try {
                    Optional<UdpDiscoveryClient.DiscoveredServer> found = get();
                    if (found.isPresent()) {
                        UdpDiscoveryClient.DiscoveredServer server = found.get();
                        hostField.setText(server.address().getHostAddress());
                        portField.setText(Integer.toString(server.reply().tcpPort()));
                        setMessage("Đã tìm thấy server \"" + server.reply().name() + "\".");
                    } else {
                        setMessage("Không tìm thấy server. Hãy nhập IP thủ công.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    setMessage("Lỗi khi tìm server: " + MainFrame.describeError(e));
                }
            }
        }.execute();
    }

    /** Kết nối TCP trên luồng nền; thành công thì mở màn hình đăng nhập. */
    private void connect() {
        String host = hostField.getText().trim();
        if (host.isEmpty()) {
            setMessage("Hãy bấm \"Tìm server\" hoặc nhập IP server.");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            setMessage("Cổng TCP phải là số trong khoảng 1–65535.");
            return;
        }
        if (port <= 0 || port > 65_535) {
            setMessage("Cổng TCP phải là số trong khoảng 1–65535.");
            return;
        }

        setBusy(true, "Đang kết nối tới " + host + ":" + port + "...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                mainFrame.connection().connect(host, port);
                return null;
            }

            @Override
            protected void done() {
                setBusy(false, " ");
                try {
                    get();
                    mainFrame.onConnected(host, port);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    setMessage("Không kết nối được: " + MainFrame.describeError(e));
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy, String message) {
        discoverButton.setEnabled(!busy);
        connectButton.setEnabled(!busy);
        hostField.setEnabled(!busy);
        portField.setEnabled(!busy);
        setMessage(message);
    }

    private void setMessage(String message) {
        messageLabel.setText(message == null || message.isBlank() ? " " : message);
    }
}
