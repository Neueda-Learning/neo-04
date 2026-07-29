--liquibase formatted sql

-- The change b9c82e9 tried to make by editing 005 in place, done the way it has to be done.
--
-- 005 had already applied to dev, so editing it changed its check sum and every deploy after
-- that died in Liquibase before the app could start. 005 is now back to the bytes dev ran and
-- the data change lives here, where it can run once.
--
-- WHY UPDATE AND NOT A NEW VERSION. watchlist_entry.version is a foreign key onto
-- screening_config.version, which looks like the shape where you seed a new version instead of
-- touching old rows. It is not, here: ScreeningConfigService assigns new versions at RUNTIME as
-- findMaxVersion() + 1 from the admin UI, so a migration that inserted a v2 would race that
-- counter and collide with any version an operator had already created. The seeded rows are v1,
-- screening_config v1 is the current version, and the matcher reads
-- findAllByVersionOrderByIdAsc(config.getVersion()) — so updating the v1 rows is exactly what
-- the matcher sees.
--
-- NEVER EDIT THIS FILE once it has run — add a new change set instead.

--changeset neo-04:007-correct-watchlist-seed-names
--comment Replace four seeded watchlist rows that named real public figures with the scenario-corpus names.
UPDATE watchlist_entry SET first_name = 'Marek ', last_name = 'Nowak',   date_of_birth = '1961-04-19', list_type = 'SANCTIONED' WHERE version = 1 AND list_id = 'WL-001';
UPDATE watchlist_entry SET first_name = 'Viktor', last_name = 'Petrov',  date_of_birth = '1975-08-22', list_type = 'SANCTIONED' WHERE version = 1 AND list_id = 'WL-002';
UPDATE watchlist_entry SET first_name = 'Amara',  last_name = 'Diallo',  date_of_birth = '1969-02-10', list_type = 'SANCTIONED' WHERE version = 1 AND list_id = 'WL-003';
UPDATE watchlist_entry SET first_name = 'DUO',    last_name = 'LINGGUO', date_of_birth = '1953-06-15', list_type = 'POLITICAL'   WHERE version = 1 AND list_id = 'WL-025';
--rollback UPDATE watchlist_entry SET first_name = 'AHMED',  last_name = 'AL-QAEDA',  date_of_birth = '1965-03-15', list_type = 'TERRORIST' WHERE version = 1 AND list_id = 'WL-001';
--rollback UPDATE watchlist_entry SET first_name = 'hassan', last_name = 'nasrallah', date_of_birth = '1960-08-31', list_type = 'TERRORIST' WHERE version = 1 AND list_id = 'WL-002';
--rollback UPDATE watchlist_entry SET first_name = 'Masoud', last_name = 'Rajavi',    date_of_birth = '1948-12-08', list_type = 'TERRORIST' WHERE version = 1 AND list_id = 'WL-003';
--rollback UPDATE watchlist_entry SET first_name = 'XI',     last_name = 'jinping',   date_of_birth = '1953-06-15', list_type = 'POLITICAL' WHERE version = 1 AND list_id = 'WL-025';
