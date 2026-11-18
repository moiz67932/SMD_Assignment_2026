CREATE TABLE IF NOT EXISTS media (
 id CHAR(36) PRIMARY KEY, owner_id CHAR(36) NOT NULL, storage_path VARCHAR(500) NOT NULL,
 mime_type VARCHAR(120) NOT NULL, original_name VARCHAR(255) NOT NULL, byte_size BIGINT UNSIGNED NOT NULL,
 sha256 CHAR(64) NOT NULL, created_at DATETIME(6) NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES users(id), KEY ix_media_owner(owner_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS friendships (
 user_low CHAR(36) NOT NULL, user_high CHAR(36) NOT NULL, created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(user_low,user_high), FOREIGN KEY(user_low) REFERENCES users(id), FOREIGN KEY(user_high) REFERENCES users(id),
 CHECK(user_low < user_high), KEY ix_friend_high(user_high,user_low)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS friend_requests (
 id CHAR(36) PRIMARY KEY, sender_id CHAR(36) NOT NULL, receiver_id CHAR(36) NOT NULL,
 state ENUM('pending','accepted','declined','cancelled') NOT NULL, created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL, UNIQUE KEY uq_active_pair(sender_id,receiver_id,state),
 FOREIGN KEY(sender_id) REFERENCES users(id), FOREIGN KEY(receiver_id) REFERENCES users(id),
 KEY ix_request_inbox(receiver_id,state,created_at), CHECK(sender_id <> receiver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS posts (
 id CHAR(36) PRIMARY KEY, author_id CHAR(36) NOT NULL, body TEXT NOT NULL, created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL, deleted_at DATETIME(6) NULL, version BIGINT NOT NULL DEFAULT 1,
 FOREIGN KEY(author_id) REFERENCES users(id), KEY ix_feed(author_id,created_at,id), KEY ix_post_time(created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS post_media (
 post_id CHAR(36) NOT NULL, media_id CHAR(36) NOT NULL, position SMALLINT UNSIGNED NOT NULL,
 PRIMARY KEY(post_id,position), UNIQUE KEY uq_post_media(post_id,media_id), FOREIGN KEY(post_id) REFERENCES posts(id), FOREIGN KEY(media_id) REFERENCES media(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS post_reactions (
 post_id CHAR(36) NOT NULL, user_id CHAR(36) NOT NULL, reaction ENUM('Like','Love','Laugh','Wow','Sad','Angry') NOT NULL,
 updated_at DATETIME(6) NOT NULL, PRIMARY KEY(post_id,user_id), FOREIGN KEY(post_id) REFERENCES posts(id), FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS comments (
 id CHAR(36) PRIMARY KEY, post_id CHAR(36) NOT NULL, author_id CHAR(36) NOT NULL, parent_id CHAR(36) NULL,
 body TEXT NOT NULL, media_id CHAR(36) NULL, created_at DATETIME(6) NOT NULL, deleted_at DATETIME(6) NULL,
 FOREIGN KEY(post_id) REFERENCES posts(id), FOREIGN KEY(author_id) REFERENCES users(id), FOREIGN KEY(parent_id) REFERENCES comments(id), FOREIGN KEY(media_id) REFERENCES media(id), KEY ix_comments(post_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS stories (
 id CHAR(36) PRIMARY KEY, owner_id CHAR(36) NOT NULL, media_id CHAR(36) NOT NULL,
 label VARCHAR(80) NOT NULL DEFAULT '', audience ENUM('public','close_friends') NOT NULL,
 created_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL, deleted_at DATETIME(6) NULL,
 FOREIGN KEY(owner_id) REFERENCES users(id), FOREIGN KEY(media_id) REFERENCES media(id), KEY ix_story_expiry(owner_id,audience,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS story_views (
 story_id CHAR(36) NOT NULL, viewer_id CHAR(36) NOT NULL, viewed_at DATETIME(6) NOT NULL,
 PRIMARY KEY(story_id,viewer_id), FOREIGN KEY(story_id) REFERENCES stories(id), FOREIGN KEY(viewer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS close_friends (
 owner_id CHAR(36) NOT NULL, member_id CHAR(36) NOT NULL, created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(owner_id,member_id), FOREIGN KEY(owner_id) REFERENCES users(id), FOREIGN KEY(member_id) REFERENCES users(id), CHECK(owner_id <> member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
