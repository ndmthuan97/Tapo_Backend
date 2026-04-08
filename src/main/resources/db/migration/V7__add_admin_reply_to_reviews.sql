-- V7: Admin reply to review
-- Thêm 2 cột để admin có thể phản hồi đánh giá của khách hàng.
-- admin_reply    : nội dung phản hồi (TEXT, nullable)
-- replied_at     : thời điểm phản hồi (TIMESTAMPTZ, nullable)

ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS admin_reply TEXT,
    ADD COLUMN IF NOT EXISTS replied_at  TIMESTAMPTZ;
