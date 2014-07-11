# --- !Ups

CREATE TABLE IF NOT EXISTS users(
  id                       INTEGER PRIMARY KEY AUTOINCREMENT,
  email                    VARCHAR(255) NOT NULL,
  encrypted_password       TEXT NOT NULL,
  pw_entry_encryption_key  TEXT,
  first_name               VARCHAR(64),
  last_name                VARCHAR(64),
  active                   INTEGER DEFAULT 1,
  admin                    INTEGER DEFAULT 0
);

-- Default admin user (admin@example.com), with password "admin".
INSERT INTO users(email, encrypted_password, first_name,
                  pw_entry_encryption_key, active, admin)
VALUES ('admin@example.com',
        '$2a$10$dod.88izAFGzSxwgMT9/Ou6xqqjX5shU9wlfKEtuc4P3DinTA2R9q',
        'Administrator',
        '339f4cb5dc577c4ef75eaf4c73adad8f',
        1,
        1);

CREATE UNIQUE INDEX IF NOT EXISTS users_ix_email ON users(email);
CREATE INDEX IF NOT EXISTS users_ix_last_name ON users(last_name);

CREATE TABLE IF NOT EXISTS password_entries(
  id                       INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id                  INTEGER NOT NULL,
  name                     VARCHAR(255) NOT NULL,
  login_id                 VARCHAR(255) NULL,
  description              TEXT,
  encrypted_password       TEXT,
  notes                    TEXT,

  FOREIGN KEY(user_id) REFERENCES users(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS password_entries_ix_name ON password_entries(user_id, name);

# --- !Downs

DROP TABLE password_entries;
DROP TABLE users;
