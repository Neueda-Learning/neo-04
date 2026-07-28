--liquibase formatted sql

--changeset neo-04:004-seed-screening-config-v1
--comment Seed initial v1 screening configuration for local/demo environments.
INSERT INTO screening_config (version, sampling_frequency, current_version, created_by)
VALUES (1, 7, TRUE, 'seed-data');
--rollback DELETE FROM screening_config WHERE version = 1;

--changeset neo-04:004-seed-country-risk-entry-v1
--comment Seed initial v1 high-risk country list for screening demo/testing.
INSERT INTO country_risk_entry (version, country_code, country_name, risk_level)
VALUES
  (1, 'IR', 'Iran', 'HIGH'),
  (1, 'KP', 'North Korea', 'HIGH'),
  (1, 'SY', 'Syria', 'HIGH'),
  (1, 'BY', 'Belarus', 'HIGH'),
  (1, 'MM', 'Myanmar', 'HIGH');
--rollback DELETE FROM country_risk_entry WHERE version = 1 AND country_code IN ('IR', 'KP', 'SY', 'BY', 'MM');
