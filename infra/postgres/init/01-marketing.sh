#!/bin/sh
# Cria o schema marketing e os dois usuários do Contrato §7.1, como a plataforma faz:
#   own_marketing: dono do schema, roda o Flyway
#   usr_marketing: roda a aplicação, só com leitura e escrita nas tabelas
# Roda uma vez, na criação do volume do Postgres (docker-entrypoint-initdb.d).
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
CREATE ROLE own_marketing LOGIN PASSWORD '${MARKETING_OWNER_SENHA:-own_marketing}';
CREATE ROLE usr_marketing LOGIN PASSWORD '${MARKETING_APP_SENHA:-usr_marketing}';

CREATE SCHEMA marketing AUTHORIZATION own_marketing;
GRANT USAGE ON SCHEMA marketing TO usr_marketing;

ALTER DEFAULT PRIVILEGES FOR ROLE own_marketing IN SCHEMA marketing
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO usr_marketing;
ALTER DEFAULT PRIVILEGES FOR ROLE own_marketing IN SCHEMA marketing
    GRANT USAGE, SELECT ON SEQUENCES TO usr_marketing;

ALTER ROLE own_marketing SET search_path = marketing;
ALTER ROLE usr_marketing SET search_path = marketing;
EOSQL
