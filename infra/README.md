# infra

Arquivos que o [docker-compose.yml](../docker-compose.yml) da raiz usa para imitar o pedaço da plataforma (`infra-integrador-2026`, Grupo 2) de que o Marketing precisa: Postgres com o schema `marketing` e RabbitMQ com o vhost `plataforma`.

Na plataforma de verdade, quem cria o schema, os usuários, o vhost e as exchanges é o Grupo 2. Estes arquivos existem só para o ambiente local ficar igual ao da plataforma, com os mesmos nomes, usuários e permissões. Assim, o que funciona aqui funciona lá.

```
infra/
├── postgres/init/01-marketing.sh   cria o schema e os usuários do Marketing
└── rabbitmq/
    ├── rabbitmq.conf               manda o RabbitMQ carregar o definitions.json ao subir
    └── definitions.json            vhost, usuários, permissões e exchanges
```

## Postgres

### `postgres/init/01-marketing.sh`

Roda **uma vez só**, quando o volume do Postgres é criado. O Postgres executa tudo o que está em `docker-entrypoint-initdb.d`. O script cria:

| O quê | Detalhe |
|---|---|
| banco `plataforma` | criado pelo próprio compose (`POSTGRES_DB`) |
| usuário `own_marketing` | dono do schema; é quem roda as migrations do Flyway |
| usuário `usr_marketing` | usuário da aplicação: só `SELECT`, `INSERT`, `UPDATE` e `DELETE` nas tabelas e uso das sequências; **não cria nem altera tabela** |
| schema `marketing` | pertence ao `own_marketing` |
| privilégios padrão | toda tabela que o `own_marketing` criar já nasce liberada para o `usr_marketing` |
| `search_path` | os dois usuários enxergam o schema `marketing` por padrão |

As senhas vêm das variáveis `MARKETING_OWNER_SENHA` e `MARKETING_APP_SENHA`. Por padrão, a senha é igual ao nome do usuário.

O mesmo script é usado pelos testes da API (`FundacaoApiTest`), que o copiam para o Postgres do Testcontainers. Se ele mudar, os testes sobem com a versão nova.

**Mudou o script e quer rodar de novo?** Como ele só roda na criação do volume, é preciso apagar o volume. Isso **apaga todos os dados** do Postgres local:

```bash
docker compose down -v
docker compose up -d postgres
```

Conexão pelo seu cliente SQL (DBeaver, pgAdmin etc.): `localhost:5432`, banco `plataforma`, usuário `postgres` / senha `postgres` (administrador) ou `usr_marketing` / `usr_marketing`.

## RabbitMQ

### `rabbitmq/rabbitmq.conf`

Só aponta para o `definitions.json`. O RabbitMQ importa esse arquivo ao subir.

### `rabbitmq/definitions.json`

| O quê | Valor |
|---|---|
| vhost | `plataforma` |
| usuário `admin` (senha `admin`) | administrador, para o painel em http://localhost:15672 |
| usuário `mq_marketing` (senha `mq_marketing`) | o Marketing. **Configura** `marketing.*`; **escreve** em `marketing.*` e `identity.entrada`; **lê** `marketing.*` e `landing.eventos` |
| usuário `mq_landing` (senha `mq_landing`) | o placeholder do Landing. **Configura** e **escreve** em `landing.*`; **lê** `landing.*` e `marketing.eventos` |
| exchanges (topic, duráveis) | `marketing.eventos`, `landing.eventos` e `identity.entrada` |

Não há filas aqui: **cada módulo declara as filas que consome**. A fila `marketing.landing-leads` e a DLQ dela serão criadas pela própria API do Marketing (tarefa F4 em [PROXIMOS-PASSOS.md](../PROXIMOS-PASSOS.md)).

Os usuários `mq_*` não têm acesso ao painel web; é normal o login deles no painel falhar. Para conferir uma senha:

```bash
docker compose exec rabbitmq rabbitmqctl authenticate_user mq_marketing mq_marketing
```

As senhas ficam no arquivo como *hash*. Para trocar uma senha, gere o hash novo e substitua no `password_hash`:

```bash
docker compose exec rabbitmq rabbitmqctl hash_password nova-senha
```

Depois de mudar o `definitions.json`, reinicie o RabbitMQ: `docker compose restart rabbitmq`.

## Mailpit

Não tem arquivo aqui: é só a imagem `axllent/mailpit` no compose. Ele recebe os e-mails que o Marketing enviar (boas-vindas e aquecimento) por SMTP em `localhost:1025` e mostra tudo em http://localhost:8025.
