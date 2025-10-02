#!/usr/bin/env bash
set -euo pipefail
createdb verafin_demo || true
psql verafin_demo -f sql/schema.sql
