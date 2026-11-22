package com.livebid.client.ui;

import com.livebid.common.dto.UserView;
import com.livebid.common.payload.UpdateProfilePayload;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Hộp thoại "Thông tin cá nhân": sửa tên hiển thị và đổi mật khẩu (tùy chọn), giống trang
 * {@code #/profile} của web.
 *
 * <p>Kiểm tra sơ bộ ngay trên client (server vẫn kiểm tra lại). Lỗi từ server hiện ngay trong hộp thoại
 * để người dùng sửa tiếp, không đóng hộp thoại.
 */
final class ProfileDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final int MAX_DISPLAY_NAME = 50;
    private static final int MIN_PASSWORD = 8;

    private final Function<UpdateProfilePayload, CompletableFuture<UserView>> submitter;
    private final BiConsumer<UserView, String> onSaved;

    private final JTextField nameField;
    private final JPasswordField currentField = UiTheme.field(new JPasswordField(20));
    private final JPasswordField newField = UiTheme.field(new JPasswordField(20));
    private final JPasswordField againField = UiTheme.field(new JPasswordField(20));
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton saveButton = UiTheme.primary("Lưu thay đổi");
    private final JButton cancelButton = UiTheme.secondary("Hủy");

    private ProfileDialog(Window owner, UserView user,
                          Function<UpdateProfilePayload, CompletableFuture<UserView>> submitter,
                          BiConsumer<UserView, String> onSaved) {
        super(owner, "Thông tin cá nhân", Dialog.ModalityType.APPLICATION_MODAL);
        this.submitter = submitter;
        this.onSaved = onSaved;
        this.nameField = UiTheme.field(new JTextField(user.displayName(), 20));
        getContentPane().setBackground(UiTheme.CANVAS);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 20, 8, 20));
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addRow(form, gbc, row++, "Tên đăng nhập:", new JLabel(user.username()));
        addRow(form, gbc, row++, "Tên hiển thị:", nameField);

        JLabel passwordTitle = new JLabel("Đổi mật khẩu (để trống nếu không đổi)");
        passwordTitle.setFont(UiTheme.sans(java.awt.Font.BOLD, 14f));
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(14, 4, 4, 4);
        form.add(passwordTitle, gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(4, 4, 4, 4);

        addRow(form, gbc, row++, "Mật khẩu hiện tại:", currentField);
        addRow(form, gbc, row++, "Mật khẩu mới:", newField);
        addRow(form, gbc, row++, "Nhập lại mật khẩu mới:", againField);

        errorLabel.setForeground(UiTheme.RUBY);
        errorLabel.setBorder(new EmptyBorder(0, 24, 0, 24));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.setOpaque(false);
        buttons.setBorder(new EmptyBorder(0, 16, 8, 16));
        buttons.add(cancelButton);
        buttons.add(saveButton);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(errorLabel, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        saveButton.addActionListener(e -> save());
        cancelButton.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(saveButton);

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /**
     * Mở hộp thoại (chặn cho tới khi đóng).
     *
     * @param submitter gửi {@code UPDATE_PROFILE} và trả về người dùng sau khi cập nhật
     * @param onSaved   gọi trên EDT khi lưu thành công: người dùng mới và mật khẩu mới (null nếu không đổi)
     */
    static void show(Window owner, UserView user,
                     Function<UpdateProfilePayload, CompletableFuture<UserView>> submitter,
                     BiConsumer<UserView, String> onSaved) {
        new ProfileDialog(owner, user, submitter, onSaved).setVisible(true);
    }

    private static void addRow(JPanel form, GridBagConstraints gbc, int row, String label,
                               java.awt.Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(field, gbc);
    }

    private void save() {
        String name = nameField.getText().trim();
        String current = new String(currentField.getPassword());
        String next = new String(newField.getPassword());
        String again = new String(againField.getPassword());
        boolean changePassword = !current.isEmpty() || !next.isEmpty() || !again.isEmpty();

        if (name.isEmpty() || name.length() > MAX_DISPLAY_NAME) {
            setError("Tên hiển thị phải từ 1 đến 50 ký tự.");
            return;
        }
        if (changePassword) {
            if (current.isEmpty()) {
                setError("Nhập mật khẩu hiện tại.");
                return;
            }
            if (next.length() < MIN_PASSWORD) {
                setError("Mật khẩu mới cần ít nhất 8 ký tự.");
                return;
            }
            if (!next.equals(again)) {
                setError("Mật khẩu nhập lại không khớp.");
                return;
            }
        }

        String newPassword = changePassword ? next : null;
        UpdateProfilePayload payload = new UpdateProfilePayload(name, changePassword ? current : null, newPassword);
        setBusy(true);
        submitter.apply(payload).whenComplete((updated, error) -> SwingUtilities.invokeLater(() -> {
            setBusy(false);
            if (error != null) {
                setError(MainFrame.describeError(error));
                return;
            }
            onSaved.accept(updated, newPassword);
            dispose();
        }));
    }

    private void setBusy(boolean busy) {
        saveButton.setEnabled(!busy);
        cancelButton.setEnabled(!busy);
        saveButton.setText(busy ? "Đang lưu..." : "Lưu thay đổi");
        if (busy) {
            setError(" ");
        }
    }

    private void setError(String message) {
        errorLabel.setText(message == null || message.isBlank() ? " " : message);
        pack();
    }
}
