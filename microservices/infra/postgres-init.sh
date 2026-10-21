#!/bin/sh
# Um banco por servico (database per service): nenhum servico enxerga as tabelas dos outros.
set -e
for db in catalog orders notifications; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c "CREATE DATABASE $db OWNER $POSTGRES_USER"
done
