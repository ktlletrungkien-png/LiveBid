package com.livebid.client.ui;

import com.livebid.client.ClientConfig;
import com.livebid.client.net.ConnectionState;
import com.livebid.client.net.RemoteErrorException;
import com.livebid.client.net.ServerConnection;
import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.UserView;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

/**
 * Cửa sổ chính của desktop client: một {@link CardLayout} giữa các màn hình và thanh trạng thái luôn hiện.
 *
 * <p>Các màn hình của M4:
 *
 * <ul>
 *   <li>{@link ConnectPanel}: tìm server bằng UDP hoặc nhập IP thủ công.</li>
 *   <li>{@link LoginPanel}: đăng nhập / đăng ký.</li>
 *   <li>Màn hình tạm "Đã đăng nhập: ..." — danh sách phiên và phòng đấu giá thuộc M5.</li>
 * </ul>
 *
 * <p>Cửa sổ sở hữu một {@link ServerConnection} duy nhất và đóng nó khi thoát. Listener của kết nối chạy
 * trên luồng mạng nên mọi cập nhật giao diện ở đây đều đi qua {@link SwingUtilities#invokeLater}.
 */
public final class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final String CARD_CONNECT = "connect";
    private static final String CARD_LOGIN = "login";
    private static final String CARD_HOME = "home";

    private final ClientConfig config;
    private final ServerConnection connection = new ServerConnection();
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JLabel statusLabel = new JLabel("Chưa kết nối");
    private final JLabel userLabel = new JLabel();

    private LoginPanel loginPanel;
    private JPanel homePanel;

    public MainFrame(ClientConfig config) {
        super("LiveBid - Desktop client");
        this.config = config;

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
        setMinimumSize(new Dimension(560, 400));
        setSize(680, 480);
        setLocationRelativeTo(null);

        connection.addStateListener(state -> SwingUtilities.invokeLater(() -> onStateChanged(state)));
        connection.addErrorListener(error -> SwingUtilities.invokeLater(
                () -> setStatus("Lỗi từ server: " + error.message())));

        cardPanel.add(new ConnectPanel(this, config), CARD_CONNECT);
        add(cardPanel, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        showConnect();
    }

    /** Cấu hình đã đọc từ {@code client.properties}. */
    public ClientConfig config() {
        return config;
    }

    /** Kết nối dùng chung cho mọi màn hình; đóng khi cửa sổ đóng. */
    public ServerConnection connection() {
        return connection;
    }

    /** Quay về màn hình kết nối (lúc mở ứng dụng hoặc sau khi mất kết nối). */
    public void showConnect() {
        cards.show(cardPanel, CARD_CONNECT);
    }

    /** Đã kết nối TCP: mở màn hình đăng nhập. Gọi trên EDT. */
    public void onConnected() {
        if (loginPanel == null) {
            loginPanel = new LoginPanel(this);
            cardPanel.add(loginPanel, CARD_LOGIN);
        }
        userLabel.setText("");
        cards.show(cardPanel, CARD_LOGIN);
        setStatus("Đã kết nối tới server. Hãy đăng nhập.");
    }

    /** Đăng nhập/đăng ký thành công: hiện màn hình tạm cho tới khi có M5. Gọi trên EDT. */
    public void onLoggedIn(UserView user) {
        if (homePanel != null) {
            cardPanel.remove(homePanel);
        }
        homePanel = buildHomePanel(user);
        cardPanel.add(homePanel, CARD_HOME);
        userLabel.setText("Người dùng: " + user.displayName());
        cards.show(cardPanel, CARD_HOME);
        setStatus("Đã đăng nhập: " + user.displayName());
    }

    /** Cập nhật thanh trạng thái; gọi được từ bất kỳ luồng nào. */
    public void setStatus(String text) {
        if (SwingUtilities.isEventDispatchThread()) {
            statusLabel.setText(text);
        } else {
            SwingUtilities.invokeLater(() -> statusLabel.setText(text));
        }
    }

    private void onStateChanged(ConnectionState state) {
        switch (state) {
            case CONNECTING -> setStatus("Đang kết nối...");
            case CONNECTED -> setStatus("Đã kết nối tới server");
            case DISCONNECTED -> {
                setStatus("Chưa kết nối tới server");
                userLabel.setText("");
                if (loginPanel != null) {
                    cardPanel.remove(loginPanel);
                    loginPanel = null;
                }
                if (homePanel != null) {
                    cardPanel.remove(homePanel);
                    homePanel = null;
                }
                showConnect();
            }
        }
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                new EmptyBorder(6, 10, 6, 10)));
        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(userLabel, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildHomePanel(UserView user) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.insets = new Insets(4, 4, 4, 4);

        JLabel title = new JLabel("Đã đăng nhập: " + user.displayName());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        gbc.gridy = 0;
        panel.add(title, gbc);

        gbc.gridy = 1;
        panel.add(new JLabel("Tài khoản: " + user.username() + " — vai trò: " + user.role()), gbc);

        gbc.gridy = 2;
        panel.add(new JLabel("Danh sách phiên đấu giá và phòng đấu giá sẽ có ở mốc M5."), gbc);
        return panel;
    }

    private void shutdown() {
        connection.close();
        dispose();
    }

    /**
     * Câu thông báo tiếng Việt cho một lỗi bất đồng bộ: lỗi nghiệp vụ của server, hết giờ, hay lỗi mạng.
     */
    static String describeError(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof RemoteErrorException remote) {
            return remote.error().message();
        }
        if (cause instanceof TimeoutException) {
            return "Server không trả lời sau 10 giây. Hãy thử lại.";
        }
        if (cause instanceof IOException) {
            return "Không gửi được yêu cầu: " + cause.getMessage();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    /** Bóc {@link CompletionException}/{@link ExecutionException} để lấy nguyên nhân thật của lỗi. */
    static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /** Nhãn tiếng Việt cho trạng thái phiên, dùng chung cho bảng và phòng đấu giá. */
    static String statusText(AuctionStatus status) {
        if (status == null) {
            return "Không rõ";
        }
        return switch (status) {
            case SCHEDULED -> "Sắp bắt đầu";
            case ACTIVE -> "Đang diễn ra";
            case FINISHED -> "Đã kết thúc";
        };
    }
}
