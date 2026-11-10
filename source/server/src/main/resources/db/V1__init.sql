-- Schema khởi tạo của LiveBid (phiên bản 1).
-- Tên cột và kiểu phải giữ nguyên: Kiên và Quân dựa vào ý nghĩa các cột này.

CREATE TABLE users (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    username            TEXT    NOT NULL UNIQUE,          -- luôn chữ thường
    display_name        TEXT    NOT NULL,
    password_hash       TEXT    NOT NULL,                 -- Base64
    password_salt       TEXT    NOT NULL,                 -- Base64
    password_iterations INTEGER NOT NULL,
    role                TEXT    NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    created_at          INTEGER NOT NULL                  -- epoch ms UTC
);

CREATE TABLE auctions (
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
    status            TEXT    NOT NULL CHECK (status IN ('SCHEDULED', 'ACTIVE', 'FINISHED')),
    version           INTEGER NOT NULL,
    created_by        INTEGER NOT NULL REFERENCES users (id),
    created_at        INTEGER NOT NULL,
    CHECK (ends_at > starts_at)
);

CREATE TABLE bids (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    auction_id  INTEGER NOT NULL REFERENCES auctions (id),
    user_id     INTEGER NOT NULL REFERENCES users (id),
    amount      INTEGER NOT NULL CHECK (amount > 0),
    accepted_at INTEGER NOT NULL,
    request_id  TEXT    NOT NULL
);
CREATE INDEX idx_bids_auction ON bids (auction_id, id);
CREATE INDEX idx_bids_user    ON bids (user_id, auction_id);

CREATE TABLE settlement_queue (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    auction_id      INTEGER NOT NULL UNIQUE REFERENCES auctions (id),
    status          TEXT    NOT NULL CHECK (status IN ('PENDING', 'DONE')),
    attempts        INTEGER NOT NULL DEFAULT 0,
    last_attempt_at INTEGER NULL
);
CREATE INDEX idx_settlement_status ON settlement_queue (status, id);
