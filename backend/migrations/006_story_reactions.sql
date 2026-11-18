CREATE TABLE IF NOT EXISTS story_reactions (
 story_id CHAR(36) NOT NULL, user_id CHAR(36) NOT NULL, reaction VARCHAR(20) NOT NULL,
 updated_at DATETIME(6) NOT NULL, PRIMARY KEY(story_id,user_id), FOREIGN KEY(story_id) REFERENCES stories(id), FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
