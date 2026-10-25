CREATE TABLE click_daily (
    code           VARCHAR(9)   NOT NULL,
    day            DATE         NOT NULL,
    clicks         BIGINT       NOT NULL,
    blocked        BIGINT       NOT NULL,
    last_click_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (code, day)
);

CREATE TABLE click_daily_country (
    code     VARCHAR(9)  NOT NULL,
    day      DATE        NOT NULL,
    country  CHAR(2)     NOT NULL,
    clicks   BIGINT      NOT NULL,
    PRIMARY KEY (code, day, country)
);

CREATE TABLE click_daily_referrer (
    code      VARCHAR(9)    NOT NULL,
    day       DATE          NOT NULL,
    referrer  VARCHAR(253)  NOT NULL,
    clicks    BIGINT        NOT NULL,
    PRIMARY KEY (code, day, referrer)
);

CREATE INDEX click_daily_country_day ON click_daily_country (day);
CREATE INDEX click_daily_referrer_day ON click_daily_referrer (day);
