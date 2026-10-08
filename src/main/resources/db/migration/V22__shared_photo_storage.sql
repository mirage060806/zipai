CREATE TABLE zipai_photo_budget (
    id INT PRIMARY KEY,
    used_bytes BIGINT NOT NULL DEFAULT 0
);
INSERT INTO zipai_photo_budget (id, used_bytes) VALUES (1, 0);

CREATE TABLE zipai_photo_blob (
    scope VARCHAR(20) NOT NULL,
    stored_name VARCHAR(100) NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    byte_size INT NOT NULL,
    photo_data MEDIUMBLOB NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (scope, stored_name)
);
