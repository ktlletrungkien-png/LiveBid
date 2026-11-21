package com.livebid.client.ui;

import com.livebid.client.room.AuctionForm;
import com.livebid.common.dto.AuctionDraft;
import com.livebid.common.dto.AuctionView;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
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
 * Hộp thoại tạo hoặc sửa phiên đấu giá (chỉ ADMIN).
 *
 * <p>Ngày giờ nhập bằng {@link JSpinner} với {@link SpinnerDateModel} theo định dạng
 * {@code dd/MM/yyyy HH:mm}; giá trị trả về là epoch ms. Nút “Lưu” kiểm tra dữ liệu bằng
 * {@link AuctionForm#toDraft} và chỉ đóng khi hợp lệ, nên lỗi hiện ngay trong hộp thoại.
 *
 * <p>Hộp thoại chỉ thu thập dữ liệu: việc gọi {@code CREATE_AUCTION}/{@code UPDATE_AUCTION} do
 * {@link MainFrame} làm để giữ một chỗ xử lý lỗi nghiệp vụ.
 */
final class AuctionFormDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final String DATE_PATTERN = "dd/MM/yyyy HH:mm";

    private static final long DEFAULT_START_OFFSET_MILLIS = 60_000;

    private static final long DEFAULT_DURATION_MILLIS = 3_600_000;

    private final JTextField titleField = new JTextField(28);
    private final JTextArea descriptionArea = new JTextArea(4, 28);
    private final JTextField startingPriceField = new JTextField(14);
    private final JTextField incrementField = new JTextField(14);
    private final JSpinner startsAtSpinner;
    private final JSpinner endsAtSpinner;
    private final JLabel messageLabel = new JLabel(" ");

    private AuctionDraft result;

    private AuctionFormDialog(Window owner, String heading, AuctionView existing, long now) {
        super(owner, heading, Dialog.ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        if (existing == null) {
            startsAtSpinner = dateSpinner(now + DEFAULT_START_OFFSET_MILLIS);
            endsAtSpinner = dateSpinner(now + DEFAULT_START_OFFSET_MILLIS + DEFAULT_DURATION_MILLIS);
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

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        content.add(buildForm(), BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.add(messageLabel, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton cancelButton = new JButton("Huỷ");
        JButton saveButton = new JButton("Lưu");
        UiTheme.primary(saveButton);
        buttons.add(cancelButton);
        buttons.add(saveButton);
        footer.add(buttons, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);

        cancelButton.addActionListener(e -> dispose());
        saveButton.addActionListener(e -> save());
        getRootPane().setDefaultButton(saveButton);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Mở hộp thoại tạo phiên; trả về dữ liệu đã kiểm tra nếu người dùng bấm “Lưu”.
     *
     * @param owner cửa sổ cha (có thể {@code null})
     * @param now   giờ hiện tại (epoch ms) để đặt giờ mặc định và kiểm tra tương lai
     */
    static Optional<AuctionDraft> showCreate(Window owner, long now) {
        return new AuctionFormDialog(owner, "Tạo phiên đấu giá", null, now).open();
    }

    /** Mở hộp thoại sửa phiên đang ở trạng thái {@code SCHEDULED}, điền sẵn dữ liệu hiện tại. */
    static Optional<AuctionDraft> showEdit(Window owner, AuctionView auction, long now) {
        return new AuctionFormDialog(owner, "Sửa phiên #" + auction.auctionId(), auction, now).open();
    }

    private Optional<AuctionDraft> open() {
        setVisible(true);
        return Optional.ofNullable(result);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(new JLabel("Tên sản phẩm:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(titleField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;
        form.add(new JLabel("Mô tả:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        form.add(new JScrollPane(descriptionArea), gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        form.add(new JLabel("Giá khởi điểm:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(startingPriceField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        form.add(new JLabel("Bước giá:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(incrementField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        form.add(new JLabel("Bắt đầu:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(startsAtSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0;
        form.add(new JLabel("Kết thúc:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(endsAtSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        form.add(new JLabel("Tiền nhập như 280000, 280.000 hoặc 280 000."), gbc);

        return form;
    }

    private void save() {
        try {
            result = AuctionForm.toDraft(titleField.getText(), descriptionArea.getText(),
                    startingPriceField.getText(), incrementField.getText(),
                    valueOf(startsAtSpinner), valueOf(endsAtSpinner), System.currentTimeMillis());
        } catch (IllegalArgumentException e) {
            messageLabel.setText(e.getMessage());
            return;
        }
        dispose();
    }

    private static JSpinner dateSpinner(long value) {
        JSpinner spinner = new JSpinner(new SpinnerDateModel(new Date(value), null, null, Calendar.MINUTE));
        spinner.setEditor(new JSpinner.DateEditor(spinner, DATE_PATTERN));
        return spinner;
    }

    private static long valueOf(JSpinner spinner) {
        Object value = spinner.getValue();
        return value instanceof Date date ? date.getTime() : 0L;
    }
}
