package com.livebid.client.ui;

import com.livebid.client.room.AuctionForm;
import com.livebid.common.dto.AuctionDraft;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.Role;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.border.EmptyBorder;

/**
 * Hộp thoại tạo hoặc sửa phiên, giống form {@code #/create} của web.
 *
 * <p>Ngày giờ nhập bằng {@link JSpinner} với {@link SpinnerDateModel} theo định dạng
 * {@code dd/MM/yyyy HH:mm}; giá trị trả về là epoch ms. Nút lưu kiểm tra dữ liệu bằng
 * {@link AuctionForm#toDraft} và chỉ đóng khi hợp lệ, nên lỗi hiện ngay trong hộp thoại.
 *
 * <p><b>USER</b> đăng phiên: giờ bắt đầu phải cách hiện tại ít nhất {@value #USER_LEAD_MINUTES} phút và phiên
 * được quản trị viên duyệt trước khi lên sàn. <b>ADMIN</b> không bị giới hạn này.
 *
 * <p>Hộp thoại chỉ thu thập dữ liệu: việc gọi {@code CREATE_AUCTION}/{@code UPDATE_AUCTION} do
 * {@link MainFrame} làm để giữ một chỗ xử lý lỗi nghiệp vụ.
 */
final class AuctionFormDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final String DATE_PATTERN = "dd/MM/yyyy HH:mm";

    /** USER phải đăng phiên bắt đầu sau hiện tại ít nhất ngần này phút. */
    static final int USER_LEAD_MINUTES = 30;

    private static final long USER_LEAD_MILLIS = USER_LEAD_MINUTES * 60_000L;
    private static final long ONE_HOUR = 3_600_000L;

    private final JTextField titleField = UiTheme.field(new JTextField(30));
    private final JTextArea descriptionArea = UiTheme.field(new JTextArea(4, 30));
    private final JTextField startingPriceField = UiTheme.field(new JTextField(14));
    private final JTextField incrementField = UiTheme.field(new JTextField(14));
    private final JSpinner startsAtSpinner;
    private final JSpinner endsAtSpinner;
    private final JLabel messageLabel = new JLabel(" ");
    private final Role role;

    private AuctionDraft result;

    private AuctionFormDialog(Window owner, String heading, AuctionView existing, long now, Role role,
            String submitLabel) {
        super(owner, heading, Dialog.ModalityType.APPLICATION_MODAL);
        this.role = role;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        if (existing == null) {
            long start = now + (role == Role.ADMIN ? 60_000L : USER_LEAD_MILLIS + 5 * 60_000L);
            startsAtSpinner = dateSpinner(start);
            endsAtSpinner = dateSpinner(start + ONE_HOUR);
        } else {
            titleField.setText(existing.title());
            descriptionArea.setText(existing.description());
            startingPriceField.setText(Long.toString(existing.startingPrice()));
            incrementField.setText(Long.toString(existing.minIncrement()));
            startsAtSpinner = dateSpinner(existing.startsAt());
            endsAtSpinner = dateSpinner(existing.endsAt());
        }
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(UiTheme.CANVAS);
        content.setBorder(new EmptyBorder(22, 26, 18, 26));

        JLabel title = new JLabel(heading);
        title.setFont(UiTheme.sans(Font.BOLD, 20f));
        title.setForeground(UiTheme.INK);
        content.add(title, BorderLayout.NORTH);
        content.add(buildForm(), BorderLayout.CENTER);

        messageLabel.setForeground(UiTheme.RUBY);
        messageLabel.setFont(UiTheme.sans(Font.PLAIN, 13f));

        JPanel footer = new JPanel(new BorderLayout(8, 6));
        footer.setOpaque(false);
        footer.add(messageLabel, BorderLayout.NORTH);
        if (role != Role.ADMIN) {
            footer.add(UiTheme.muted("Phiên sẽ được quản trị viên duyệt trước khi lên sàn."), BorderLayout.CENTER);
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancelButton = UiTheme.secondary("Hủy");
        JButton saveButton = UiTheme.primary(submitLabel);
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

    /**
     * Mở hộp thoại tạo phiên; trả về dữ liệu đã kiểm tra nếu người dùng bấm nút lưu.
     *
     * @param owner cửa sổ cha (có thể {@code null})
     * @param now   giờ hiện tại (epoch ms) để đặt giờ mặc định và kiểm tra tương lai
     * @param role  vai trò người đăng: USER bị giới hạn giờ bắt đầu và phải chờ duyệt
     */
    static Optional<AuctionDraft> showCreate(Window owner, long now, Role role) {
        String submit = role == Role.ADMIN ? "Tạo phiên" : "Gửi phiên";
        return new AuctionFormDialog(owner, "Đăng phiên đấu giá", null, now, role, submit).open();
    }

    /** Mở hộp thoại sửa phiên (ADMIN sửa phiên SCHEDULED, USER sửa phiên của mình đang chờ duyệt). */
    static Optional<AuctionDraft> showEdit(Window owner, AuctionView auction, long now, Role role) {
        return new AuctionFormDialog(owner, "Sửa phiên #" + auction.auctionId(), auction, now, role,
                "Lưu thay đổi").open();
    }

    private Optional<AuctionDraft> open() {
        setVisible(true);
        return Optional.ofNullable(result);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 0, 2, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridx = 0;
        gbc.gridwidth = 2;

        int row = 0;
        row = addField(form, gbc, row, "Tên sản phẩm", titleField);
        row = addField(form, gbc, row, "Mô tả", new JScrollPane(descriptionArea));

        gbc.gridwidth = 1;
        gbc.insets = new Insets(6, 0, 2, 8);
        row = addPair(form, gbc, row, "Giá khởi điểm (đ)", startingPriceField, "Bước giá (đ)", incrementField);
        row = addPair(form, gbc, row, "Giờ bắt đầu", startsAtSpinner, "Giờ kết thúc", endsAtSpinner);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(8, 0, 0, 0);
        form.add(UiTheme.muted("Tiền nhập như 280000, 280.000 hoặc 280 000."), gbc);
        return form;
    }

    private static int addField(JPanel form, GridBagConstraints gbc, int row, String label, Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        form.add(caption(label), gbc);
        gbc.gridy = row + 1;
        form.add(field, gbc);
        return row + 2;
    }

    private static int addPair(JPanel form, GridBagConstraints gbc, int row, String leftLabel, Component left,
            String rightLabel, Component right) {
        gbc.gridx = 0;
        gbc.gridy = row;
        form.add(caption(leftLabel), gbc);
        gbc.gridx = 1;
        form.add(caption(rightLabel), gbc);
        gbc.gridx = 0;
        gbc.gridy = row + 1;
        form.add(left, gbc);
        gbc.gridx = 1;
        form.add(right, gbc);
        return row + 2;
    }

    private static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTheme.sans(Font.BOLD, 12f));
        label.setForeground(UiTheme.INK);
        return label;
    }

    private void save() {
        long now = System.currentTimeMillis();
        long startsAt = valueOf(startsAtSpinner);
        try {
            if (role != Role.ADMIN && startsAt < now + USER_LEAD_MILLIS) {
                throw new IllegalArgumentException(
                        "Giờ bắt đầu phải cách hiện tại ít nhất " + USER_LEAD_MINUTES + " phút");
            }
            result = AuctionForm.toDraft(titleField.getText(), descriptionArea.getText(),
                    startingPriceField.getText(), incrementField.getText(),
                    startsAt, valueOf(endsAtSpinner), now);
        } catch (IllegalArgumentException e) {
            messageLabel.setText(e.getMessage());
            pack();
            return;
        }
        dispose();
    }

    private static JSpinner dateSpinner(long value) {
        JSpinner spinner = new JSpinner(new SpinnerDateModel(new Date(value), null, null, Calendar.MINUTE));
        spinner.setEditor(new JSpinner.DateEditor(spinner, DATE_PATTERN));
        spinner.setBorder(BorderFactory.createLineBorder(UiTheme.HAIRLINE_INPUT));
        spinner.setFont(UiTheme.sans(Font.PLAIN, 14f));
        spinner.setBackground(Color.WHITE);
        return spinner;
    }

    private static long valueOf(JSpinner spinner) {
        Object value = spinner.getValue();
        return value instanceof Date date ? date.getTime() : 0L;
    }
}
