-- Standardized schema mirroring a simplified Verafin-style target
CREATE SCHEMA IF NOT EXISTS verafin_standard;

CREATE TABLE IF NOT EXISTS verafin_standard.transactions (
  id BIGSERIAL PRIMARY KEY,
  account_number        VARCHAR(20) NOT NULL,
  counterparty_account  VARCHAR(20),
  transaction_type      VARCHAR(16) NOT NULL CHECK (transaction_type IN ('WIRE','ACH','DEPOSIT')),
  amount                NUMERIC(18,2) NOT NULL CHECK (amount >= 0),
  currency_code         CHAR(3) NOT NULL CHECK (currency_code IN ('CAD','USD')),
  transaction_timestamp DATE NOT NULL,
  source_file           TEXT NOT NULL,
  raw_row_number        INT NOT NULL,
  inserted_at           TIMESTAMP DEFAULT NOW()
);
