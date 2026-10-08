-- Existing anonymous records remain unassigned.
ALTER TABLE room_visit ADD COLUMN applicant_user_id BIGINT NULL;
ALTER TABLE room_visit ADD COLUMN owner_user_id BIGINT NULL;
ALTER TABLE room_visit ADD INDEX idx_visit_applicant (applicant_user_id, visit_id);
ALTER TABLE room_visit ADD INDEX idx_visit_owner (owner_user_id, visit_id);
ALTER TABLE room_offer ADD COLUMN owner_user_id BIGINT NULL;
ALTER TABLE room_offer ADD INDEX idx_offer_owner (owner_user_id, offer_id);
