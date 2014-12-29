# --- !Ups

ALTER TABLE password_entries ADD COLUMN url TEXT NULL;

# --- !Downs

UPDATE password_entries SET url = NULL;
