CREATE SEQUENCE link_change_seq;

CREATE TABLE links (
    code        VARCHAR(9)     NOT NULL,
    owner_id    UUID           NOT NULL,
    target_url  VARCHAR(2048)  NOT NULL,
    status      VARCHAR(16)    NOT NULL,
    version     INT            NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    change_seq  BIGINT         NOT NULL DEFAULT nextval('link_change_seq'),
    PRIMARY KEY (code)
);

CREATE INDEX links_owner_list ON links (owner_id, status, created_at DESC, code);
CREATE INDEX links_owner_newest ON links (owner_id, created_at DESC, code DESC);
CREATE INDEX links_change_seq ON links (change_seq);
CREATE INDEX links_updated_at ON links (updated_at);
