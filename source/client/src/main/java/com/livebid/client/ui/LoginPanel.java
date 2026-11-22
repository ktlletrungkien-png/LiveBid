package com.livebid.client.ui;

import com.livebid.client.net.ServerConnection;
import com.livebid.common.dto.LoginResult;
import com.livebid.common.payload.LoginPayload;
import com.livebid.common.payload.RegisterPayload;
import com.livebid.common.protocol.MessageType;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.Arrays;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Màn hình đăng nhập / đăng ký sau khi đã kết nối TCP, bố cục giống trang {@code #/login} của web: bên trái
 * lời giới thiệu, bên phải thẻ có hai mục chuyển "Đăng nhập" / "Đăng ký".
 *
 * <p>Qua TCP, kết nối tự giữ trạng thái đăng nhập nên không cần token. Đăng nhập thành công thì
 * {@link MainFrame#onLoggedIn} mở danh sách phiên đấu giá. Đăng ký công khai luôn tạo USER.
 *
 * <p>Yêu cầu mạng chạy trên luồng của {@link ServerConnection}; callback được chuyển về EDT bằng
 * {@link SwingUtilities#invokeLater}. Lỗi nghiệp vụ hiện nguyên câu tiếng Việt của server.
 */
final class LoginPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String CARD_LOGIN = "login";
    private static final String CARD_REGISTER = "register";

    private final MainFrame mainFrame;
    private final ServerConnection connection;

    private final JTextField loginUsername = UiTheme.field(new JTextField(18));
    private final JPasswordField loginPassword = UiTheme.field(new JPasswordField(18));
    private final JButton loginButton = UiTheme.primary("Đăng nhập");

    private final JTextField registerUsername = UiTheme.field(new JTextField(18));
    private final JTextField registerDisplayName = UiTheme.field(new JTextField(18));
    private final JPasswordField registerPassword = UiTheme.field(new JPasswordField(18));
    private final JButton registerButton = UiTheme.primary("Tạo tài khoản");

    private final JLabel messageLabel = new JLabel(" ");
    private final JLabel cardTitle = new JLabel();
    private final JLabel cardSubtitle = UiTheme.muted(" ");
    private final CardLayout forms = new CardLayout();
    private final JPanel formPanel = new JPanel(forms);

    LoginPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.connection = mainFrame.connection();

        setOpaque(false);
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(24, 40, 24, 40));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1;
        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 0, 48);
        add(buildHero(), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(0, 0, 0, 0);
        add(buildCard(), gbc);

        loginButton.addActionListener(e -> submitLogin());
        registerButton.addActionListener(e -> submitRegister());
        loginUsername.addActionListener(e -> submitLogin());
        loginPassword.addActionListener(e -> submitLogin());
        registerDisplayName.addActionListener(e -> submitRegister());
        registerPassword.addActionListener(e -> submitRegister());

        showMode(true);
    }

    private JPanel buildHero() {
        JPanel hero = new JPanel();
        hero.setOpaque(false);
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        hero.add(Box.createVerticalGlue());

        JLabel headline = new JLabel("<html><body style='width:330px'>Đấu giá trực tiếp, giá nhảy từng giây.</body></html>");
        headline.setFont(UiTheme.sans(Font.BOLD, 32f));
        headline.setForeground(UiTheme.INK);
        headline.setAlignmentX(LEFT_ALIGNMENT);
        hero.add(headline);
        hero.add(Box.createVerticalStrut(14));

        JLabel lead = new JLabel("<html><body style='width:330px'>Tham gia phiên đấu giá theo thời gian thực, "
                + "đặt giá và theo dõi đối thủ ngay trên máy của bạn.</body></html>");
        lead.setFont(UiTheme.sans(Font.PLAIN, 15f));
        lead.setForeground(UiTheme.INK_SECONDARY);
        lead.setAlignmentX(LEFT_ALIGNMENT);
        hero.add(lead);
        hero.add(Box.createVerticalStrut(22));

        for (String point : new String[] {
                "Giá cập nhật tức thì cho mọi người trong phòng",
                "Đồng hồ đếm ngược chính xác, tự gia hạn cuối phiên",
                "Theo dõi phiên của bạn và lịch sử đặt giá"}) {
            JLabel label = new JLabel("•  " + point);
            label.setFont(UiTheme.sans(Font.PLAIN, 14f));
            label.setForeground(UiTheme.INK_SECONDARY);
            label.setAlignmentX(LEFT_ALIGNMENT);
            hero.add(label);
            hero.add(Box.createVerticalStrut(8));
        }
        hero.add(Box.createVerticalGlue());
        return hero;
    }

    private JPanel buildCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 12), 28);
        card.setPreferredSize(new Dimension(400, 440));

        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.setOpaque(false);
        cardTitle.setFont(UiTheme.sans(Font.BOLD, 22f));
        cardTitle.setForeground(UiTheme.INK);
        head.add(cardTitle, BorderLayout.NORTH);
        head.add(cardSubtitle, BorderLayout.CENTER);
        head.add(buildSwitch(), BorderLayout.SOUTH);
        card.add(head, BorderLayout.NORTH);

        formPanel.setOpaque(false);
        formPanel.add(buildLoginForm(), CARD_LOGIN);
        formPanel.add(buildRegisterForm(), CARD_REGISTER);
        card.add(formPanel, BorderLayout.CENTER);

        messageLabel.setFont(UiTheme.sans(Font.PLAIN, 13f));
        messageLabel.setForeground(UiTheme.RUBY);
        card.add(messageLabel, BorderLayout.SOUTH);
        return card;
    }

    /** Hai mục chuyển dạng pill, giống thanh chọn "Đăng nhập / Đăng ký" trên web. */
    private JPanel buildSwitch() {
        JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(14, 0, 0, 0));
        ButtonGroup group = new ButtonGroup();
        JToggleButton login = UiTheme.pillToggle("Đăng nhập");
        JToggleButton register = UiTheme.pillToggle("Đăng ký");
        login.setSelected(true);
        login.addActionListener(e -> showMode(true));
        register.addActionListener(e -> showMode(false));
        group.add(login);
        group.add(register);
        row.add(login);
        row.add(register);
        return row;
    }

    private void showMode(boolean login) {
        cardTitle.setText(login ? "Chào mừng quay lại" : "Tạo tài khoản mới");
        cardSubtitle.setText(login ? "Đăng nhập để bắt đầu đặt giá." : "Chỉ mất vài giây để tham gia.");
        forms.show(formPanel, login ? CARD_LOGIN : CARD_REGISTER);
        setMessage(" ");
    }

    private static JPanel formColumn() {
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(8, 0, 0, 0));
        return form;
    }

    private static void addField(JPanel form, String label, JComponent field) {
        JLabel caption = new JLabel(label);
        caption.setFont(UiTheme.sans(Font.BOLD, 12f));
        caption.setForeground(UiTheme.INK);
        caption.setAlignmentX(LEFT_ALIGNMENT);
        form.add(caption);
        form.add(Box.createVerticalStrut(4));
        field.setAlignmentX(LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
        form.add(field);
        form.add(Box.createVerticalStrut(12));
    }

    private JPanel buildLoginForm() {
        JPanel form = formColumn();
        addField(form, "Tên đăng nhập", loginUsername);
        addField(form, "Mật khẩu", loginPassword);
        form.add(Box.createVerticalStrut(4));
        loginButton.setAlignmentX(LEFT_ALIGNMENT);
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, loginButton.getPreferredSize().height));
        form.add(loginButton);
        return form;
    }

    private JPanel buildRegisterForm() {
        JPanel form = formColumn();
        addField(form, "Tên đăng nhập", registerUsername);
        addField(form, "Tên hiển thị", registerDisplayName);
        addField(form, "Mật khẩu", registerPassword);
        JLabel hint = UiTheme.muted("Tên đăng nhập 3–30 ký tự, mật khẩu từ 8 ký tự.");
        hint.setAlignmentX(LEFT_ALIGNMENT);
        form.add(hint);
        form.add(Box.createVerticalStrut(10));
        registerButton.setAlignmentX(LEFT_ALIGNMENT);
        registerButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, registerButton.getPreferredSize().height));
        form.add(registerButton);
        return form;
    }

    private void submitLogin() {
        String username = loginUsername.getText().trim();
        char[] raw = loginPassword.getPassword();
        String password = new String(raw);
        Arrays.fill(raw, '\0');
        if (username.isEmpty() || password.isEmpty()) {
            setMessage("Nhập tên đăng nhập và mật khẩu.");
            return;
        }

        setBusy(true, "Đang đăng nhập...");
        connection.call(MessageType.LOGIN, new LoginPayload(username, password), LoginResult.class)
                .whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false, " ");
                    if (error != null) {
                        setMessage(MainFrame.describeError(error));
                        return;
                    }
                    loginPassword.setText("");
                    mainFrame.onLoggedIn(result.user(), username, password);
                }));
    }

    private void submitRegister() {
        String username = registerUsername.getText().trim();
        String displayName = registerDisplayName.getText().trim();
        char[] raw = registerPassword.getPassword();
        String password = new String(raw);
        Arrays.fill(raw, '\0');
        if (username.isEmpty() || displayName.isEmpty() || password.isEmpty()) {
            setMessage("Nhập đủ tên đăng nhập, tên hiển thị và mật khẩu.");
            return;
        }

        setBusy(true, "Đang tạo tài khoản...");
        connection.call(MessageType.REGISTER,
                        new RegisterPayload(username, password, displayName), LoginResult.class)
                .whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false, " ");
                    if (error != null) {
                        setMessage(MainFrame.describeError(error));
                        return;
                    }
                    registerPassword.setText("");
                    mainFrame.onLoggedIn(result.user(), username, password);
                }));
    }

    private void setBusy(boolean busy, String message) {
        loginButton.setEnabled(!busy);
        registerButton.setEnabled(!busy);
        loginUsername.setEnabled(!busy);
        loginPassword.setEnabled(!busy);
        registerUsername.setEnabled(!busy);
        registerDisplayName.setEnabled(!busy);
        registerPassword.setEnabled(!busy);
        setMessage(message);
    }

    private void setMessage(String message) {
        messageLabel.setText(message == null || message.isBlank() ? " " : message);
    }
}
