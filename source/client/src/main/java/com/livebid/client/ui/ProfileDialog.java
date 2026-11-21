package com.livebid.client.ui;

import com.livebid.client.net.ServerConnection;
import com.livebid.common.dto.Role;
import com.livebid.common.dto.UserView;
import com.livebid.common.payload.UpdateProfilePayload;
import com.livebid.common.protocol.MessageType;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Arrays;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Hộp thoại "Cập nhật thông tin": đổi tên hiển thị và (tùy chọn) đổi mật khẩu của tài khoản đang đăng nhập.
 *
 * <p>Khác {@link AuctionFormDialog}, hộp thoại này tự gửi {@code UPDATE_PROFILE} và chỉ đóng khi server báo
 * thành công, để lỗi như "Mật khẩu hiện tại không đúng" hiện ngay trong hộp thoại cho người dùng sửa.
 */
final class ProfileDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final int MAX_DISPLAY_NAME = 50;
    private static final int MIN_PASSWORD = 8;

    /** Kết quả khi lưu thành công; {@code newPassword} là {@code null} nếu không đổi mật khẩu. */
    record Result(UserView user, String newPassword) {
    }

    private final transient ServerConnection connection;
    private final JTextField displayNameField = new JTextField(24);
    private final JPasswordField currentPasswordField = new JPasswordField(24);
    private final JPasswordField newPasswordField = new JPasswordField(24);
    private final JPasswordField confirmPasswordField = new JPasswordField(24);
    private final JLabel messageLabel = new JLabel(" ");
    private final JButton saveButton = new JButton("Lưu thay đổi");
    private final JButton cancelButton = new JButton("Huỷ");

    private Result result;

    private ProfileDialog(Window owner, ServerConnection connection, UserView user) {
        super(owner, "Cập nhật thông tin", Dialog.ModalityType.APPLICATION_MODAL);
        this.connection = connection;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        displayNameField.setText(user.displayName());

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(new EmptyBorder(18, 20, 16, 20));
        content.add(buildHeader(user), BorderLayout.NORTH);
        content.add(buildForm(), BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 6));
        footer.add(messageLabel, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        UiTheme.primary(saveButton);
        buttons.add(cancelButton);
        buttons.add(saveButton);
        footer.add(buttons, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);

        cancelButton.addActionListener(e -> dispose());
        saveButton.addActionListener(e -> save());
        getRootPane().setDefaultButton(saveButton);

        setContentPane(content);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Mở hộp thoại; trả kết quả nếu lưu thành công, rỗng nếu người dùng huỷ. Gọi trên EDT. */
    static Optional<Result> show(Window owner, ServerConnection connection, UserView user) {
        ProfileDialog dialog = new ProfileDialog(owner, connection, user);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private JPanel buildHeader(UserView user) {
        JLabel name = new JLabel(user.displayName());
        UiTheme.title(name, 20f);
        name.putClientProperty("html.disable", Boolean.TRUE);
        JLabel account = new JLabel("@" + user.username() + " · "
                + (user.role() == Role.ADMIN ? "Quản trị viên" : "Người dùng"));
        account.setForeground(UiTheme.MUTED);
        account.putClientProperty("html.disable", Boolean.TRUE);

        JPanel text = new JPanel(new BorderLayout(0, 2));
        text.add(name, BorderLayout.NORTH);
        text.add(account, BorderLayout.SOUTH);

        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.add(new JLabel(UiTheme.avatarIcon(user.displayName(), 52)), BorderLayout.WEST);
        header.add(text, BorderLayout.CENTER);
        header.setBorder(new EmptyBorder(0, 0, 12, 0));
        return header;
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        gbc.insets = new Insets(0, 0, 6, 0);
        form.add(caption("Thông tin hiển thị"), gbc);
        addField(form, gbc, "Tên hiển thị", displayNameField);

        gbc.insets = new Insets(14, 0, 6, 0);
        gbc.gridy++;
        form.add(caption("Đổi mật khẩu (không bắt buộc)"), gbc);
        addField(form, gbc, "Mật khẩu hiện tại", currentPasswordField);
        addField(form, gbc, "Mật khẩu mới (ít nhất 8 ký tự, để trống nếu không đổi)", newPasswordField);
        addField(form, gbc, "Nhập lại mật khẩu mới", confirmPasswordField);
        return form;
    }

    private static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        UiTheme.caption(label);
        return label;
    }

    private static void addField(JPanel form, GridBagConstraints gbc, String label, JTextField field) {
        gbc.gridy++;
        gbc.insets = new Insets(6, 0, 4, 0);
        JLabel caption = new JLabel(label);
        caption.setForeground(UiTheme.MUTED);
        form.add(caption, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 4, 0);
        form.add(field, gbc);
    }

    /** Kiểm tra trên máy trước (báo lỗi sớm), rồi gửi UPDATE_PROFILE; server vẫn kiểm tra lại. */
    private void save() {
        String name = displayNameField.getText().trim();
        char[] current = currentPasswordField.getPassword();
        char[] fresh = newPasswordField.getPassword();
        char[] confirm = confirmPasswordField.getPassword();
        try {
            boolean changePassword = fresh.length > 0 || confirm.length > 0;
            String problem = validate(name, changePassword, current, fresh, confirm);
            if (problem != null) {
                showMessage(problem, true);
                return;
            }
            String newPassword = changePassword ? new String(fresh) : null;
            UpdateProfilePayload payload = new UpdateProfilePayload(name,
                    changePassword ? new String(current) : null, newPassword);
            setBusy(true);
            showMessage("Đang lưu...", false);
            connection.call(MessageType.UPDATE_PROFILE, payload, UserView.class)
                    .whenComplete((user, error) -> SwingUtilities.invokeLater(() -> {
                        setBusy(false);
                        if (error != null) {
                            showMessage(MainFrame.describeError(error), true);
                            return;
                        }
                        result = new Result(user, newPassword);
                        dispose();
                    }));
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(fresh, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    private static String validate(String name, boolean changePassword, char[] current, char[] fresh,
                                   char[] confirm) {
        if (name.isEmpty()) {
            return "Tên hiển thị không được để trống.";
        }
        if (name.length() > MAX_DISPLAY_NAME) {
            return "Tên hiển thị tối đa " + MAX_DISPLAY_NAME + " ký tự.";
        }
        if (changePassword) {
            if (current.length == 0) {
                return "Hãy nhập mật khẩu hiện tại để đổi mật khẩu.";
            }
            if (fresh.length < MIN_PASSWORD) {
                return "Mật khẩu mới phải có ít nhất " + MIN_PASSWORD + " ký tự.";
            }
            if (!Arrays.equals(fresh, confirm)) {
                return "Hai lần nhập mật khẩu mới không khớp.";
            }
        }
        return null;
    }

    private void setBusy(boolean busy) {
        saveButton.setEnabled(!busy);
        cancelButton.setEnabled(!busy);
        displayNameField.setEnabled(!busy);
        currentPasswordField.setEnabled(!busy);
        newPasswordField.setEnabled(!busy);
        confirmPasswordField.setEnabled(!busy);
    }

    private void showMessage(String text, boolean error) {
        messageLabel.setText(text);
        messageLabel.setForeground(error ? UiTheme.ERROR : UiTheme.MUTED);
    }
}
