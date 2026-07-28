-- Table: chapter_blocks
-- Service: content-builder
-- Entities mapped: ChapterBlock
-- Engine: PostgreSQL
-- Mô tả: Bảng quản lý cấu trúc phân cấp cây chủ đề (tree structure) trong một chương trình học.
-- Mỗi chapter_block có thể đại diện cho một nhóm chương/chủ đề cha (node trung gian) hoặc gắn trực tiếp với một chapter cụ thể (leaf node).
-- Hỗ trợ phân cấp đa cấp (parent_id) và sắp xếp thứ tự hiển thị (sort_order).
-- Lưu ý: Không sử dụng ràng buộc khóa ngoại mức DB đối với tham chiếu cross-service hoặc tự tham chiếu nhằm tối ưu hiệu năng và tính linh hoạt.

-- Section 1 — CREATE TABLE
CREATE TABLE chapter_blocks (
    id                     UUID            NOT NULL DEFAULT gen_random_uuid(), -- Định danh nhóm chủ đề / node cây
    learning_program_id    UUID            NOT NULL,                           -- ID chương trình học sở hữu node này
    parent_id              UUID            NULL,                               -- ID node cha trong cây phân cấp (NULL nếu là node gốc)
    chapter_id             UUID            NULL,                               -- ID chapter được gắn vào node này (NULL nếu node chỉ là container nhóm)
    title                  VARCHAR(255)    NOT NULL,                           -- Tiêu đề hiển thị của node
    sort_order             INT             NOT NULL DEFAULT 0,                 -- Thứ tự sắp xếp hiển thị trong cùng cấp
    status                 VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',           -- Trạng thái hiển thị (DRAFT, PUBLISHED, ARCHIVED, HIDDEN)
    metadata               JSONB           NOT NULL DEFAULT '{}',              -- Dữ liệu mở rộng cấu trúc cây dạng JSON
    created_by             UUID            NOT NULL,                           -- ID người tạo node (tham chiếu User Service)
    created_at             TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo bản ghi
    updated_by             UUID            NULL,                               -- ID người cập nhật gần nhất (NULL khi vừa khởi tạo)
    updated_at             TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm cập nhật gần nhất
    deleted_at             TIMESTAMPTZ     NULL                                -- Thời điểm xóa mềm (NULL = bản ghi đang hoạt động)
);

-- Section 2 — ALTER TABLE (Constraints)
ALTER TABLE chapter_blocks
    ADD CONSTRAINT pk_chapter_blocks PRIMARY KEY (id),
    ADD CONSTRAINT ck_chapter_blocks_sort_order CHECK (sort_order >= 0),
    ADD CONSTRAINT ck_chapter_blocks_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'HIDDEN'));

-- Section 3 — COMMENT ON COLUMN
COMMENT ON COLUMN chapter_blocks.id IS 'Định danh duy nhất của node khối chương (UUIDv4/UUIDv7)';
COMMENT ON COLUMN chapter_blocks.learning_program_id IS 'ID của chương trình học chứa khối này (tham chiếu nội bộ bảng learning_programs)';
COMMENT ON COLUMN chapter_blocks.parent_id IS 'ID của node khối cha (NULL nếu là node gốc ở cấp cao nhất)';
COMMENT ON COLUMN chapter_blocks.chapter_id IS 'ID bài học/chủ đề cụ thể được đính kèm vào khối này (tham chiếu bảng chapters)';
COMMENT ON COLUMN chapter_blocks.title IS 'Tiêu đề hiển thị của khối chương trên giao diện người dùng';
COMMENT ON COLUMN chapter_blocks.sort_order IS 'Thứ tự ưu tiên hiển thị (>= 0, số nhỏ hơn hiển thị trước)';
COMMENT ON COLUMN chapter_blocks.status IS 'Trạng thái hoạt động của khối chương (DRAFT: Nháp, PUBLISHED: Xuất bản, ARCHIVED: Lưu trữ, HIDDEN: Ẩn)';
COMMENT ON COLUMN chapter_blocks.metadata IS 'Chứa các thiết lập nâng cao cấu trúc nhóm bài học';
COMMENT ON COLUMN chapter_blocks.created_by IS 'Định danh người dùng tạo bản ghi (User Service)';
COMMENT ON COLUMN chapter_blocks.created_at IS 'Thời gian tạo bản ghi (UTC)';
COMMENT ON COLUMN chapter_blocks.updated_by IS 'Định danh người dùng cập nhật gần nhất (User Service)';
COMMENT ON COLUMN chapter_blocks.updated_at IS 'Thời gian cập nhật bản ghi (UTC)';
COMMENT ON COLUMN chapter_blocks.deleted_at IS 'Thời gian thực hiện xóa mềm (NULL = chưa xóa)';

-- Section 4 — Indexes
-- Index truy vấn cây các khối chương thuộc một chương trình học
CREATE INDEX ix_chapter_blocks_learning_program_id ON chapter_blocks (learning_program_id) WHERE deleted_at IS NULL;

-- Index truy vấn các khối con theo parent_id
CREATE INDEX ix_chapter_blocks_parent_id ON chapter_blocks (parent_id) WHERE deleted_at IS NULL;

-- Index tra cứu vị trí khối gắn với một chapter cụ thể
CREATE INDEX ix_chapter_blocks_chapter_id ON chapter_blocks (chapter_id) WHERE deleted_at IS NULL AND chapter_id IS NOT NULL;

-- Index tra cứu theo người tạo
CREATE INDEX ix_chapter_blocks_created_by ON chapter_blocks (created_by) WHERE deleted_at IS NULL;

-- Trigger auto-update updated_at
CREATE TRIGGER trg_chapter_blocks_updated_at
    BEFORE UPDATE ON chapter_blocks
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
