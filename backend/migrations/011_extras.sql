-- Features kept from Assignment 2 that also leave Firebase: notes, live video, groups, marketplace, story highlights.
CREATE TABLE IF NOT EXISTS notes (
 user_id CHAR(36) PRIMARY KEY, body VARCHAR(60) NOT NULL, created_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS lives (
 host_id CHAR(36) PRIMARY KEY, channel VARCHAR(80) NOT NULL, started_at DATETIME(6) NOT NULL, heartbeat_at DATETIME(6) NOT NULL,
 FOREIGN KEY(host_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS live_viewers (
 host_id CHAR(36) NOT NULL, viewer_id CHAR(36) NOT NULL, seen_at DATETIME(6) NOT NULL, PRIMARY KEY(host_id,viewer_id),
 FOREIGN KEY(host_id) REFERENCES users(id), FOREIGN KEY(viewer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS groups_ (
 id CHAR(36) PRIMARY KEY, name VARCHAR(40) NOT NULL, created_by CHAR(36) NOT NULL, created_at DATETIME(6) NOT NULL,
 FOREIGN KEY(created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS group_members (
 group_id CHAR(36) NOT NULL, user_id CHAR(36) NOT NULL, PRIMARY KEY(group_id,user_id),
 FOREIGN KEY(group_id) REFERENCES groups_(id), FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS group_messages (
 id CHAR(36) PRIMARY KEY, group_id CHAR(36) NOT NULL, sender_id CHAR(36) NOT NULL, body VARCHAR(1000) NOT NULL, created_at DATETIME(6) NOT NULL,
 FOREIGN KEY(group_id) REFERENCES groups_(id), FOREIGN KEY(sender_id) REFERENCES users(id), KEY ix_group_messages(group_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS marketplace_items (
 id CHAR(36) PRIMARY KEY, seller_id CHAR(36) NOT NULL, title VARCHAR(80) NOT NULL, price VARCHAR(30) NOT NULL, created_at DATETIME(6) NOT NULL,
 FOREIGN KEY(seller_id) REFERENCES users(id), KEY ix_market_time(created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS highlights (
 user_id CHAR(36) NOT NULL, story_id CHAR(36) NOT NULL, media_id CHAR(36) NOT NULL, label VARCHAR(80) NOT NULL DEFAULT '', created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(user_id,story_id), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(media_id) REFERENCES media(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
