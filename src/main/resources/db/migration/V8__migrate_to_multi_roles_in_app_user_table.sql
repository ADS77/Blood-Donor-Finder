CREATE TABLE user_roles (
                            user_id BIGINT NOT NULL,
                            role VARCHAR(50) NOT NULL,
                            PRIMARY KEY (user_id, role),
                            FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

INSERT INTO user_roles (user_id, role)
SELECT id, role FROM app_user WHERE role IS NOT NULL;

-- ALTER TABLE app_user DROP COLUMN role;

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);