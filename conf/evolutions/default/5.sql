# --- !Ups

ALTER TABLE password_entry_extra_fields ADD COLUMN is_password INTEGER NOT NULL DEFAULT 0;

# --- !Downs

UPDATE password_entry_extra_fields SET is_password = 0;
