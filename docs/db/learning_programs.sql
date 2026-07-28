-- Table: learning_programs
-- Service: content-builder
-- Entities mapped: LearningProgram
-- Engine: PostgreSQL
-- Mô tả: Bảng quản lý danh mục và thông tin chi tiết các chương trình học trong hệ thống Content Builder.
-- Bảng này đóng vai trò thực thể cấp cao chứa thông tin tổng quan của chương trình đào tạo/chủ đề lớn.
-- Mã chương trình (code) là duy nhất dùng cho mục đích tích hợp hệ thống ngoài và import/export.
-- Hỗ trợ cơ chế Soft Delete thông qua deleted_at.

-- Section 1 — CREATE TABLE
CREATE TABLE learning_programs (
    id             UUID            NOT NULL DEFAULT gen_random_uuid(), -- Định danh duy nhất của chương trình học
    title          VARCHAR(255)    NOT NULL,                           -- Tên chương trình học
    code           VARCHAR(100)    NULL,                               -- Mã chương trình dùng để import/export hoặc tích hợp
    description    TEXT            NULL,                               -- Mô tả chi tiết chương trình học (NULL nếu không có mô tả)
    status         VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',           -- Trạng thái chương trình (DRAFT, PUBLISHED, ARCHIVED, HIDDEN)
    metadata       JSONB           NULL DEFAULT '{}',                  -- Dữ liệu mở rộng dạng JSON
    created_by     UUID            NOT NULL,                           -- ID người tạo bản ghi (định danh từ User/IAM Service)
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo bản ghi
    updated_by     UUID            NULL,                               -- ID người cập nhật cuối cùng (NULL khi vừa khởi tạo)
    updated_at     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm cập nhật bản ghi gần nhất
    deleted_at     TIMESTAMPTZ     NULL                                -- Thời điểm xóa mềm (NULL = chưa bị xóa)
);

-- Section 2 — ALTER TABLE (Constraints)
ALTER TABLE learning_programs
    ADD CONSTRAINT pk_learning_programs PRIMARY KEY (id),
    ADD CONSTRAINT ck_learning_programs_title CHECK (length(trim(title)) > 0),
    ADD CONSTRAINT ck_learning_programs_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'HIDDEN'));

-- Section 3 — COMMENT ON COLUMN
COMMENT ON COLUMN learning_programs.id IS 'Định danh duy nhất của chương trình học (UUIDv4/UUIDv7)';
COMMENT ON COLUMN learning_programs.title IS 'Tên hiển thị của chương trình học (không được để trống hoặc chứa toàn khoảng trắng)';
COMMENT ON COLUMN learning_programs.code IS 'Mã định danh nghiệp vụ duy nhất dùng cho import/export hoặc kết nối hệ thống ngoài';
COMMENT ON COLUMN learning_programs.description IS 'Mô tả tổng quan nội dung và mục tiêu chương trình học';
COMMENT ON COLUMN learning_programs.status IS 'Trạng thái vòng đời của chương trình (DRAFT: Nháp, PUBLISHED: Đã xuất bản, ARCHIVED: Lưu trữ, HIDDEN: Ẩn)';
COMMENT ON COLUMN learning_programs.metadata IS 'Chứa các thuộc tính tùy biến mở rộng chưa có trong schema cố định';
COMMENT ON COLUMN learning_programs.created_by IS 'Định danh tài khoản người tạo chương trình (tham chiếu User Service)';
COMMENT ON COLUMN learning_programs.created_at IS 'Thời điểm khởi tạo bản ghi trong hệ thống (UTC)';
COMMENT ON COLUMN learning_programs.updated_by IS 'Định danh tài khoản thực hiện chỉnh sửa gần nhất (tham chiếu User Service)';
COMMENT ON COLUMN learning_programs.updated_at IS 'Thời điểm bản ghi cập nhật gần nhất (UTC)';
COMMENT ON COLUMN learning_programs.deleted_at IS 'Thời điểm đánh dấu xóa mềm chương trình (NULL = bản ghi đang hoạt động)';

-- Section 4 — Indexes
-- Index hỗ trợ tìm kiếm nhanh chương trình học theo mã duy nhất
CREATE UNIQUE INDEX uq_learning_programs_code ON learning_programs (code) WHERE deleted_at IS NULL AND code IS NOT NULL;

-- Index lọc danh sách chương trình theo trạng thái hiển thị/xuất bản
CREATE INDEX ix_learning_programs_status ON learning_programs (status) WHERE deleted_at IS NULL;

-- Index tra cứu danh sách chương trình do một người dùng tạo
CREATE INDEX ix_learning_programs_created_by ON learning_programs (created_by) WHERE deleted_at IS NULL;

-- Trigger auto-update updated_at
CREATE OR REPLACE FUNCTION trigger_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_learning_programs_updated_at
    BEFORE UPDATE ON learning_programs
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
