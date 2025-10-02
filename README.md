# Banking Data Onboarding Pipeline (Java + PostgreSQL)


- **Simulates banking transaction data** (wires, ACH, deposits)
- **Maps raw banking fields** (e.g., `acct_no` → `account_number`) to a **standardized schema**
- **Validates** rows (date format, negative amounts, enums)
- **Loads** clean data into **PostgreSQL**

## 🧩 Stack
- **Java 17** (Maven)
- **PostgreSQL** (JDBC)
- **OpenCSV** for CSV parsing
- **Unix scripts** for quick start

## 📁 Project Structure
```
.
├── data
│   ├── raw/transactions_raw.csv
│   └── output/{clean.csv, rejected.csv}
├── sql/schema.sql
├── scripts/{run_local.sh, seed_local.sh}
├── src/main/java/com/example/onboarding/App.java
├── src/main/resources/field_mappings.json
└── pom.xml
```

## 🔧 Setup

1) **PostgreSQL**: create a DB and load schema.
```bash
createdb verafin_demo || true
psql verafin_demo -f sql/schema.sql
```

2) **Environment** (for the app to connect to Postgres):
```bash
export DB_URL="jdbc:postgresql://localhost:5432/verafin_demo"
export DB_USER="$USER"
export DB_PASS=""
```

3) **Build & Run**:
```bash
mvn -q -e -DskipTests package
java -jar target/banking-onboarding-pipeline-1.0.0-jar-with-dependencies.jar data/raw/transactions_raw.csv
```

Or use the helper script:
```bash
chmod +x scripts/run_local.sh
./scripts/run_local.sh
```

## ✅ What it does
- Reads `data/raw/transactions_raw.csv`
- Applies **field mapping** from `src/main/resources/field_mappings.json`
- **Validates**:
  - `transaction_timestamp`: `YYYY-MM-DD` (UTC date allowed)
  - `transaction_type`: one of `WIRE,ACH,DEPOSIT`
  - `amount`: non-negative
  - `currency_code`: one of `CAD,USD`
  - `account_number` and `counterparty_account`: 8–16 digits
- Inserts **clean rows** into `verafin_standard.transactions`
- Writes **clean** and **rejected** CSVs to `data/output/`

## 🧪 Sample Queries
```sql
-- total CAD volume by type
SELECT currency_code, transaction_type, SUM(amount) AS total_amount
FROM verafin_standard.transactions
GROUP BY 1,2
ORDER BY 1,2;
```

## 📈 Extensions
- Add more transaction types (bill pay, ATM)
- Add SFTP ingestion + job scheduling
- Add programmatic reconciliation reports (counts per source vs. target)
- Replace enums with lookup tables; add FK to accounts
