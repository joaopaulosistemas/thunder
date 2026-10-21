#!/bin/sh
# Replica de leitura (streaming replication): na primeira subida copia o primario
# com pg_basebackup (-R grava standby.signal e primary_conninfo) e depois sobe como standby.
set -e

DATA=/var/lib/postgresql/data

if [ ! -s "$DATA/PG_VERSION" ]; then
  until pg_basebackup -h postgres -U replicator -D "$DATA" -Xs -R; do
    echo "aguardando o primario..."
    sleep 2
  done
  chown -R postgres:postgres "$DATA"
  chmod 700 "$DATA"
fi

exec su-exec postgres postgres
