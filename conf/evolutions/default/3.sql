# --- !Ups

CREATE TABLE IF NOT EXISTS password_entry_extra_fields(
  id                INTEGER PRIMARY KEY,
  password_entry_id INTEGER NOT NULL,
  field_name        VARCHAR(255) NOT NULL,
  field_value       VARCHAR(255) NOT NULL,
  FOREIGN KEY(password_entry_id) REFERENCES password_entries(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS pweef_ix_id
  ON password_entry_extra_fields(password_entry_id, field_name);

# --- !Downs

DROP TABLE password_entry_extra_fields;