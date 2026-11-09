CREATE TABLE user_roles (
                            user_id UUID NOT NULL,
                            role VARCHAR(50) NOT NULL,
                            PRIMARY KEY (user_id, role),
                            FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

