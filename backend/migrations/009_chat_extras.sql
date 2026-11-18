-- Chat (21) features kept from Assignment 2: reply quotes / shared post / call summary (extra JSON), read receipts ("Seen"),
-- typing indicator and the heart reaction on a message.
ALTER TABLE messages ADD COLUMN extra TEXT NULL;
CREATE TABLE IF NOT EXISTS chat_reads (
 chat_id CHAR(36) NOT NULL, user_id CHAR(36) NOT NULL, last_read_at DATETIME(6) NOT NULL,
 PRIMARY KEY(chat_id,user_id), FOREIGN KEY(chat_id) REFERENCES chats(id), FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS chat_typing (
 chat_id CHAR(36) NOT NULL, user_id CHAR(36) NOT NULL, until DATETIME(6) NOT NULL,
 PRIMARY KEY(chat_id,user_id), FOREIGN KEY(chat_id) REFERENCES chats(id), FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS message_hearts (
 message_id CHAR(36) NOT NULL, user_id CHAR(36) NOT NULL, created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(message_id,user_id), FOREIGN KEY(message_id) REFERENCES messages(id), FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
