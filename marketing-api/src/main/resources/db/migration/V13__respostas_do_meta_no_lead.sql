-- E4: respostas do formulário instantâneo do Meta (norteador §11). O job 4.1 pontua o lead do Meta com as
-- regras `campo_meta` na hora, mas uma nova pontuação (por exemplo, quando o mesmo lead envia um
-- formulário do Landing) só soma essas regras se as respostas ficarem com o lead. Ficam aqui, pelo nome
-- da pergunta: {"qual_o_seu_cargo": "Decisor", "interesses": ["a", "b"]}. Sem nome, e-mail e telefone,
-- que já têm coluna própria. A anonimização (job 4.3) zera a coluna.

ALTER TABLE marketing.leads ADD COLUMN respostas_meta jsonb;
