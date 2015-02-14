# --- !Ups

CREATE TABLE IF NOT EXISTS password_entry_keywords(
  id                INTEGER PRIMARY KEY,
  password_entry_id INTEGER NOT NULL,
  keyword           VARCHAR(255) NOT NULL,
  FOREIGN KEY(password_entry_id) REFERENCES password_entries(id)
);

CREATE UNIQUE INDEX pwek_ix_keyword
  ON password_entry_keywords(password_entry_id, keyword);

# --- !Downs

DROP TABLE password_entry_keywords;
