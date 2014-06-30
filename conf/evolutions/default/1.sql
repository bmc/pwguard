# --- !Ups

CREATE TABLE IF NOT EXISTS users(
  id                       INTEGER PRIMARY KEY AUTOINCREMENT,
  email                    VARCHAR(255) NOT NULL,
  encrypted_password       TEXT,
  pw_entry_encryption_key  TEXT,
  first_name               VARCHAR(64),
  last_name                VARCHAR(64),
  active                   BOOLEAN DEFAULT 'true'
);

CREATE UNIQUE INDEX IF NOT EXISTS users_ix_email ON users(email);
CREATE INDEX IF NOT EXISTS users_ix_last_name ON users(last_name);

CREATE TABLE IF NOT EXISTS password_entries(
  id                       INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id                  INTEGER NOT NULL,
  name                     VARCHAR(255) NOT NULL,
  description              TEXT,
  encrypted_password       TEXT,
  notes                    TEXT,

  FOREIGN KEY(user_id) REFERENCES users(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS password_entries_ix_name ON password_entries(user_id, name);

# --- !Downs

DROP TABLE password_entries;
DROP TABLE users;
