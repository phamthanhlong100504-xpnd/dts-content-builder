-- Table: explanations
-- Service: content-builder
-- Entities mapped: Explanation
-- Engine: PostgreSQL
-- Mô tả: Bảng lưu trữ nội dung giải thích/lời giải cho các đối tượng khác nhau (Polymorphic: target_id, target_type).
-- Mặc định đối tượng áp dụng lời giải thích là câu hỏi (target_type = 'QUESTION').
-- Chuẩn hóa tên bảng theo dạng số nhiều (explanations) tuân thủ quy tắc NAME-009.

-- Section 1 — CREATE TABLE
CREATE TABLE explanations (
    id             UUID            NOT NULL DEFAULT gen_random_uuid(), -- Định danh duy nhất lời giải thích
    target_id      UUID            NOT NULL,                           -- ID đối tượng được giải thích (ví dụ: question_id)
    target_type    VARCHAR(30)     NOT NULL DEFAULT 'QUESTION',        -- Loại đối tượng (mặc định: 'QUESTION')
    content        TEXT            NOT NULL,                           -- Nội dung giải thích/lời giải chi tiết
    status         VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',          -- Trạng thái (ACTIVE, INACTIVE)
    metadata       JSONB           NOT NULL DEFAULT '{}',              -- Dữ liệu mở rộng dạng JSON
    created_by     UUID            NOT NULL,                           -- ID người tạo (tham chiếu User Service)
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo
    updated_by     UUID            NULL,                               -- ID người cập nhật gần nhất (NULL khi vừa khởi tạo)
    updated_at     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm cập nhật
    deleted_at     TIMESTAMPTZ     NULL                                -- Thời điểm xóa mềm (NULL = bản ghi đang hoạt động)
);

-- Section 2 — ALTER TABLE (Constraints)
ALTER TABLE explanations
    ADD CONSTRAINT pk_explanations PRIMARY KEY (id),
    ADD CONSTRAINT ck_explanations_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

-- Section 3 — COMMENT ON COLUMN
COMMENT ON COLUMN explanations.id IS 'Định danh duy nhất của lời giải thích (UUIDv4/UUIDv7)';
COMMENT ON COLUMN explanations.target_id IS 'ID định danh của đối tượng nhận lời giải thích (Polymorphic reference)';
COMMENT ON COLUMN explanations.target_type IS 'Loại đối tượng được giải thích (mặc định: QUESTION)';
COMMENT ON COLUMN explanations.content IS 'Nội dung chi tiết của lời giải thích (văn bản thuần hoặc định dạng rich text)';
COMMENT ON COLUMN explanations.status IS 'Trạng thái hoạt động của lời giải thích (ACTIVE: Hoạt động, INACTIVE: Ngừng dùng)';
COMMENT ON COLUMN explanations.metadata IS 'Chứa dữ liệu mở rộng cho lời giải (media bổ trợ, ghi chú giáo viên,...)';
COMMENT ON COLUMN explanations.created_by IS 'Định danh người tạo bản ghi (User Service)';
COMMENT ON COLUMN explanations.created_at IS 'Thời điểm tạo bản ghi (UTC)';
COMMENT ON COLUMN explanations.updated_by IS 'Định danh người cập nhật gần nhất (User Service)';
COMMENT ON COLUMN explanations.updated_at IS 'Thời điểm cập nhật bản ghi gần nhất (UTC)';
COMMENT ON COLUMN explanations.deleted_at IS 'Thời điểm thực hiện xóa mềm (NULL = chưa xóa)';

-- Section 4 — Indexes
-- Index truy vấn lời giải thích theo đối tượng target_id và target_type
CREATE INDEX ix_explanations_target ON explanations (target_id, target_type) WHERE deleted_at IS NULL;

-- Index tra cứu theo người tạo
CREATE INDEX ix_explanations_created_by ON explanations (created_by) WHERE deleted_at IS NULL;

-- Trigger auto-update updated_at
CREATE TRIGGER trg_explanations_updated_at
    BEFORE UPDATE ON explanations
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
