package com.livebid.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.livebid.client.net.RemoteErrorException;
import com.livebid.client.net.ServerConnection;
import com.livebid.client.room.MoneyFormat;
import com.livebid.client.room.RoomState;
import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.BidReceipt;
import com.livebid.common.dto.BidView;
import com.livebid.common.dto.Role;
import com.livebid.common.dto.RoomSnapshot;
import com.livebid.common.dto.UserView;
import com.livebid.common.payload.AuctionIdPayload;
import com.livebid.common.payload.BidHistoryPayload;
import com.livebid.common.payload.PlaceBidPayload;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.ErrorInfo;
import com.livebid.common.protocol.MessageType;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/**
 * Phòng đấu giá: thông tin phiên, đồng hồ đếm ngược theo giờ server, lịch sử đặt giá và ô đặt giá.
 *
 * <p>Vào phòng bằng {@code JOIN_AUCTION} (lấy {@link RoomSnapshot}); rời phòng gửi {@code LEAVE_AUCTION}
 * rồi gọi callback quay lại danh sách. Mọi cập nhật sau đó đến từ sự kiện {@code AUCTION_*} do
 * {@link MainFrame} chuyển vào {@link #onEvent} trên EDT.
 *
 * <p>Đồng hồ chạy bằng {@link Timer} 250 ms và tính theo {@link RoomState#remainingMillis()} (đã trừ lệch
 * giờ server), không dùng trực tiếp giờ máy. Biên nhận {@code PLACE_BID} không ghi đè trạng thái phòng:
 * chỉ sự kiện {@code AUCTION_UPDATED} có {@code version} mới hơn mới đổi giá hiển thị.
 *
 * <p>ADMIN chỉ xem phòng, không hiện ô đặt giá (server cũng chặn). Khi đặt giá lỗi vì quá thấp, ô nhập
 * được điền lại đúng {@code details.minimumAllowed} của server.
 */
final class RoomPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int HISTORY_LIMIT = 20;

    private static final Type LIST_OF_BIDS = new TypeToken<List<BidView>>() {
    }.getType();

    private final ServerConnection connection;
    private final long auctionId;
    private final UserView user;
    private final Runnable onLeave;
    private final RoomState state = new RoomState();

    private final JLabel titleLabel = new JLabel("Đang tải...");
    private final JLabel statusLabel = new JLabel();
    private final JTextArea descriptionArea = new JTextArea(2, 40);
    private final JLabel priceCaptionLabel = new JLabel("Giá khởi điểm:");
    private final JLabel priceLabel = new JLabel("-");
    private final JLabel incrementLabel = new JLabel(" ");
    private final JLabel leaderLabel = new JLabel(" ");
    private final JLabel winnerLabel = new JLabel(" ");
    private final JLabel timeLabel = new JLabel(" ");
    private final JLabel countdownLabel = new JLabel(" ");
    private final JLabel messageLabel = new JLabel(" ");
    private final JTextField amountField = new JTextField(12);
    private final JButton bidButton = new JButton("Đặt giá");
    private final JButton reloadHistoryButton = new JButton("Tải lại lịch sử");
    private final DefaultTableModel historyModel = new DefaultTableModel(
            new Object[] {"Thời điểm", "Người đặt", "Số tiền"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable historyTable = new JTable(historyModel);
    private final Timer countdownTimer = new Timer(250, e -> updateCountdown());

    private boolean bidInFlight;
    private long autoFilledAmount = -1;

    RoomPanel(ServerConnection connection, long auctionId, UserView user, Runnable onLeave) {
        this.connection = connection;
        this.auctionId = auctionId;
        this.user = user;
        this.onLeave = onLeave;

        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        historyTable.setRowHeight(22);
        historyTable.setFillsViewportHeight(true);
        historyTable.getTableHeader().setReorderingAllowed(false);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildBidArea(), BorderLayout.SOUTH);

        bidButton.addActionListener(e -> placeBid());
        amountField.addActionListener(e -> placeBid());
        reloadHistoryButton.addActionListener(e -> loadHistory());
    }

    /** Gửi {@code JOIN_AUCTION}; thành công thì đổ snapshot vào giao diện và chạy đồng hồ. */
    void join() {
        setMessage("Đang vào phòng...");
        connection.call(MessageType.JOIN_AUCTION, new AuctionIdPayload(auctionId), RoomSnapshot.class)
                .whenComplete((snapshot, error) -> SwingUtilities.invokeLater(() -> {
                    if (error != null) {
                        setMessage("Không vào được phòng: " + MainFrame.describeError(error));
                        return;
                    }
                    if (snapshot == null) {
                        setMessage("Server không trả về thông tin phòng.");
                        return;
                    }
                    state.apply(snapshot.auction());
                    renderState();
                    fillHistory(snapshot.recentBids());
                    countdownTimer.start();
                    setMessage(user.role() == Role.ADMIN
                            ? "Đã vào phòng. Quản trị viên chỉ xem, không đặt giá."
                            : "Đã vào phòng. Nhập giá rồi bấm Đặt giá.");
                }));
    }

    /**
     * Nhận sự kiện {@code AUCTION_*} của đúng phiên này. Gọi trên EDT.
     *
     * <p>Sự kiện có {@code version} cũ hơn hoặc bằng bị bỏ qua, nên gói về muộn không ghi đè trạng thái
     * mới hơn.
     */
    void onEvent(MessageType type, AuctionView view) {
        if (view == null || view.auctionId() != auctionId) {
            return;
        }
        String previousLeader = state.leaderDisplayName();
        boolean hadLeader = state.hasBids();
        long previousEndsAt = state.endsAt();
        if (!state.apply(view)) {
            return;
        }

        renderState();
        switch (type) {
            case AUCTION_UPDATED -> {
                loadHistory();
                if (hadLeader && previousLeader != null && previousLeader.equals(user.displayName())
                        && !previousLeader.equals(view.leaderDisplayName())) {
                    setMessage("Bạn đã bị vượt giá");
                }
            }
            case AUCTION_EXTENDED -> {
                long addedSeconds = Math.max(0, view.endsAt() - previousEndsAt) / 1_000;
                setMessage(addedSeconds > 0
                        ? "Phiên được gia hạn thêm " + addedSeconds + " giây"
                        : "Phiên được gia hạn");
            }
            case AUCTION_STARTED -> setMessage("Phiên đã bắt đầu, có thể đặt giá");
            case AUCTION_FINISHED -> setMessage(view.winnerDisplayName() == null
                    ? "Phiên đã kết thúc: chưa có người thắng"
                    : "Phiên đã kết thúc: người thắng là " + view.winnerDisplayName());
            default -> {
                // Sự kiện khác không thuộc phòng này.
            }
        }
    }

    /** Dừng đồng hồ; gọi khi rời phòng hoặc mất kết nối. */
    void close() {
        countdownTimer.stop();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        JButton backButton = new JButton("‹ Danh sách phiên");
        backButton.addActionListener(e -> leave());
        header.add(backButton, BorderLayout.WEST);
        header.add(titleLabel, BorderLayout.CENTER);
        header.add(statusLabel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.add(buildInfoPanel(), BorderLayout.NORTH);
        center.add(buildHistoryPanel(), BorderLayout.CENTER);
        return center;
    }

    private JPanel buildInfoPanel() {
        JPanel info = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        priceLabel.setFont(priceLabel.getFont().deriveFont(Font.BOLD, 16f));

        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.weightx = 0;
        info.add(priceCaptionLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        info.add(priceLabel, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        info.add(incrementLabel, gbc);

        gbc.gridy = 2;
        info.add(leaderLabel, gbc);

        gbc.gridy = 3;
        winnerLabel.setVisible(false);
        info.add(winnerLabel, gbc);

        gbc.gridy = 4;
        info.add(timeLabel, gbc);

        gbc.gridy = 5;
        countdownLabel.setFont(countdownLabel.getFont().deriveFont(Font.BOLD, 14f));
        info.add(countdownLabel, gbc);

        gbc.gridy = 6;
        info.add(new JLabel("Mô tả:"), gbc);

        gbc.gridy = 7;
        JScrollPane descriptionScroll = new JScrollPane(descriptionArea);
        descriptionScroll.setPreferredSize(new Dimension(100, 56));
        info.add(descriptionScroll, gbc);

        return info;
    }

    private JPanel buildHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Lịch sử đặt giá (mới nhất trước)"));
        panel.add(new JScrollPane(historyTable), BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        footer.add(reloadHistoryButton);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildBidArea() {
        JPanel area = new JPanel(new BorderLayout(0, 4));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.add(new JLabel("Giá của bạn:"));
        row.add(amountField);
        row.add(bidButton);
        if (user.role() == Role.ADMIN) {
            row.setVisible(false);
        }

        area.add(row, BorderLayout.NORTH);
        area.add(messageLabel, BorderLayout.SOUTH);
        return area;
    }

    private void renderState() {
        if (!state.isPresent()) {
            return;
        }
        titleLabel.setText(state.title());
        descriptionArea.setText(state.description().isBlank() ? "(Không có mô tả)" : state.description());
        descriptionArea.setCaretPosition(0);
        priceCaptionLabel.setText(state.hasBids() ? "Giá đã được đặt:" : "Giá khởi điểm:");
        priceLabel.setText(MoneyFormat.format(state.currentPrice()));
        incrementLabel.setText("Bước giá: " + MoneyFormat.format(state.minIncrement()));
        leaderLabel.setText(state.hasBids()
                ? "Người dẫn đầu: " + (state.leaderDisplayName() == null
                        ? "(không rõ)" : state.leaderDisplayName())
                : "Chưa có ai đặt giá");
        if (state.isFinished()) {
            winnerLabel.setVisible(true);
            winnerLabel.setText(state.winnerDisplayName() == null
                    ? "Kết quả: chưa có người thắng"
                    : "Người thắng: " + state.winnerDisplayName());
        } else {
            winnerLabel.setVisible(false);
        }
        timeLabel.setText("Bắt đầu: " + TimeFormat.dateTime(state.startsAt())
                + "   Kết thúc: " + TimeFormat.dateTime(state.endsAt()));
        statusLabel.setText(MainFrame.statusText(state.status()));
        refreshAmountField();
        updateCountdown();
    }

    /** Điền sẵn giá tối thiểu khi ô còn trống hoặc vẫn đang là giá tự điền lần trước. */
    private void refreshAmountField() {
        if (user.role() == Role.ADMIN) {
            return;
        }
        long minimum = state.minimumNextBid();
        String current = amountField.getText().trim();
        if (current.isEmpty() || current.equals(Long.toString(autoFilledAmount))) {
            amountField.setText(Long.toString(minimum));
            autoFilledAmount = minimum;
        }
    }

    private void updateCountdown() {
        if (!state.isPresent()) {
            return;
        }
        if (state.isFinished()) {
            countdownLabel.setText("Đã kết thúc");
        } else if (state.remainingMillis() <= 0) {
            countdownLabel.setText("Đã hết giờ, đang chờ server chốt kết quả");
        } else {
            countdownLabel.setText("Còn lại: " + TimeFormat.duration(state.remainingMillis()));
        }
        updateBidControls();
    }

    private void updateBidControls() {
        boolean canBid = user.role() != Role.ADMIN
                && state.isPresent()
                && state.status() == AuctionStatus.ACTIVE
                && !state.isFinished()
                && state.remainingMillis() > 0
                && !bidInFlight
                && connection.isConnected();
        bidButton.setEnabled(canBid);
        amountField.setEnabled(canBid);
    }

    private void loadHistory() {
        reloadHistoryButton.setEnabled(false);
        connection.<List<BidView>>call(MessageType.GET_BID_HISTORY,
                        new BidHistoryPayload(auctionId, null, HISTORY_LIMIT), LIST_OF_BIDS)
                .whenComplete((bids, error) -> SwingUtilities.invokeLater(() -> {
                    reloadHistoryButton.setEnabled(true);
                    if (error != null) {
                        setMessage("Không tải được lịch sử: " + MainFrame.describeError(error));
                        return;
                    }
                    fillHistory(bids);
                }));
    }

    private void fillHistory(List<BidView> bids) {
        historyModel.setRowCount(0);
        if (bids == null) {
            return;
        }
        for (BidView bid : bids) {
            historyModel.addRow(new Object[] {
                    TimeFormat.dateTime(bid.acceptedAt()),
                    bid.bidderDisplayName() == null ? "(không rõ)" : bid.bidderDisplayName(),
                    MoneyFormat.format(bid.amount())});
        }
    }

    private void placeBid() {
        if (!connection.isConnected()) {
            setMessage("Chưa kết nối tới server.");
            return;
        }
        long amount;
        try {
            amount = MoneyFormat.parse(amountField.getText());
        } catch (IllegalArgumentException e) {
            setMessage(e.getMessage());
            return;
        }
        long minimum = state.minimumNextBid();
        if (state.isPresent() && amount < minimum) {
            setMessage("Giá tối thiểu hiện tại là " + MoneyFormat.format(minimum));
            return;
        }

        bidInFlight = true;
        updateBidControls();
        setMessage("Đang gửi giá " + MoneyFormat.format(amount) + "...");
        connection.call(MessageType.PLACE_BID, new PlaceBidPayload(auctionId, amount), BidReceipt.class)
                .whenComplete((receipt, error) -> SwingUtilities.invokeLater(() -> {
                    bidInFlight = false;
                    if (error != null) {
                        handleBidError(error);
                    } else if (receipt == null) {
                        setMessage("Đặt giá thành công.");
                        amountField.setText("");
                        autoFilledAmount = -1;
                    } else {
                        setMessage("Đặt giá thành công: " + MoneyFormat.format(receipt.acceptedAmount()));
                        amountField.setText("");
                        autoFilledAmount = -1;
                    }
                    updateBidControls();
                }));
    }

    private void handleBidError(Throwable error) {
        Throwable cause = MainFrame.unwrap(error);
        if (cause instanceof RemoteErrorException remote) {
            ErrorInfo info = remote.error();
            if (info.code() == ErrorCode.BID_TOO_LOW && info.details() != null) {
                JsonElement minimum = info.details().get("minimumAllowed");
                if (minimum != null && !minimum.isJsonNull()) {
                    long allowed = minimum.getAsLong();
                    amountField.setText(Long.toString(allowed));
                    autoFilledAmount = allowed;
                    setMessage(info.message() + " Đã điền lại " + MoneyFormat.format(allowed) + ".");
                    return;
                }
            }
            setMessage(info.message());
            return;
        }
        if (cause instanceof TimeoutException || cause instanceof IOException) {
            setMessage("Chưa xác định kết quả: " + MainFrame.describeError(error)
                    + " Hãy bấm “Tải lại lịch sử” để kiểm tra.");
            return;
        }
        setMessage(MainFrame.describeError(error));
    }

    private void leave() {
        countdownTimer.stop();
        if (connection.isConnected()) {
            connection.request(MessageType.LEAVE_AUCTION, new AuctionIdPayload(auctionId))
                    .exceptionally(e -> null);
        }
        onLeave.run();
    }

    private void setMessage(String message) {
        messageLabel.setText(message == null || message.isBlank() ? " " : message);
    }
}
