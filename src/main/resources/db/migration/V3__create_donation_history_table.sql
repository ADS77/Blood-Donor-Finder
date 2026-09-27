CREATE TABLE donation_history (
                                  id              UUID PRIMARY KEY,

                                  donor_id        UUID NOT NULL,
                                  recipient_id    UUID NOT NULL,
                                  request_id      UUID NOT NULL,

                                  donation_date   TIMESTAMP NOT NULL,
                                  notes           VARCHAR(1000),

                                  verified        BOOLEAN NOT NULL DEFAULT FALSE,

                                  CONSTRAINT fk_donation_history_donor
                                      FOREIGN KEY (donor_id)
                                          REFERENCES app_user(id),

                                  CONSTRAINT fk_donation_history_recipient
                                      FOREIGN KEY (recipient_id)
                                          REFERENCES app_user(id),

                                  CONSTRAINT fk_donation_history_request
                                      FOREIGN KEY (request_id)
                                          REFERENCES blood_request(id)
);

CREATE INDEX idx_donation_history_donor
    ON donation_history(donor_id);

CREATE INDEX idx_donation_history_recipient
    ON donation_history(recipient_id);

CREATE INDEX idx_donation_history_request
    ON donation_history(request_id);

CREATE INDEX idx_donation_history_date
    ON donation_history(donation_date);