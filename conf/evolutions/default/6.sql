# --- !Ups

CREATE TABLE IF NOT EXISTS password_entry_security_questions(
  id                INTEGER PRIMARY KEY,
  password_entry_id INTEGER NOT NULL,
  question          VARCHAR(255) NOT NULL,
  answer            VARCHAR(255) NOT NULL,
  FOREIGN KEY(password_entry_id) REFERENCES password_entries(id)
);

CREATE UNIQUE INDEX pwesq_ix_id
  ON password_entry_security_questions(password_entry_id, question);

# --- !Downs

DROP TABLE password_entry_security_questions;
