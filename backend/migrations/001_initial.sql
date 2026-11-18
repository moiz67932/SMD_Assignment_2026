CREATE TABLE IF NOT EXISTS users (
 id CHAR(36) PRIMARY KEY, email VARCHAR(254) NOT NULL, email_normalized VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL, display_name VARCHAR(100) NOT NULL DEFAULT '', username VARCHAR(30) NULL,
 username_normalized VARCHAR(30) NULL UNIQUE, bio VARCHAR(500) NOT NULL DEFAULT '', photo_media_id CHAR(36) NULL,
 cover_media_id CHAR(36) NULL, profile_setup_complete BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), updated_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS sessions (
 id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE,
 device_id VARCHAR(128) NULL, issued_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL,
 revoked_at DATETIME(6) NULL, FOREIGN KEY(user_id) REFERENCES users(id), KEY ix_session_expiry(expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS processed_actions (
 actor_id CHAR(36) NOT NULL, action_uuid CHAR(36) NOT NULL, operation VARCHAR(80) NOT NULL,
 request_hash CHAR(64) NOT NULL, response_json JSON NOT NULL, committed_at DATETIME(6) NOT NULL,
 PRIMARY KEY(actor_id, action_uuid), FOREIGN KEY(actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS devices (
 id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL, device_key VARCHAR(128) NOT NULL,
 fcm_token VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 updated_at DATETIME(6) NOT NULL, UNIQUE KEY uq_device_user(user_id,device_key), UNIQUE KEY uq_fcm_token(fcm_token),
 FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
