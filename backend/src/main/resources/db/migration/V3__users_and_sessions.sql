-- Accounts and server-side sessions (ADR-0005, FR-001..FR-003).

CREATE TABLE app_user (
    id                   uuid         PRIMARY KEY,                 -- UUIDv7, generated in the app
    email                varchar(254) NOT NULL,                    -- stored lower-case
    password_hash        varchar(200) NOT NULL,                    -- DelegatingPasswordEncoder ({bcrypt}...)
    display_name         varchar(100) NOT NULL,
    locale               varchar(10)  NOT NULL DEFAULT 'pl',
    role                 varchar(20)  NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    active               boolean      NOT NULL DEFAULT true,
    must_change_password boolean      NOT NULL DEFAULT false,      -- temporary password set by an admin
    created_at           timestamptz  NOT NULL,
    updated_at           timestamptz  NOT NULL,
    version              bigint       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX app_user_email_uq ON app_user (email);

-- Spring Session JDBC (spring-session-jdbc 4.1 schema-postgresql.sql). Created by Flyway, not by Spring
-- (spring.session.jdbc.initialize-schema: never), so the schema stays owned by migrations.
CREATE TABLE spring_session (
    primary_id            char(36)     NOT NULL,
    session_id            char(36)     NOT NULL,
    creation_time         bigint       NOT NULL,
    last_access_time      bigint       NOT NULL,
    max_inactive_interval int          NOT NULL,
    expiry_time           bigint       NOT NULL,
    principal_name        varchar(100),                            -- the user's id
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);
CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id char(36)     NOT NULL,
    attribute_name     varchar(200) NOT NULL,
    attribute_bytes    bytea        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
