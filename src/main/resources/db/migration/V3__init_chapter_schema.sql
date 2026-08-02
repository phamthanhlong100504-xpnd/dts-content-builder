-- Table: chapters
-- Service: content-builder
-- Entities mapped: Chapter
-- Engine: PostgreSQL
-- Mô tả: Bảng lưu trữ danh mục chủ đề/bài học độc lập trong Content Builder Service.
-- Mỗi chapter chứa tiêu đề, trạng thái và các dữ liệu mở rộng để tái sử dụng hoặc liên kết vào cấu trúc khối (chapter_blocks).
-- Hỗ trợ cơ chế Soft Delete (deleted_at).

-- Section 1 — CREATE TABLE
CREATE TABLE IF NOT EXISTS chapters (
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
-- Table: question_blocks
-- Service: content-builder
-- Entities mapped: QuestionBlock
-- Engine: PostgreSQL
-- Mô tả: Bảng quản lý cấu trúc cây phân cấp nhóm câu hỏi (Question Tree / Question Section) trong một Chapter cụ thể.
-- Giúp tổ chức câu hỏi theo từng phần/nhóm nhỏ (ví dụ: Phần trắc nghiệm, Phần bài tập đọc hiểu,...).
-- Mỗi question_block có thể chứa các khối con (parent_id) hoặc gắn trực tiếp với câu hỏi cụ thể (question_id).

-- Section 1 — CREATE TABLE
CREATE TABLE IF NOT EXISTS question_blocks (
    id            UUID            NOT NULL DEFAULT gen_random_uuid(), -- Định danh duy nhất nhóm câu hỏi
    chapter_id    UUID            NOT NULL,                           -- ID chapter sở hữu khối câu hỏi này
    parent_id     UUID            NULL,                               -- ID khối câu hỏi cha (NULL nếu là node gốc)
    question_id   UUID            NULL,                               -- ID câu hỏi được đính kèm vào khối (NULL nếu node chỉ là container)
    title         VARCHAR(255)    NOT NULL,                           -- Tiêu đề nhóm câu hỏi
    sort_order    INT             NOT NULL DEFAULT 0,                 -- Thứ tự hiển thị (>= 0)
    status        VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',           -- Trạng thái (DRAFT, PUBLISHED, ARCHIVED, HIDDEN)
    metadata      JSONB           NOT NULL DEFAULT '{}',              -- Dữ liệu mở rộng dạng JSON
    created_by    UUID            NOT NULL,                           -- ID người tạo (tham chiếu User Service)
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo
    updated_by    UUID            NULL,                               -- ID người cập nhật gần nhất (NULL khi vừa khởi tạo)
    updated_at    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm cập nhật
    deleted_at    TIMESTAMPTZ     NULL                                -- Thời điểm xóa mềm (NULL = bản ghi đang hoạt động)
);

-- Section 2 — ALTER TABLE (Constraints)
ALTER TABLE question_blocks
    ADD CONSTRAINT pk_question_blocks PRIMARY KEY (id),
    ADD CONSTRAINT ck_question_blocks_sort_order CHECK (sort_order >= 0),
    ADD CONSTRAINT ck_question_blocks_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'HIDDEN'));

-- Section 3 — COMMENT ON COLUMN
COMMENT ON COLUMN question_blocks.id IS 'Định danh duy nhất của khối nhóm câu hỏi (UUIDv4/UUIDv7)';
COMMENT ON COLUMN question_blocks.chapter_id IS 'ID chủ đề/chương sở hữu khối câu hỏi này (tham chiếu bảng chapters)';
COMMENT ON COLUMN question_blocks.parent_id IS 'ID của khối câu hỏi cấp cha (NULL nếu nằm ở root của chapter)';
COMMENT ON COLUMN question_blocks.question_id IS 'ID câu hỏi cụ thể gắn với khối này (tham chiếu bảng questions)';
COMMENT ON COLUMN question_blocks.title IS 'Tiêu đề hoặc tên nhóm câu hỏi';
COMMENT ON COLUMN question_blocks.sort_order IS 'Thứ tự ưu tiên hiển thị (>= 0, số nhỏ hơn hiển thị trước)';
COMMENT ON COLUMN question_blocks.status IS 'Trạng thái hiển thị (DRAFT: Nháp, PUBLISHED: Xuất bản, ARCHIVED: Lưu trữ, HIDDEN: Ẩn)';
COMMENT ON COLUMN question_blocks.metadata IS 'Chứa dữ liệu mở rộng cho việc cấu hình hiển thị bài tập';
COMMENT ON COLUMN question_blocks.created_by IS 'Định danh người dùng tạo bản ghi (User Service)';
COMMENT ON COLUMN question_blocks.created_at IS 'Thời điểm tạo bản ghi (UTC)';
COMMENT ON COLUMN question_blocks.updated_by IS 'Định danh người dùng cập nhật gần nhất (User Service)';
COMMENT ON COLUMN question_blocks.updated_at IS 'Thời điểm cập nhật gần nhất (UTC)';
COMMENT ON COLUMN question_blocks.deleted_at IS 'Thời điểm thực hiện xóa mềm (NULL = chưa xóa)';

-- Section 4 — Indexes
-- Index tìm kiếm khối câu hỏi theo chapter_id
CREATE INDEX ix_question_blocks_chapter_id ON question_blocks (chapter_id) WHERE deleted_at IS NULL;

-- Index tìm các khối câu hỏi con theo parent_id
CREATE INDEX ix_question_blocks_parent_id ON question_blocks (parent_id) WHERE deleted_at IS NULL;

-- Index tra cứu vị trí khối gắn với câu hỏi cụ thể
CREATE INDEX ix_question_blocks_question_id ON question_blocks (question_id) WHERE deleted_at IS NULL AND question_id IS NOT NULL;

-- Index lọc theo người tạo
CREATE INDEX ix_question_blocks_created_by ON question_blocks (created_by) WHERE deleted_at IS NULL;

-- Trigger auto-update updated_at
CREATE TRIGGER trg_question_blocks_updated_at
    BEFORE UPDATE ON question_blocks
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
