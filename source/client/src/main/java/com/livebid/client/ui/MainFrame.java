package com.livebid.client.ui;

import com.livebid.client.ClientConfig;
import com.livebid.client.net.ConnectionState;
import com.livebid.client.net.MulticastListener;
import com.livebid.client.net.ReconnectPolicy;
import com.livebid.client.net.RemoteErrorException;
import com.livebid.client.net.ServerConnection;
import com.livebid.common.dto.AuctionDraft;
import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.LoginResult;
import com.livebid.common.dto.UserView;
import com.livebid.common.payload.LoginPayload;
import com.livebid.common.payload.UpdateAuctionPayload;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.Message;
import com.livebid.common.protocol.MessageType;
import com.livebid.common.protocol.ProtocolException;
import com.livebid.common.udp.AuctionAnnouncement;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
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
    private final JButton reconnectButton = new JButton("Kết nối lại");
    /** Ảnh đại diện + tên ở góc phải thanh trên cùng; bấm vào mở menu tài khoản (giống trang web). */
    private final JButton accountButton = new JButton();
    private final JPopupMenu accountMenu = new JPopupMenu();
    private final JLabel accountAvatar = new JLabel();
    private final JLabel accountName = new JLabel();
    private final JLabel accountUsername = new JLabel();
    private final Timer noticeTimer = new Timer(NOTICE_MILLIS, e -> noticeLabel.setText(""));
    private final Timer retryTimer = new Timer(0, e -> attemptReconnect());
    private final ReconnectPolicy reconnectPolicy = new ReconnectPolicy();

    private LoginPanel loginPanel;
    private AuctionHubPanel hubPanel;
    private RoomPanel roomPanel;
    private UserView currentUser;
    private MulticastListener multicastListener;

    /** Địa chỉ server lần kết nối gần nhất, dùng khi kết nối lại. */
    private String lastHost = "";
    private int lastPort;

    /** Thông tin đăng nhập chỉ giữ trong bộ nhớ để đăng nhập lại sau khi mất kết nối; không ghi ra file. */
    private String savedUsername;
    private char[] savedPassword;

    /** Phiên đang mở, để vào lại phòng sau khi kết nối lại; {@code null} khi đang ở danh sách. */
    private Long openRoomId;

    private boolean retryInProgress;
    private boolean closing;

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

        retryTimer.setRepeats(false);
        reconnectButton.setVisible(false);
        reconnectButton.addActionListener(e -> retryNow());

        connection.addStateListener(state -> SwingUtilities.invokeLater(() -> onStateChanged(state)));
        connection.addErrorListener(error -> SwingUtilities.invokeLater(
                () -> setStatus("Lỗi từ server: " + error.message())));
        connection.addEventListener(message -> SwingUtilities.invokeLater(() -> dispatchEvent(message)));

        cardPanel.add(new ConnectPanel(this, config), CARD_CONNECT);
        add(buildBrandBar(), BorderLayout.NORTH);
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
    public void onConnected(String host, int port) {
        lastHost = host == null ? "" : host.trim();
        lastPort = port;
        if (loginPanel == null) {
            loginPanel = new LoginPanel(this);
            cardPanel.add(loginPanel, CARD_LOGIN);
        }
        userLabel.setText("");
        cards.show(cardPanel, CARD_LOGIN);
        setStatus("Đã kết nối tới server. Hãy đăng nhập.");
    }

    /** Đăng nhập/đăng ký thành công: mở danh sách phiên và bật nhận thông báo multicast. Gọi trên EDT. */
    public void onLoggedIn(UserView user, String username, String password) {
        this.currentUser = user;
        rememberCredentials(username, password);
        userLabel.setText("Người dùng: " + user.displayName());
        updateAccountMenu();
        showList();
        startMulticast();
        setStatus("Đã đăng nhập: " + user.displayName());
    }

    /** Menu tài khoản → "Đăng xuất": gửi LOGOUT, giữ kết nối TCP và quay về màn hình đăng nhập. Gọi trên EDT. */
    private void logout() {
        if (currentUser == null) {
            return;
        }
        if (!connection.isConnected()) {
            afterLogout(null);
            return;
        }
        setStatus("Đang đăng xuất...");
        connection.request(MessageType.LOGOUT, null)
                .whenComplete((reply, error) -> SwingUtilities.invokeLater(() -> afterLogout(
                        error != null ? error
                                : reply.isSuccess() ? null
                                : new RemoteErrorException(reply.error()))));
    }

    /**
     * Dọn trạng thái đăng nhập trên máy dù server có trả lời hay không: quên mật khẩu đã lưu (để không tự đăng
     * nhập lại), đóng phòng, tắt multicast. Còn kết nối thì mở lại màn hình đăng nhập trống.
     */
    private void afterLogout(Throwable error) {
        retryTimer.stop();
        reconnectPolicy.reset();
        retryInProgress = false;
        reconnectButton.setVisible(false);
        clearCredentials();
        stopMulticast();
        closeRoom();
        openRoomId = null;
        if (hubPanel != null) {
            cardPanel.remove(hubPanel);
            hubPanel = null;
        }
        currentUser = null;
        userLabel.setText("");
        updateAccountMenu();
        if (!connection.isConnected()) {
            resetToConnectScreen();
            return;
        }
        if (loginPanel != null) {
            cardPanel.remove(loginPanel);
        }
        loginPanel = new LoginPanel(this);
        cardPanel.add(loginPanel, CARD_LOGIN);
        cards.show(cardPanel, CARD_LOGIN);
        setStatus(error == null ? "Đã đăng xuất." : "Đã đăng xuất trên máy này (server báo: " + describeError(error) + ")");
    }

    /** Menu tài khoản → "Cập nhật thông tin": mở hộp thoại, lưu xong thì làm mới các màn hình. Gọi trên EDT. */
    private void openProfile() {
        if (currentUser == null) {
            return;
        }
        if (!connection.isConnected()) {
            setStatus("Chưa kết nối tới server.");
            return;
        }
        Optional<ProfileDialog.Result> saved = ProfileDialog.show(this, connection, currentUser);
        if (saved.isEmpty() || currentUser == null) {
            return;
        }
        currentUser = saved.get().user();
        if (saved.get().newPassword() != null && savedUsername != null) {
            // Tự đăng nhập lại sau khi mất kết nối phải dùng mật khẩu mới.
            rememberCredentials(savedUsername, saved.get().newPassword());
        }
        userLabel.setText("Người dùng: " + currentUser.displayName());
        updateAccountMenu();
        setStatus("Đã cập nhật thông tin: " + currentUser.displayName());
        // Danh sách và phòng đang giữ thông tin cũ (tên ở chân trang, so sánh "bị vượt giá"), nên dựng lại.
        if (hubPanel != null) {
            cardPanel.remove(hubPanel);
            hubPanel = null;
        }
        if (openRoomId != null) {
            openRoom(openRoomId);
        } else {
            showList();
        }
    }

    private JPanel buildBrandBar() {
        JPanel bar = UiTheme.brandBar();
        UiTheme.accountButton(accountButton);
        accountButton.setVisible(false);
        accountButton.setToolTipText("Tài khoản");
        accountButton.addActionListener(e -> accountMenu.show(accountButton,
                accountButton.getWidth() - accountMenu.getPreferredSize().width, accountButton.getHeight() + 6));

        JPanel names = new JPanel(new BorderLayout(0, 2));
        names.setOpaque(false);
        accountName.setFont(UiTheme.sans(700, 14f));
        accountName.putClientProperty("html.disable", Boolean.TRUE);
        accountUsername.setForeground(UiTheme.MUTED);
        accountUsername.putClientProperty("html.disable", Boolean.TRUE);
        names.add(accountName, BorderLayout.NORTH);
        names.add(accountUsername, BorderLayout.SOUTH);
        JPanel head = new JPanel(new BorderLayout(12, 0));
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(6, 8, 10, 16));
        head.add(accountAvatar, BorderLayout.WEST);
        head.add(names, BorderLayout.CENTER);

        JMenuItem profileItem = new JMenuItem("Cập nhật thông tin...");
        profileItem.addActionListener(e -> openProfile());
        JMenuItem logoutItem = new JMenuItem("Đăng xuất");
        logoutItem.setForeground(UiTheme.ERROR);
        logoutItem.addActionListener(e -> logout());
        accountMenu.add(head);
        accountMenu.addSeparator();
        accountMenu.add(profileItem);
        accountMenu.add(logoutItem);

        bar.add(accountButton, BorderLayout.EAST);
        return bar;
    }

    /** Hiện/ẩn nút tài khoản theo người đang đăng nhập và cập nhật tên, ảnh đại diện. */
    private void updateAccountMenu() {
        UserView user = currentUser;
        accountMenu.setVisible(false);
        if (user == null) {
            accountButton.setVisible(false);
            return;
        }
        // Dấu ▾ chỉ thêm khi font có ký tự này (Be Vietnam Pro không có; Swing không tự lấy từ font khác).
        String caret = accountButton.getFont().canDisplay('▾') ? "  ▾" : "";
        accountButton.setText(user.displayName() + caret);
        accountButton.setIcon(UiTheme.avatarIcon(user.displayName(), 30));
        accountAvatar.setIcon(UiTheme.avatarIcon(user.displayName(), 40));
        accountName.setText(user.displayName());
        accountUsername.setText("@" + user.username());
        accountButton.setVisible(true);
    }

    /** Quay lại danh sách phiên (sau đăng nhập hoặc khi rời phòng). Gọi trên EDT. */
    public void showList() {
        if (currentUser == null) {
            return;
        }
        openRoomId = null;
        closeRoom();
        if (hubPanel == null) {
            hubPanel = new AuctionHubPanel(connection, currentUser, this::openRoom,
                    this::createAuction, this::editAuction);
            cardPanel.add(hubPanel, CARD_LIST);
        }
        cards.show(cardPanel, CARD_LIST);
        hubPanel.refresh();
    }

    /** Mở phòng đấu giá của một phiên: gửi {@code JOIN_AUCTION} rồi hiện {@link RoomPanel}. Gọi trên EDT. */
    public void openRoom(long auctionId) {
        if (currentUser == null) {
            return;
        }
        closeRoom();
        openRoomId = auctionId;
        RoomPanel panel = new RoomPanel(connection, auctionId, currentUser, this::showList);
        roomPanel = panel;
        cardPanel.add(panel, CARD_ROOM);
        cards.show(cardPanel, CARD_ROOM);
        panel.join();
    }

    /** Mở form tạo phiên rồi gửi {@code CREATE_AUCTION}. Gọi trên EDT. */
    private void createAuction() {
        if (!connection.isConnected()) {
            setStatus("Chưa kết nối tới server.");
            return;
        }
        Optional<AuctionDraft> draft = AuctionFormDialog.showCreate(this, System.currentTimeMillis());
        if (draft.isEmpty()) {
            return;
        }
        setStatus("Đang tạo phiên...");
        connection.call(MessageType.CREATE_AUCTION, draft.get(), AuctionView.class)
                .whenComplete((created, error) -> SwingUtilities.invokeLater(() -> {
                    if (error != null) {
                        setStatus("Không tạo được phiên: " + describeError(error));
                        return;
                    }
                    setStatus("Đã tạo phiên #" + created.auctionId() + " «" + created.title() + "».");
                    if (hubPanel != null) {
                        hubPanel.refresh();
                    }
                }));
    }

    /** Mở form sửa phiên đang chọn rồi gửi {@code UPDATE_AUCTION} với {@code expectedVersion}. Gọi trên EDT. */
    private void editAuction(AuctionView auction) {
        if (!connection.isConnected()) {
            setStatus("Chưa kết nối tới server.");
            return;
        }
        Optional<AuctionDraft> draft = AuctionFormDialog.showEdit(this, auction, System.currentTimeMillis());
        if (draft.isEmpty()) {
            return;
        }
        setStatus("Đang lưu phiên #" + auction.auctionId() + "...");
        connection.call(MessageType.UPDATE_AUCTION,
                        new UpdateAuctionPayload(auction.auctionId(), auction.version(), draft.get()),
                        AuctionView.class)
                .whenComplete((updated, error) -> SwingUtilities.invokeLater(() -> {
                    if (error != null) {
                        handleUpdateError(error);
                        return;
                    }
                    setStatus("Đã lưu phiên #" + updated.auctionId() + " «" + updated.title() + "».");
                    if (hubPanel != null) {
                        hubPanel.refresh();
                    }
                }));
    }

    private void handleUpdateError(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof RemoteErrorException remote
                && remote.error().code() == ErrorCode.VERSION_CONFLICT) {
            setStatus("Phiên đã bị thay đổi, hãy tải lại rồi sửa.");
            if (hubPanel != null) {
                hubPanel.refresh();
            }
            return;
        }
        setStatus("Không lưu được phiên: " + describeError(error));
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
                if (closing || retryInProgress) {
                    return;
                }
                if (savedUsername != null) {
                    beginReconnect();
                } else {
                    resetToConnectScreen();
                }
            }
        }
    }

    /** Mất kết nối khi đang đăng nhập: giữ phiên, đóng phòng và hẹn thử lại. */
    private void beginReconnect() {
        closeRoom();
        reconnectButton.setVisible(true);
        if (hubPanel != null) {
            cards.show(cardPanel, CARD_LIST);
        } else {
            showConnect();
        }
        scheduleRetry();
    }

    private void scheduleRetry() {
        if (closing || savedUsername == null) {
            return;
        }
        if (reconnectPolicy.exhausted()) {
            setStatus("Không kết nối lại được sau " + reconnectPolicy.attempts()
                    + " lần. Bấm “Kết nối lại” để thử tiếp.");
            reconnectButton.setVisible(true);
            return;
        }
        long delay = reconnectPolicy.nextDelayMillis();
        setStatus("Mất kết nối. Thử lại sau " + (delay / 1_000) + " giây (lần "
                + reconnectPolicy.attempts() + "/" + ReconnectPolicy.MAX_ATTEMPTS + ")...");
        retryTimer.setInitialDelay((int) delay);
        retryTimer.restart();
    }

    /** Bấm “Kết nối lại”: thử ngay, bỏ qua thời gian chờ còn lại. */
    private void retryNow() {
        retryTimer.stop();
        reconnectPolicy.reset();
        retryInProgress = false;
        attemptReconnect();
    }

    private void attemptReconnect() {
        if (closing || savedUsername == null || connection.isConnected()) {
            return;
        }
        if (lastHost.isBlank() || lastPort <= 0) {
            setStatus("Chưa biết địa chỉ server để kết nối lại.");
            return;
        }
        retryInProgress = true;
        reconnectButton.setEnabled(false);
        setStatus("Đang kết nối lại tới " + lastHost + ":" + lastPort + "...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                connection.connect(lastHost, lastPort);
                return null;
            }

            @Override
            protected void done() {
                reconnectButton.setEnabled(true);
                try {
                    get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    retryInProgress = false;
                    return;
                } catch (ExecutionException e) {
                    retryInProgress = false;
                    scheduleRetry();
                    return;
                }
                relogin();
            }
        }.execute();
    }

    /** Kết nối TCP đã mở lại: đăng nhập bằng thông tin còn trong bộ nhớ rồi vào lại phòng nếu đang mở. */
    private void relogin() {
        char[] password = savedPassword;
        String username = savedUsername;
        if (username == null || password == null) {
            retryInProgress = false;
            resetToConnectScreen();
            return;
        }
        setStatus("Đã kết nối lại, đang đăng nhập...");
        connection.call(MessageType.LOGIN, new LoginPayload(username, new String(password)), LoginResult.class)
                .whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
                    retryInProgress = false;
                    if (savedUsername == null) {
                        return; // người dùng đã đăng xuất trong lúc đang đăng nhập lại
                    }
                    if (error != null) {
                        setStatus("Không đăng nhập lại được: " + describeError(error));
                        scheduleRetry();
                        return;
                    }
                    reconnectPolicy.reset();
                    reconnectButton.setVisible(false);
                    currentUser = result.user();
                    userLabel.setText("Người dùng: " + result.user().displayName());
                    updateAccountMenu();
                    setStatus("Đã kết nối lại: " + result.user().displayName());
                    if (openRoomId != null) {
                        openRoom(openRoomId);
                    } else if (hubPanel != null) {
                        hubPanel.refresh();
                    } else {
                        showList();
                    }
                }));
    }

    /** Dọn toàn bộ màn hình phiên và quay về màn hình Kết nối (chưa đăng nhập hoặc đã đóng). */
    private void resetToConnectScreen() {
        retryTimer.stop();
        reconnectPolicy.reset();
        retryInProgress = false;
        reconnectButton.setVisible(false);
        setStatus("Chưa kết nối tới server");
        userLabel.setText("");
        stopMulticast();
        closeRoom();
        if (hubPanel != null) {
            cardPanel.remove(hubPanel);
            hubPanel = null;
        }
        if (loginPanel != null) {
            cardPanel.remove(loginPanel);
            loginPanel = null;
        }
        currentUser = null;
        updateAccountMenu();
        openRoomId = null;
        clearCredentials();
        showConnect();
    }

    private void rememberCredentials(String username, String password) {
        clearCredentials();
        savedUsername = username;
        savedPassword = password == null ? new char[0] : password.toCharArray();
    }

    private void clearCredentials() {
        savedUsername = null;
        if (savedPassword != null) {
            Arrays.fill(savedPassword, '\0');
            savedPassword = null;
        }
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER),
                new EmptyBorder(6, 10, 6, 10)));
        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(noticeLabel, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(reconnectButton);
        right.add(userLabel);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void shutdown() {
        closing = true;
        retryTimer.stop();
        clearCredentials();
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
