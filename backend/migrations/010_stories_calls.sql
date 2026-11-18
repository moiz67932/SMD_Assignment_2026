-- Story extras kept from Assignment 2 (boomerang loop, music, tagged friends) and Agora call logs (screen 21/22).
ALTER TABLE stories ADD COLUMN extra TEXT NULL;
CREATE TABLE IF NOT EXISTS calls (
 id CHAR(36) PRIMARY KEY, caller_id CHAR(36) NOT NULL, callee_id CHAR(36) NOT NULL, video TINYINT(1) NOT NULL DEFAULT 0,
 status VARCHAR(12) NOT NULL, created_at DATETIME(6) NOT NULL, answered_at DATETIME(6) NULL, ended_at DATETIME(6) NULL,
 duration_seconds INT NOT NULL DEFAULT 0,
 FOREIGN KEY(caller_id) REFERENCES users(id), FOREIGN KEY(callee_id) REFERENCES users(id),
 KEY ix_calls_callee(callee_id,status,created_at), KEY ix_calls_caller(caller_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
