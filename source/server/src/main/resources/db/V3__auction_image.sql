-- Phiên bản 3: ảnh đại diện cho phiên đấu giá.
-- image_id là tên file trong kho ảnh của server (http.imageDir), NULL = không có ảnh.

ALTER TABLE auctions ADD COLUMN image_id TEXT NULL;
