#!/usr/bin/env bash
set -euo pipefail

: "${DB_URL:=jdbc:postgresql://localhost:5432/verafin_demo}"
: "${DB_USER:=$USER}"
: "${DB_PASS:=}"

echo "Seeding schema..."
psql "${DB_URL#*//*/}" -c "" >/dev/null 2>&1 || true  # no-op if not local
psql verafin_demo -f sql/schema.sql || true

echo "Building (Maven required)..."
mvn -q -DskipTests package

echo "Running..."
java -jar target/banking-onboarding-pipeline-1.0.0-jar-with-dependencies.jar data/raw/transactions_raw.csv
