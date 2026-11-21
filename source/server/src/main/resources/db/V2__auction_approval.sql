-- Phiên bản 2: cho phép USER đăng phiên chờ duyệt và ADMIN duyệt hoặc từ chối.
-- SQLite không sửa được ràng buộc CHECK bằng ALTER TABLE nên phải dựng lại bảng auctions.

CREATE TABLE auctions_new (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    title             TEXT    NOT NULL,
    description       TEXT    NOT NULL DEFAULT '',
    starting_price    INTEGER NOT NULL CHECK (starting_price > 0),
    min_increment     INTEGER NOT NULL CHECK (min_increment > 0),
    current_price     INTEGER NOT NULL,
    current_bidder_id INTEGER NULL REFERENCES users (id),
    winner_id         INTEGER NULL REFERENCES users (id),
    starts_at         INTEGER NOT NULL,
    ends_at           INTEGER NOT NULL,
    extension_count   INTEGER NOT NULL DEFAULT 0,
    status            TEXT    NOT NULL CHECK (status IN
                        ('PENDING_APPROVAL', 'REJECTED', 'SCHEDULED', 'ACTIVE', 'FINISHED')),
    version           INTEGER NOT NULL,
    created_by        INTEGER NOT NULL REFERENCES users (id),
    created_at        INTEGER NOT NULL,
    reject_reason     TEXT    NULL,
    reviewed_by       INTEGER NULL REFERENCES users (id),
    reviewed_at       INTEGER NULL,
    CHECK (ends_at > starts_at)
);

INSERT INTO auctions_new (id, title, description, starting_price, min_increment, current_price,
    current_bidder_id, winner_id, starts_at, ends_at, extension_count, status, version,
    created_by, created_at)
SELECT id, title, description, starting_price, min_increment, current_price,
    current_bidder_id, winner_id, starts_at, ends_at, extension_count, status, version,
    created_by, created_at FROM auctions;

DROP TABLE auctions;
ALTER TABLE auctions_new RENAME TO auctions;

CREATE INDEX idx_auctions_status ON auctions (status, starts_at, id);
CREATE INDEX idx_auctions_owner  ON auctions (created_by, id);
