CREATE TABLE IF NOT EXISTS chats (
 id CHAR(36) PRIMARY KEY, user_low CHAR(36) NOT NULL, user_high CHAR(36) NOT NULL,
 created_at DATETIME(6) NOT NULL, UNIQUE KEY uq_chat_pair(user_low,user_high),
 FOREIGN KEY(user_low) REFERENCES users(id), FOREIGN KEY(user_high) REFERENCES users(id), CHECK(user_low < user_high)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS messages (
 id CHAR(36) PRIMARY KEY, chat_id CHAR(36) NOT NULL, sender_id CHAR(36) NOT NULL,
 body TEXT NOT NULL, type VARCHAR(20) NOT NULL DEFAULT 'text', created_at DATETIME(6) NOT NULL,
 edited_at DATETIME(6) NULL, deleted_at DATETIME(6) NULL, version BIGINT NOT NULL DEFAULT 1,
 FOREIGN KEY(chat_id) REFERENCES chats(id), FOREIGN KEY(sender_id) REFERENCES users(id), KEY ix_chat_messages(chat_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
