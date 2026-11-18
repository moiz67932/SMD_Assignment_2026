-- Create post (07) extras from Assignment 2: colour background of a text post, feeling, check-in, event and tagged friends.
-- Stored as one small JSON text so the feed returns them with the post.
ALTER TABLE posts ADD COLUMN details TEXT NULL;
