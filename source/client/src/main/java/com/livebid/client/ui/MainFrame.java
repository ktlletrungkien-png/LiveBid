package com.livebid.client.ui;

import com.livebid.client.ClientConfig;
import com.livebid.client.net.ConnectionState;
import com.livebid.client.net.MulticastListener;
import com.livebid.client.net.RemoteErrorException;
import com.livebid.client.net.ServerConnection;
import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.UserView;
import com.livebid.common.protocol.Message;
import com.livebid.common.protocol.MessageType;
import com.livebid.common.protocol.ProtocolException;
import com.livebid.common.udp.AuctionAnnouncement;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

/**
 * Cửa sổ chính của desktop client: một {@link CardLayout} giữa các màn hình và thanh trạng thái luôn hiện.
 *
 * <p>Các màn hình:
 *
 * <ul>
 *   <li>{@link ConnectPanel}: tìm server bằng UDP hoặc nhập IP thủ công.</li>
 *   <li>{@link LoginPanel}: đăng nhập / đăng ký.</li>
 *   <li>{@link AuctionListPanel}: danh sách phiên, lọc và phân trang.</li>
 *   <li>{@link RoomPanel}: phòng đấu giá của một phiên.</li>
 * </ul>
 *
 * <p>Cửa sổ sở hữu một {@link ServerConnection} duy nhất và đóng nó khi thoát. Listener của kết nối chạy
 * trên luồng mạng nên mọi cập nhật giao diện ở đây đều đi qua {@link SwingUtilities#invokeLater}.
 *
 * <p>Chỉ có <b>một</b> listener sự kiện duy nhất: nó chuyển sự kiện {@code AUCTION_*} cho phòng đang mở
 * (nếu có) và lọc theo {@code auctionId} bên trong {@link RoomPanel}. Nhờ vậy không cần gỡ listener khi
 * đổi phòng. Thông báo multicast "phiên sắp bắt đầu" hiện không chặn trên thanh trạng thái và tự ẩn sau
 * khoảng 5 giây.
 */
public final class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final String CARD_CONNECT = "connect";
    private static final String CARD_LOGIN = "login";
    private static final String CARD_LIST = "list";
    private static final String CARD_ROOM = "room";

    /** Thời gian hiện thông báo multicast trước khi tự ẩn (mili giây). */
    private static final int NOTICE_MILLIS = 5_000;

    private final ClientConfig config;
    private final ServerConnection connection = new ServerConnection();
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JLabel statusLabel = new JLabel("Chưa kết nối");
    private final JLabel noticeLabel = new JLabel();
    private final JLabel userLabel = new JLabel();
    private final Timer noticeTimer = new Timer(NOTICE_MILLIS, e -> noticeLabel.setText(""));

    private LoginPanel loginPanel;
    private AuctionListPanel listPanel;
    private RoomPanel roomPanel;
    private UserView currentUser;
    private MulticastListener multicastListener;

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
        setMinimumSize(new Dimension(760, 520));
        setSize(880, 620);
        setLocationRelativeTo(null);

        noticeTimer.setRepeats(false);
        noticeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        connection.addStateListener(state -> SwingUtilities.invokeLater(() -> onStateChanged(state)));
        connection.addErrorListener(error -> SwingUtilities.invokeLater(
                () -> setStatus("Lỗi từ server: " + error.message())));
        connection.addEventListener(message -> SwingUtilities.invokeLater(() -> dispatchEvent(message)));

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

    /** Đăng nhập/đăng ký thành công: mở danh sách phiên và bật nhận thông báo multicast. Gọi trên EDT. */
    public void onLoggedIn(UserView user) {
        this.currentUser = user;
        userLabel.setText("Người dùng: " + user.displayName());
        showList();
        startMulticast();
        setStatus("Đã đăng nhập: " + user.displayName());
    }

    /** Quay lại danh sách phiên (sau đăng nhập hoặc khi rời phòng). Gọi trên EDT. */
    public void showList() {
        if (currentUser == null) {
            return;
        }
        closeRoom();
        if (listPanel == null) {
            listPanel = new AuctionListPanel(connection, currentUser, this::openRoom);
            cardPanel.add(listPanel, CARD_LIST);
        }
        cards.show(cardPanel, CARD_LIST);
        listPanel.refresh();
    }

    /** Mở phòng đấu giá của một phiên: gửi {@code JOIN_AUCTION} rồi hiện {@link RoomPanel}. Gọi trên EDT. */
    public void openRoom(long auctionId) {
        if (currentUser == null) {
            return;
        }
        closeRoom();
        RoomPanel panel = new RoomPanel(connection, auctionId, currentUser, this::showList);
        roomPanel = panel;
        cardPanel.add(panel, CARD_ROOM);
        cards.show(cardPanel, CARD_ROOM);
        panel.join();
    }

    /** Cập nhật thanh trạng thái; gọi được từ bất kỳ luồng nào. */
    public void setStatus(String text) {
        if (SwingUtilities.isEventDispatchThread()) {
            statusLabel.setText(text);
        } else {
            SwingUtilities.invokeLater(() -> statusLabel.setText(text));
        }
    }

    private void closeRoom() {
        RoomPanel panel = roomPanel;
        roomPanel = null;
        if (panel != null) {
            panel.close();
            cardPanel.remove(panel);
        }
    }

    private void dispatchEvent(Message message) {
        MessageType type = message.typeEnum();
        if (type == null || !type.isEvent()) {
            return;
        }
        AuctionView view;
        try {
            view = message.payloadAs(AuctionView.class);
        } catch (ProtocolException e) {
            setStatus("Bỏ qua sự kiện không đọc được: " + e.getMessage());
            return;
        }
        RoomPanel panel = roomPanel;
        if (panel != null) {
            panel.onEvent(type, view);
        }
    }

    private void startMulticast() {
        if (multicastListener != null) {
            return;
        }
        try {
            InetAddress group = InetAddress.getByName(config.multicastGroup());
            MulticastListener listener = new MulticastListener(group, config.multicastPort(),
                    announcement -> SwingUtilities.invokeLater(() -> showNotice(announcement)));
            listener.start();
            multicastListener = listener;
        } catch (UnknownHostException e) {
            setStatus("Không đọc được nhóm multicast " + config.multicastGroup() + ": " + e.getMessage());
        }
    }

    private void stopMulticast() {
        MulticastListener listener = multicastListener;
        multicastListener = null;
        if (listener != null) {
            listener.close();
        }
        noticeTimer.stop();
        noticeLabel.setText("");
    }

    private void showNotice(AuctionAnnouncement announcement) {
        noticeLabel.setText("Phiên «" + announcement.title() + "» sắp bắt đầu lúc "
                + TimeFormat.clock(announcement.startsAt()));
        noticeTimer.restart();
    }

    private void onStateChanged(ConnectionState state) {
        switch (state) {
            case CONNECTING -> setStatus("Đang kết nối...");
            case CONNECTED -> setStatus("Đã kết nối tới server");
            case DISCONNECTED -> {
                setStatus("Chưa kết nối tới server");
                userLabel.setText("");
                stopMulticast();
                closeRoom();
                if (listPanel != null) {
                    cardPanel.remove(listPanel);
                    listPanel = null;
                }
                if (loginPanel != null) {
                    cardPanel.remove(loginPanel);
                    loginPanel = null;
                }
                currentUser = null;
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
        bar.add(noticeLabel, BorderLayout.CENTER);
        bar.add(userLabel, BorderLayout.EAST);
        return bar;
    }

    private void shutdown() {
        stopMulticast();
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
