-- [jooq ignore start]
GRANT USAGE ON SCHEMA public TO dashboard_app, link_reader;

GRANT SELECT, INSERT, UPDATE ON links TO dashboard_app;
GRANT USAGE ON SEQUENCE link_change_seq TO dashboard_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON click_daily, click_daily_country, click_daily_referrer TO dashboard_app;

GRANT SELECT ON link_snapshot TO link_reader;
-- [jooq ignore stop]
