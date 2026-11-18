-- Sign-up fields kept from Assignment 2 (screen 02) and the extra Edit profile fields (screen 16).
ALTER TABLE users
  ADD COLUMN first_name VARCHAR(60) NOT NULL DEFAULT '',
  ADD COLUMN last_name VARCHAR(60) NOT NULL DEFAULT '',
  ADD COLUMN birthday DATE NULL,
  ADD COLUMN gender VARCHAR(20) NOT NULL DEFAULT '',
  ADD COLUMN website VARCHAR(200) NOT NULL DEFAULT '',
  ADD COLUMN phone VARCHAR(30) NOT NULL DEFAULT '';
