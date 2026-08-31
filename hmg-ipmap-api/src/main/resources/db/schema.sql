-- =============================================================================
-- HMG IP Map - Complete Database Schema (PostgreSQL 17)
--
-- Run this script on a fresh database to initialize the full schema.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Extensions
-- ---------------------------------------------------------------------------

-- ltree: hierarchical path storage for location.path_ids
CREATE EXTENSION IF NOT EXISTS ltree;

-- btree_gist: allows scalar types (bigint, timestamp) in multi-column GIST indexes
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------------
-- Sequences
-- ---------------------------------------------------------------------------

-- ip_mapping uses an explicit sequence (not IDENTITY) for hi-lo allocation
CREATE SEQUENCE IF NOT EXISTS ip_mapping_seq
    INCREMENT BY 50
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START WITH 1
    CACHE 1000
    NO CYCLE;

-- ---------------------------------------------------------------------------
-- Tables
-- (ordered by foreign-key dependency)
-- ---------------------------------------------------------------------------

CREATE TABLE batch_run (
    id          BIGSERIAL NOT NULL,
    finished_at TIMESTAMP(6) NULL,
    job_id      VARCHAR(255) NOT NULL,
    job_name    VARCHAR(255) NOT NULL,
    started_at  TIMESTAMP(6) NULL,
    status      VARCHAR(255) NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  BIGINT,
    updated_at  TIMESTAMP,
    updated_by  BIGINT,
    CONSTRAINT batch_run_pkey PRIMARY KEY (id),
    CONSTRAINT batch_run_status_check
        CHECK (status IN ('RECEIVED','UPLOADING','READY','IN_PROGRESS','COMPLETED','FAILED','CANCELED'))
);

CREATE TABLE batch_file_zip (
    id            BIGSERIAL NOT NULL,
    name          VARCHAR(255) NULL,
    file_path     VARCHAR(255) NULL,
    status        VARCHAR(255) NULL,
    zip_type      VARCHAR(255) NULL,
    batch_id      BIGINT NULL,
    error_message VARCHAR(255),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    BIGINT,
    updated_at    TIMESTAMP,
    updated_by    BIGINT,
    CONSTRAINT batch_file_zip_pkey PRIMARY KEY (id),
    CONSTRAINT batch_file_zip_status_check
        CHECK (status IN ('INIT','EXTRACTED','FAILED')),
    CONSTRAINT fk_batch_file_zip_batch_id
        FOREIGN KEY (batch_id) REFERENCES batch_run (id)
);

-- Self-referential; parent_id FK is added after table creation
CREATE TABLE location (
    id             BIGSERIAL NOT NULL,
    attributes     JSONB NULL,
    geoname_id     BIGINT NULL,
    location_code  VARCHAR(255) NULL,
    location_level VARCHAR(255) NOT NULL,
    parent_id      BIGINT NULL,
    scope          VARCHAR(255) NULL,
    user_id        BIGINT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     BIGINT,
    updated_at     TIMESTAMP,
    updated_by     BIGINT,
    path_ids       ltree,
    CONSTRAINT pk_location PRIMARY KEY (id),
    CONSTRAINT fk_location_parent_id
        FOREIGN KEY (parent_id) REFERENCES location (id)
);

-- Composite PK (location_id, locale_code); no surrogate id column
CREATE TABLE location_name (
    locale_code VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    location_id BIGINT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  BIGINT,
    updated_at  TIMESTAMP,
    updated_by  BIGINT,
    CONSTRAINT location_name_pkey PRIMARY KEY (location_id, locale_code),
    CONSTRAINT fk_location_name_location_id
        FOREIGN KEY (location_id) REFERENCES location (id)
);

-- Self-referential
CREATE TABLE "user" (
    id                BIGSERIAL NOT NULL,
    api_key           VARCHAR(255) NOT NULL,
    name              VARCHAR(255) NOT NULL,
    source_ip         VARCHAR(15) NULL,
    user_type         VARCHAR(255) NULL,
    parent_id         BIGINT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        BIGINT,
    updated_at        TIMESTAMP,
    updated_by        BIGINT,
    response_template VARCHAR(255) NULL,
    CONSTRAINT user_pkey PRIMARY KEY (id),
    CONSTRAINT user_user_type_check
        CHECK (user_type IN ('SUB_CLIENT','CLIENT','ADMIN')),
    CONSTRAINT fk_user_parent_id
        FOREIGN KEY (parent_id) REFERENCES "user" (id)
);

CREATE TABLE batch_file (
    id            BIGSERIAL NOT NULL,
    error_message TEXT NULL,
    file_name     VARCHAR(255) NULL,
    file_type     VARCHAR(255) NULL,
    line_count    INTEGER NULL,
    path          VARCHAR(255) NULL,
    processed_at  TIMESTAMP(6) NULL,
    status        VARCHAR(255) NULL,
    file_zip_id   BIGINT NULL,
    batch_id      BIGINT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    BIGINT,
    updated_at    TIMESTAMP,
    updated_by    BIGINT,
    skip_count    INTEGER DEFAULT 0,
    CONSTRAINT batch_file_pkey PRIMARY KEY (id),
    CONSTRAINT batch_file_status_check
        CHECK (status IN ('INIT','IN_PROGRESS','COMPLETED','FAILED','UPLOADING','READY')),
    CONSTRAINT fk_batch_file_file_zip_id
        FOREIGN KEY (file_zip_id) REFERENCES batch_file_zip (id),
    CONSTRAINT fk_batch_file_batch_id
        FOREIGN KEY (batch_id) REFERENCES batch_run (id)
);

CREATE TABLE batch_file_detail (
    id            BIGSERIAL NOT NULL,
    error_message VARCHAR(255) NULL,
    line_data     VARCHAR(1024) NULL,
    line_no       INTEGER NULL,
    status        VARCHAR(255) NULL,
    file_id       BIGINT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    BIGINT,
    updated_at    TIMESTAMP,
    updated_by    BIGINT,
    line_hash     UUID,
    CONSTRAINT batch_file_detail_pkey PRIMARY KEY (id),
    CONSTRAINT batch_file_detail_status_check
        CHECK (status IN ('INIT','PROMOTE','IN_PROGRESS','ERROR','SUCCESS')),
    CONSTRAINT fk_batch_file_detail_file_id
        FOREIGN KEY (file_id) REFERENCES batch_file (id)
);

-- Uses explicit sequence (ip_mapping_seq) instead of IDENTITY
CREATE TABLE ip_mapping (
    id                             BIGINT NOT NULL DEFAULT nextval('ip_mapping_seq'),
    created_at                     TIMESTAMP NULL,
    ip_notation                    VARCHAR(255) NULL,
    notation_type                  VARCHAR(255) NULL,
    registered_country_geoname_id  BIGINT NULL,
    represented_country_geoname_id BIGINT NULL,
    scope                          VARCHAR(255) NULL,
    updated_at                     TIMESTAMP,
    valid_period                   TIMESTAMP NULL,
    location_id                    BIGINT NULL,
    user_id                        BIGINT NULL,
    created_by                     BIGINT,
    updated_by                     BIGINT,
    CONSTRAINT ip_mapping_pkey PRIMARY KEY (id),
    CONSTRAINT ip_mapping_notation_type_check
        CHECK (notation_type IN ('CIDR','RANGE','WILDCARD','ARRAY','SINGLE')),
    CONSTRAINT ip_mapping_scope_check
        CHECK (scope IN ('SUB_CLIENT','CLIENT','GLOBAL')),
    CONSTRAINT fk_ip_mapping_location_id
        FOREIGN KEY (location_id) REFERENCES location (id),
    CONSTRAINT fk_ip_mapping_user_id
        FOREIGN KEY (user_id) REFERENCES "user" (id)
);

ALTER SEQUENCE ip_mapping_seq OWNED BY ip_mapping.id;

CREATE TABLE ip_mapping_attribute (
    id            BIGSERIAL NOT NULL,
    attributes    JSONB NOT NULL,
    object_name   VARCHAR(255) NULL,
    ip_mapping_id BIGINT NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    BIGINT,
    updated_at    TIMESTAMP,
    updated_by    BIGINT,
    CONSTRAINT ip_mapping_attribute_pkey PRIMARY KEY (id),
    CONSTRAINT ip_mapping_attribute_object_name_check
        CHECK (object_name IN ('LOCATION','POSTAL','TRAITS','CONFIDENCE')),
    CONSTRAINT fk_ip_mapping_attribute_ip_mapping_id
        FOREIGN KEY (ip_mapping_id) REFERENCES ip_mapping (id)
);

-- ip_range (int8range) is the canonical lookup column; ip_lower/ip_upper retained for reference
CREATE TABLE ip_span (
    id            BIGSERIAL NOT NULL,
    ip_lower      BIGINT NULL,
    ip_upper      BIGINT NULL,
    ip_mapping_id BIGINT NULL,
    scope         VARCHAR(255) NULL,
    created_at    TIMESTAMP NULL,
    user_id       BIGINT NULL,
    created_by    BIGINT,
    updated_at    TIMESTAMP,
    updated_by    BIGINT,
    valid_period  TIMESTAMP NULL,
    ip_range      INT8RANGE,
    CONSTRAINT ip_span_pkey PRIMARY KEY (id),
    CONSTRAINT fk_ip_span_ip_mapping_id
        FOREIGN KEY (ip_mapping_id) REFERENCES ip_mapping (id)
);

CREATE TABLE cache_sync_failure (
    id           BIGSERIAL NOT NULL,
    action       VARCHAR(25) NOT NULL,
    data         TEXT NULL,
    region       CHAR(2) NULL,
    status       VARCHAR(20) NOT NULL,
    error        TEXT NULL,
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cache_sync_failure_pkey PRIMARY KEY (id)
);

CREATE TABLE cache_sync_job (
    id            BIGSERIAL NOT NULL,
    action        VARCHAR(25) NOT NULL,
    table_name    VARCHAR(50) NOT NULL,
    data          TEXT NOT NULL,
    status        VARCHAR(20) NOT NULL,
    error_message TEXT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cache_sync_job_pkey PRIMARY KEY (id)
);


-- ---------------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------------

-- batch_run
CREATE INDEX IF NOT EXISTS idx_batch_run_job_id
    ON batch_run (job_id);

-- batch_file_zip
CREATE INDEX IF NOT EXISTS idx_batch_file_zip_batch_id
    ON batch_file_zip (batch_id);

-- batch_file
CREATE INDEX IF NOT EXISTS idx_batch_file_file_zip_id
    ON batch_file (file_zip_id);

-- batch_file_detail
CREATE INDEX IF NOT EXISTS idx_bfd_file_init_id
    ON batch_file_detail (file_id)
    WHERE status = 'INIT';

CREATE INDEX IF NOT EXISTS idx_batch_file_detail_file_id_status
    ON batch_file_detail (file_id, status);

CREATE INDEX IF NOT EXISTS idx_batch_file_detail_line_hash
    ON batch_file_detail (line_hash)
    WHERE status != 'ERROR';

-- location
CREATE INDEX IF NOT EXISTS idx_location_geoname_id
    ON location (geoname_id);

CREATE INDEX IF NOT EXISTS idx_location_code_level
    ON location (location_code, location_level);

CREATE INDEX IF NOT EXISTS idx_location_path_ids
    ON location USING GIST (path_ids);

-- location_name
CREATE INDEX IF NOT EXISTS idx_location_name_location_id
    ON location_name (location_id);

-- ip_mapping
CREATE INDEX IF NOT EXISTS idx_im_ip_created
    ON ip_mapping (ip_notation, created_at);

CREATE INDEX IF NOT EXISTS ip_mapping_scope_idx
    ON ip_mapping USING HASH (scope);

-- ip_mapping_attribute
CREATE INDEX IF NOT EXISTS idx_ip_mapping_attribute_fkey
    ON ip_mapping_attribute (ip_mapping_id);

-- ip_span: multi-column GIST index (requires btree_gist extension)
CREATE INDEX IF NOT EXISTS idx_ip_span_gist
    ON ip_span USING GIST (user_id, valid_period, ip_range);

-- cache_sync_job: partial index covers only PENDING rows (low-selectivity optimisation)
CREATE INDEX IF NOT EXISTS idx_cache_sync_job_pending_created_at
    ON cache_sync_job (created_at)
    WHERE status = 'PENDING';