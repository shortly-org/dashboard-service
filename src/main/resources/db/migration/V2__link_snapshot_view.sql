CREATE VIEW link_snapshot AS SELECT code, target_url, status, version, change_seq, updated_at FROM links;
