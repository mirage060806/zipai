ALTER TABLE property_crawl_history ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'property';
UPDATE property_crawl_history SET category='market' WHERE UPPER(source_name) LIKE '%MOLIT%' OR UPPER(source_name) LIKE '%APT_TRADE%';
CREATE INDEX idx_property_history_category ON property_crawl_history(category, started_at);
CREATE TABLE finance_import_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NOT NULL,
    received_count INT NOT NULL DEFAULT 0,
    baseline_count INT NOT NULL DEFAULT 0,
    unchanged_count INT NOT NULL DEFAULT 0,
    detected_count INT NOT NULL DEFAULT 0,
    missing_count INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    KEY idx_finance_history_started (started_at)
);
