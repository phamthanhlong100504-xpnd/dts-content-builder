-- Table: questions
-- Service: content-builder
-- Entities mapped: Question
-- Engine: PostgreSQL
-- Mô tả: Bảng lưu trữ nội dung câu hỏi và các tài liệu, media, tài liệu tham khảo đính kèm trong Content Builder.
-- Hỗ trợ đa dạng loại câu hỏi (SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE, FILL_BLANK, ORDERING, MATCHING).
-- Ràng buộc tham chiếu tới Media Service và file đính kèm lưu dưới dạng JSONB.

-- Section 1 — CREATE TABLE
CREATE TABLE questions (
    id                UUID            NOT NULL DEFAULT gen_random_uuid(), -- Định danh duy nhất câu hỏi
    type              VARCHAR(30)     NOT NULL DEFAULT 'SINGLE_CHOICE',   -- Loại câu hỏi (SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE, FILL_BLANK, ORDERING, MATCHING)
    content           TEXT            NOT NULL,                           -- Nội dung chính của câu hỏi
    explanations      JSONB           NULL,                               -- Danh sách cấu hình lời giải thích đính kèm (nếu lưu dạng inline)
    media_file_ids    JSONB           NULL,                               -- Danh sách ID media từ Media Service (JSON array UUID)
    attachments       JSONB           NULL,                               -- Danh sách file đính kèm khác dạng JSON
    references        JSONB           NULL,                               -- Danh sách tài liệu tham khảo dạng JSON
    status            VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',           -- Trạng thái (DRAFT, PUBLISHED, ARCHIVED, HIDDEN)
    metadata          JSONB           NOT NULL DEFAULT '{}',              -- Dữ liệu mở rộng dạng JSON
    created_by        UUID            NOT NULL,                           -- ID người tạo (tham chiếu User Service)
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo bản ghi
    updated_by        UUID            NULL,                               -- ID người cập nhật gần nhất (NULL khi vừa khởi tạo)
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm cập nhật bản ghi
    deleted_at        TIMESTAMPTZ     NULL                                -- Thời điểm xóa mềm (NULL = bản ghi đang hoạt động)
);

-- Section 2 — ALTER TABLE (Constraints)
ALTER TABLE questions
    ADD CONSTRAINT pk_questions PRIMARY KEY (id),
    ADD CONSTRAINT ck_questions_type CHECK (type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'FILL_BLANK', 'ORDERING', 'MATCHING')),
    ADD CONSTRAINT ck_questions_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'HIDDEN'));

-- Section 3 — COMMENT ON COLUMN
COMMENT ON COLUMN questions.id IS 'Định danh duy nhất của câu hỏi (UUIDv4/UUIDv7)';
COMMENT ON COLUMN questions.type IS 'Phân loại dạng bài tập/câu hỏi: SINGLE_CHOICE (Chọn 1), MULTIPLE_CHOICE (Chọn nhiều), TRUE_FALSE (Đúng/Sai), FILL_BLANK (Điền từ), ORDERING (Sắp xếp), MATCHING (Nối câu)';
COMMENT ON COLUMN questions.content IS 'Nội dung câu hỏi (chấp nhận văn bản thuần hoặc HTML/Markdown)';
COMMENT ON COLUMN questions.explanations IS 'Dữ liệu cấu hình hoặc danh sách lời giải thích gắn kèm câu hỏi';
COMMENT ON COLUMN questions.media_file_ids IS 'Danh sách ID file phương tiện đính kèm (hình ảnh/âm thanh/video tham chiếu Media Service)';
COMMENT ON COLUMN questions.attachments IS 'Danh sách tài liệu hoặc tệp đính kèm học tập bổ trợ';
COMMENT ON COLUMN questions.references IS 'Thông tin trích dẫn hoặc tài liệu tham khảo nguồn gốc câu hỏi';
COMMENT ON COLUMN questions.status IS 'Trạng thái vòng đời câu hỏi (DRAFT: Nháp, PUBLISHED: Đã xuất bản, ARCHIVED: Lưu trữ, HIDDEN: Ẩn)';
COMMENT ON COLUMN questions.metadata IS 'Chứa dữ liệu tùy biến khác (độ khó, tag, điểm số mặc định,...)';
COMMENT ON COLUMN questions.created_by IS 'Định danh tài khoản người tạo (User Service)';
COMMENT ON COLUMN questions.created_at IS 'Thời gian khởi tạo câu hỏi (UTC)';
COMMENT ON COLUMN questions.updated_by IS 'Định danh tài khoản cập nhật gần nhất (User Service)';
COMMENT ON COLUMN questions.updated_at IS 'Thời gian cập nhật câu hỏi gần nhất (UTC)';
COMMENT ON COLUMN questions.deleted_at IS 'Thời gian thực hiện xóa mềm (NULL = chưa xóa)';

-- Section 4 — Indexes
-- Index lọc câu hỏi theo dạng loại bài tập
CREATE INDEX ix_questions_type ON questions (type) WHERE deleted_at IS NULL;

-- Index lọc câu hỏi theo trạng thái
CREATE INDEX ix_questions_status ON questions (status) WHERE deleted_at IS NULL;

-- Index tra cứu câu hỏi theo người tạo
CREATE INDEX ix_questions_created_by ON questions (created_by) WHERE deleted_at IS NULL;

-- Trigger auto-update updated_at
CREATE TRIGGER trg_questions_updated_at
    BEFORE UPDATE ON questions
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
