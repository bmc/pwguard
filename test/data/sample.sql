-- password is "foobar" (no quotes)
insert into users(email, encrypted_password, first_name, last_name,
                  active, admin)
  values('foo@example.com', 
         '$2a$10$g8n2p9DcJe81gpf2Qh8SKO13mPhVR6Hw2Sn07uId4ORL2GeJL3ZLq',
	 'Jimmy', 'Foo', 1, 0);

-- password is "foobar"
insert into password_entries(user_id, name, description, encrypted_password)
                      values(1, 'www.example.org', 'Examples for all',
                             'e1cfc8dce118ea8e');
-- password is "woo$90"
insert into password_entries(user_id, name, description, encrypted_password)
                      values(1, 'yahoo.com', 'Yahoo! messenger and email',
                             '7d3fa1f3ffda1f0a');

-- password is "foobar"
insert into password_entries(user_id, name, description, encrypted_password)
                      values(2, 'www.example.org', 'Examples for all',
                             'e1cfc8dce118ea8e');
-- password is "woo$90"
insert into password_entries(user_id, name, description, encrypted_password)
                      values(2, 'yahoo.com', 'Yahoo! messenger and email',
                             '7d3fa1f3ffda1f0a');

