CREATE TABLE IF NOT EXISTS message_attachments (
 message_id CHAR(36) NOT NULL, media_id CHAR(36) NOT NULL, PRIMARY KEY(message_id,media_id),
 FOREIGN KEY(message_id) REFERENCES messages(id), FOREIGN KEY(media_id) REFERENCES media(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS notifications (
 id CHAR(36) PRIMARY KEY, recipient_id CHAR(36) NOT NULL, actor_id CHAR(36) NOT NULL,
 type VARCHAR(30) NOT NULL, resource_id CHAR(36) NOT NULL, body VARCHAR(500) NOT NULL,
 created_at DATETIME(6) NOT NULL, read_at DATETIME(6) NULL,
 FOREIGN KEY(recipient_id) REFERENCES users(id), FOREIGN KEY(actor_id) REFERENCES users(id), KEY ix_notifications(recipient_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS push_jobs (
 id CHAR(36) PRIMARY KEY, notification_id CHAR(36) NOT NULL, device_id CHAR(36) NOT NULL,
 payload JSON NOT NULL, attempts INT NOT NULL DEFAULT 0, next_attempt_at DATETIME(6) NOT NULL,
 lease_until DATETIME(6) NULL, state VARCHAR(20) NOT NULL DEFAULT 'queued', last_error VARCHAR(500) NULL,
 FOREIGN KEY(notification_id) REFERENCES notifications(id), FOREIGN KEY(device_id) REFERENCES devices(id),
 UNIQUE KEY uq_notification_device(notification_id,device_id), KEY ix_push_due(state,next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS presence_sessions (
 session_id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL, last_heartbeat DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL,
 FOREIGN KEY(session_id) REFERENCES sessions(id), FOREIGN KEY(user_id) REFERENCES users(id), KEY ix_presence(user_id,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
