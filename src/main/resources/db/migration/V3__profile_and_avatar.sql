ALTER TABLE users ADD COLUMN profile_picture VARCHAR(255);

UPDATE users SET profile_picture = NULL;