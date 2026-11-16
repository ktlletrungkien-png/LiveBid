package com.livebid.client.ui;

import com.livebid.client.net.ServerConnection;
import com.livebid.common.dto.LoginResult;
import com.livebid.common.payload.LoginPayload;
import com.livebid.common.payload.RegisterPayload;
import com.livebid.common.protocol.MessageType;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Màn hình đăng nhập / đăng ký sau khi đã kết nối TCP.
 *
 * <p>Qua TCP, kết nối tự giữ trạng thái đăng nhập nên không cần token. Đăng nhập thành công thì
 * {@link MainFrame#onLoggedIn} mở màn hình tạm "Đã đăng nhập: ..." cho tới khi M5 có danh sách phiên.
 *
 * <p>Yêu cầu mạng chạy trên luồng của {@link ServerConnection}; callback được chuyển về EDT bằng
 * {@link SwingUtilities#invokeLater}. Lỗi nghiệp vụ hiện nguyên câu tiếng Việt của server.
 */
final class LoginPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MainFrame mainFrame;
    private final ServerConnection connection;

    private final JTextField loginUsername = new JTextField(18);
    private final JPasswordField loginPassword = new JPasswordField(18);
    private final JButton loginButton = new JButton("Đăng nhập");

    private final JTextField registerUsername = new JTextField(18);
    private final JTextField registerDisplayName = new JTextField(18);
    private final JPasswordField registerPassword = new JPasswordField(18);
    private final JButton registerButton = new JButton("Đăng ký");

    private final JLabel messageLabel = new JLabel(" ");

    LoginPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.connection = mainFrame.connection();

        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(20, 24, 20, 24));

        JLabel title = new JLabel("Đăng nhập để tiếp tục");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Đăng nhập", buildLoginForm());
        tabs.addTab("Đăng ký", buildRegisterForm());
        add(tabs, BorderLayout.CENTER);

        messageLabel.setBorder(new EmptyBorder(0, 4, 0, 4));
        add(messageLabel, BorderLayout.SOUTH);

        loginButton.addActionListener(e -> submitLogin());
        registerButton.addActionListener(e -> submitRegister());
        loginUsername.addActionListener(e -> submitLogin());
        loginPassword.addActionListener(e -> submitLogin());
        registerDisplayName.addActionListener(e -> submitRegister());
        registerPassword.addActionListener(e -> submitRegister());
    }

    private JPanel buildLoginForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.LINE_START;

        gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(new JLabel("Tên đăng nhập:"), gbc);
        gbc.gridx = 1;
        form.add(loginUsername, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        form.add(new JLabel("Mật khẩu:"), gbc);
        gbc.gridx = 1;
        form.add(loginPassword, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        form.add(loginButton, gbc);
        return form;
    }

    private JPanel buildRegisterForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.LINE_START;

        gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(new JLabel("Tên đăng nhập:"), gbc);
        gbc.gridx = 1;
        form.add(registerUsername, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        form.add(new JLabel("Tên hiển thị:"), gbc);
        gbc.gridx = 1;
        form.add(registerDisplayName, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        form.add(new JLabel("Mật khẩu:"), gbc);
        gbc.gridx = 1;
        form.add(registerPassword, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        form.add(new JLabel("Tên đăng nhập 3–30 ký tự, mật khẩu từ 8 ký tự."), gbc);

        gbc.gridy = 4;
        form.add(registerButton, gbc);
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
                    mainFrame.onLoggedIn(result.user());
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
                    mainFrame.onLoggedIn(result.user());
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
