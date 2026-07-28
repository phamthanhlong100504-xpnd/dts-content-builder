-- Table: chapters
-- Service: content-builder
-- Entities mapped: Chapter
-- Engine: PostgreSQL
-- Mô tả: Bảng lưu trữ danh mục chủ đề/bài học độc lập trong Content Builder Service.
-- Mỗi chapter chứa tiêu đề, trạng thái và các dữ liệu mở rộng để tái sử dụng hoặc liên kết vào cấu trúc khối (chapter_blocks).
-- Hỗ trợ cơ chế Soft Delete (deleted_at).

-- Section 1 — CREATE TABLE
CREATE TABLE chapters (
    id            UUID            NOT NULL DEFAULT gen_random_uuid(), -- Định danh duy nhất của chủ đề
    title         VARCHAR(255)    NOT NULL,                           -- Tên chủ đề
    status        VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',           -- Trạng thái chủ đề (DRAFT, PUBLISHED, ARCHIVED, HIDDEN)
    metadata      JSONB           NOT NULL DEFAULT '{}',              -- Dữ liệu mở rộng của chủ đề dạng JSON
    created_by    UUID            NOT NULL,                           -- ID người tạo (tham chiếu User Service)
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo
    updated_by    UUID            NULL,                               -- ID người cập nhật gần nhất (NULL khi vừa khởi tạo)
    updated_at    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm cập nhật
    deleted_at    TIMESTAMPTZ     NULL                                -- Thời điểm xóa mềm (NULL = bản ghi đang hoạt động)
);

-- Section 2 — ALTER TABLE (Constraints)
ALTER TABLE chapters
    ADD CONSTRAINT pk_chapters PRIMARY KEY (id),
    ADD CONSTRAINT ck_chapters_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'HIDDEN'));

-- Section 3 — COMMENT ON COLUMN
COMMENT ON COLUMN chapters.id IS 'Định danh duy nhất của chủ đề/chương (UUIDv4/UUIDv7)';
COMMENT ON COLUMN chapters.title IS 'Tên chủ đề hoặc tiêu đề bài học';
COMMENT ON COLUMN chapters.status IS 'Trạng thái hoạt động của chủ đề (DRAFT: Nháp, PUBLISHED: Đã xuất bản, ARCHIVED: Lưu trữ, HIDDEN: Ẩn)';
COMMENT ON COLUMN chapters.metadata IS 'Cấu hình hoặc thuộc tính bổ sung của chủ đề';
COMMENT ON COLUMN chapters.created_by IS 'Định danh tài khoản người tạo (User Service)';
COMMENT ON COLUMN chapters.created_at IS 'Thời gian khởi tạo bản ghi trong DB (UTC)';
COMMENT ON COLUMN chapters.updated_by IS 'Định danh tài khoản cập nhật gần nhất (User Service)';
COMMENT ON COLUMN chapters.updated_at IS 'Thời gian cập nhật bản ghi gần nhất (UTC)';
COMMENT ON COLUMN chapters.deleted_at IS 'Thời gian đánh dấu xóa mềm (NULL = chưa xóa)';

-- Section 4 — Indexes
-- Index lọc chủ đề theo trạng thái
CREATE INDEX ix_chapters_status ON chapters (status) WHERE deleted_at IS NULL;

-- Index tra cứu chủ đề theo người tạo
CREATE INDEX ix_chapters_created_by ON chapters (created_by) WHERE deleted_at IS NULL;

-- Trigger auto-update updated_at
CREATE TRIGGER trg_chapters_updated_at
    BEFORE UPDATE ON chapters
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
