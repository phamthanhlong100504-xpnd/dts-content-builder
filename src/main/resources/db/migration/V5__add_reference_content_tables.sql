
CREATE TABLE public.memory_tips (
    id uuid NOT NULL,
    content_version character varying(64) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    deleted_at timestamp(6) without time zone,
    explanation text NOT NULL,
    published_at timestamp(6) without time zone,
    quick_rule text NOT NULL,
    sort_order integer NOT NULL,
    source_id integer NOT NULL,
    status character varying(20) NOT NULL,
    title character varying(255) NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    updated_by uuid,
    CONSTRAINT memory_tips_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'ARCHIVED'::character varying, 'HIDDEN'::character varying])::text[])))
);


CREATE TABLE public.traffic_signs (
    id uuid NOT NULL,
    applicable_licenses jsonb NOT NULL,
    content_version character varying(64) NOT NULL,
    category character varying(32) NOT NULL,
    category_name character varying(100) NOT NULL,
    code character varying(32) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    deleted_at timestamp(6) without time zone,
    meaning text NOT NULL,
    media_file_id uuid,
    name character varying(255) NOT NULL,
    published_at timestamp(6) without time zone,
    quick_tip text NOT NULL,
    source_id integer NOT NULL,
    image_filename character varying(255) NOT NULL,
    status character varying(20) NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    updated_by uuid,
    CONSTRAINT traffic_signs_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'ARCHIVED'::character varying, 'HIDDEN'::character varying])::text[])))
);


ALTER TABLE ONLY public.memory_tips
    ADD CONSTRAINT memory_tips_pkey PRIMARY KEY (id);


ALTER TABLE ONLY public.traffic_signs
    ADD CONSTRAINT traffic_signs_pkey PRIMARY KEY (id);


CREATE UNIQUE INDEX uq_memory_tip_version_order ON public.memory_tips USING btree (content_version, sort_order) WHERE (deleted_at IS NULL);


CREATE UNIQUE INDEX uq_memory_tip_version_source ON public.memory_tips USING btree (content_version, source_id) WHERE (deleted_at IS NULL);


CREATE UNIQUE INDEX uq_traffic_sign_version_code ON public.traffic_signs USING btree (content_version, lower(btrim((code)::text))) WHERE (deleted_at IS NULL);


CREATE UNIQUE INDEX uq_traffic_sign_version_source ON public.traffic_signs USING btree (content_version, source_id) WHERE (deleted_at IS NULL);
