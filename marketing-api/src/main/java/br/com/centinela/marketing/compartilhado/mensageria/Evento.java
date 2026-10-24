package br.com.centinela.marketing.compartilhado.mensageria;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Envelope de toda mensagem publicada pelo módulo (Contrato §9.7, norteador §8). É o mesmo para
 * eventos em {@code marketing.eventos} e pedidos em {@code identity.entrada}.
 *
 * @param id           chave de idempotência de quem consome
 * @param tipo         {@code modulo.entidade.acao}, com o verbo no particípio; também é a routing key
 * @param versao       sobe quando o formato de {@code dados} muda de forma incompatível
 * @param ocorridoEm   sempre em UTC
 * @param usuarioId    {@code sub} do token; nulo em rotina, rota pública ou token de serviço
 * @param correlacaoId {@code X-Request-Id} da requisição que originou o evento, quando houver
 * @param dados        conteúdo do evento, sem senha, token, segredo ou dado pessoal desnecessário
 */
public record Evento(
        UUID id,
        String tipo,
        int versao,
        UUID tenantId,
        String moduloOrigem,
        OffsetDateTime ocorridoEm,
        UUID usuarioId,
        String correlacaoId,
        Object dados) {

    public static final String MODULO_ORIGEM = "marketing";
}
