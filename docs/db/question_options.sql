-- Table: question_options
-- Service: content-builder
-- Entities mapped: QuestionOption
-- Engine: PostgreSQL
-- Mô tả: Bảng lưu trữ các lựa chọn/đáp án trả lời của từng câu hỏi.
-- Liên kết trực tiếp với bảng questions qua question_id.
-- Hỗ trợ đánh dấu đáp án đúng/sai (is_correct) và thứ tự hiển thị (sort_order).

-- Section 1 — CREATE TABLE
CREATE TABLE question_options (
    id            UUID            NOT NULL DEFAULT gen_random_uuid(), -- Định danh duy nhất đáp án
    question_id   UUID            NOT NULL,                           -- ID câu hỏi sở hữu đáp án này
    content       TEXT            NOT NULL,                           -- Nội dung lựa chọn/đáp án
    sort_order    INT             NOT NULL DEFAULT 0,                 -- Thứ tự hiển thị của đáp án (>= 0)
    is_correct    BOOLEAN         NOT NULL DEFAULT FALSE,             -- Đánh dấu đây có phải là đáp án đúng hay không (TRUE/FALSE)
    status        VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',           -- Trạng thái (DRAFT, PUBLISHED, ARCHIVED, HIDDEN)
    metadata      JSONB           NOT NULL DEFAULT '{}',              -- Dữ liệu mở rộng dạng JSON
    created_by    UUID            NOT NULL,                           -- ID người tạo (tham chiếu User Service)
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo
    updated_by    UUID            NULL,                               -- ID người cập nhật gần nhất (NULL khi vừa khởi tạo)
    updated_at    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm cập nhật
    deleted_at    TIMESTAMPTZ     NULL                                -- Thời điểm xóa mềm (NULL = bản ghi đang hoạt động)
);

-- Section 2 — ALTER TABLE (Constraints)
ALTER TABLE question_options
    ADD CONSTRAINT pk_question_options PRIMARY KEY (id),
    ADD CONSTRAINT ck_question_options_sort_order CHECK (sort_order >= 0),
    ADD CONSTRAINT ck_question_options_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'HIDDEN'));

-- Section 3 — COMMENT ON COLUMN
COMMENT ON COLUMN question_options.id IS 'Định danh duy nhất của đáp án (UUIDv4/UUIDv7)';
COMMENT ON COLUMN question_options.question_id IS 'ID của câu hỏi chứa đáp án này (tham chiếu bảng questions)';
COMMENT ON COLUMN question_options.content IS 'Nội dung chi tiết của lựa chọn đáp án (văn bản thuần hoặc định dạng hiển thị)';
COMMENT ON COLUMN question_options.sort_order IS 'Thứ tự ưu tiên hiển thị lựa chọn trong danh sách (>= 0)';
COMMENT ON COLUMN question_options.is_correct IS 'Cờ đánh dấu kết quả đúng/sai của lựa chọn (TRUE = Đáp án đúng, FALSE = Đáp án sai)';
COMMENT ON COLUMN question_options.status IS 'Trạng thái hiển thị (DRAFT: Nháp, PUBLISHED: Đã xuất bản, ARCHIVED: Lưu trữ, HIDDEN: Ẩn)';
COMMENT ON COLUMN question_options.metadata IS 'Chứa các cấu hình mở rộng cho đáp án (phản hồi riêng, điểm số thành phần,...)';
COMMENT ON COLUMN question_options.created_by IS 'Định danh người tạo bản ghi (User Service)';
COMMENT ON COLUMN question_options.created_at IS 'Thời điểm khởi tạo bản ghi (UTC)';
COMMENT ON COLUMN question_options.updated_by IS 'Định danh người cập nhật gần nhất (User Service)';
COMMENT ON COLUMN question_options.updated_at IS 'Thời điểm cập nhật bản ghi gần nhất (UTC)';
COMMENT ON COLUMN question_options.deleted_at IS 'Thời điểm thực hiện xóa mềm (NULL = chưa xóa)';

-- Section 4 — Indexes
-- Index truy vấn danh sách đáp án theo câu hỏi
CREATE INDEX ix_question_options_question_id ON question_options (question_id) WHERE deleted_at IS NULL;

-- Index lọc nhanh các đáp án đúng của một câu hỏi
CREATE INDEX ix_question_options_correct ON question_options (question_id, is_correct) WHERE deleted_at IS NULL AND is_correct = TRUE;

-- Index tra cứu theo người tạo
CREATE INDEX ix_question_options_created_by ON question_options (created_by) WHERE deleted_at IS NULL;

-- Trigger auto-update updated_at
CREATE TRIGGER trg_question_options_updated_at
    BEFORE UPDATE ON question_options
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
