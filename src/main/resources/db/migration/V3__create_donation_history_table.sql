CREATE TABLE donation_history (
                                  id BIGSERIAL PRIMARY KEY,
                                  donor_id BIGINT,
                                  recipient_id BIGINT,
                                  request_id BIGINT,
                                  donation_date TIMESTAMP,
                                  notes VARCHAR(1000),
                                  verified BOOLEAN,

    -- Foreign keys
                                  CONSTRAINT fk_donation_donor
                                      FOREIGN KEY (donor_id)
                                          REFERENCES app_user(id)
                                          ON DELETE SET NULL,

                                  CONSTRAINT fk_donation_recipient
                                      FOREIGN KEY (recipient_id)
                                          REFERENCES app_user(id)
                                          ON DELETE SET NULL,

                                  CONSTRAINT fk_donation_request
                                      FOREIGN KEY (request_id)
                                          REFERENCES blood_request(id)
                                          ON DELETE SET NULL
);

-- Indexes
CREATE INDEX idx_donation_history_donor ON donation_history(donor_id);
CREATE INDEX idx_donation_history_recipient ON donation_history(recipient_id);
CREATE INDEX idx_donation_history_request ON donation_history(request_id);
CREATE INDEX idx_donation_history_donation_date ON donation_history(donation_date);
CREATE INDEX idx_donation_history_verified ON donation_history(verified);