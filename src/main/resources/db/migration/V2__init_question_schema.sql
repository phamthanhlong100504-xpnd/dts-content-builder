-- V1__init_question_schema.sql
CREATE TABLE IF NOT EXISTS questions (
    id UUID PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    explanations JSONB,
    media_file_ids JSONB,
    attachments JSONB,
    reference_data JSONB,
    status VARCHAR(50) NOT NULL,
    metadata JSONB,
    created_by UUID,
    created_at TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS question_options (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL,
    content TEXT NOT NULL,
    sort_order INTEGER,
    is_correct BOOLEAN NOT NULL,
    status VARCHAR(50) NOT NULL,
    metadata JSONB,
    created_by UUID,
    created_at TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_question_option_question FOREIGN KEY (question_id) REFERENCES questions (id)
);
