--liquibase formatted sql

-- Fix two data bugs flagged in PR #21:
--
-- 1. WL-001 'Marek ' has a trailing space in first_name, which defeats NameNormalizer's
--    exact match. The normalizer trims and collapses whitespace, but the database value
--    itself should be clean.
--
-- 2. WL-002 Viktor Petrov has DOB 1975-08-22, but the sidecar corpus scenario uses
--    1975-05-14. The matching engine requires both full name AND date_of_birth to match
--    for SCR_EXACT_MATCH, so the planted exact-hit scenario (SIM-Viktor) can never
--    trigger a HIT with the wrong date.
--
-- NEVER EDIT THIS FILE once it has run — add a new change set instead.

--changeset neo-04:008-fix-watchlist-dob-and-trailing-space
--comment Fix WL-001 trailing space in first_name and WL-002 incorrect date_of_birth.
UPDATE watchlist_entry SET first_name = 'Marek' WHERE version = 1 AND list_id = 'WL-001';
UPDATE watchlist_entry SET date_of_birth = '1975-05-14' WHERE version = 1 AND list_id = 'WL-002';
--rollback UPDATE watchlist_entry SET first_name = 'Marek ' WHERE version = 1 AND list_id = 'WL-001';
--rollback UPDATE watchlist_entry SET date_of_birth = '1975-08-22' WHERE version = 1 AND list_id = 'WL-002';
