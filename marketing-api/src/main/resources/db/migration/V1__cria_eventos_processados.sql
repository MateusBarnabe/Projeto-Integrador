-- Obrigatória em todo schema que consome eventos, com formato fixo pelo Contrato (§9.7).
-- Guarda o id de cada evento já processado, na mesma transação do efeito, para o consumo ser idempotente.
CREATE TABLE marketing.eventos_processados (
    evento_id     uuid PRIMARY KEY,
    tipo          text NOT NULL,
    processado_em timestamptz NOT NULL DEFAULT now()
);
