-- Role-Permission Mappings
-- DONOR permissions:
INSERT INTO role_permissions (role_id, permission_id) VALUES
(1, 1), -- donor:read
(1, 2), -- donor:create
(1, 3); -- donor:update

-- ORG_ADMIN permissions:
INSERT INTO role_permissions (role_id, permission_id) VALUES
(2, 1), -- donor:read
(2, 3), -- donor:update
(2, 4), -- donor:delete
(2, 5); -- org:manage
