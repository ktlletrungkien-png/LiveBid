package com.livebid.client.ui;

import com.livebid.client.room.MoneyFormat;
import com.livebid.common.dto.AuctionView;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 * Bảng chỉ đọc dùng chung cho danh sách phiên và “Phiên của tôi”.
 *
 * <p>Giữ luôn danh sách gốc để lấy lại {@link AuctionView} của dòng được chọn. Mọi thay đổi dữ liệu phải
 * gọi {@link #setRows(List)} trên EDT.
 */
final class AuctionTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS = {"Tên", "Giá hiện tại", "Trạng thái", "Bắt đầu", "Kết thúc"};

    private List<AuctionView> rows = List.of();

    /** Thay toàn bộ dữ liệu; {@code null} được coi như danh sách rỗng. */
    void setRows(List<AuctionView> rows) {
        this.rows = rows == null ? List.of() : List.copyOf(rows);
        fireTableDataChanged();
    }

    /** Phiên ở chỉ số model, hoặc {@code null} nếu ngoài phạm vi. */
    AuctionView rowAt(int index) {
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        AuctionView auction = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> auction.title();
            case 1 -> MoneyFormat.format(auction.currentPrice());
            case 2 -> MainFrame.statusText(auction.status());
            case 3 -> TimeFormat.dateTime(auction.startsAt());
            case 4 -> TimeFormat.dateTime(auction.endsAt());
            default -> "";
        };
    }
}
