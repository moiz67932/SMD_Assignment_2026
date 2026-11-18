CREATE TABLE IF NOT EXISTS comment_likes (
 comment_id CHAR(36) NOT NULL, user_id CHAR(36) NOT NULL, created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(comment_id,user_id), FOREIGN KEY(comment_id) REFERENCES comments(id), FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
