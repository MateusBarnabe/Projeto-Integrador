package br.com.centinela.marketing.meta;

/**
 * Falha previsível numa chamada à Graph API do Meta. A mensagem nunca traz o token da página, o corpo
 * da resposta nem a mensagem de erro do Meta (que pode citar o lead): só o motivo, o status HTTP e o
 * código de erro da Graph API.
 */
public class FalhaNaGraphApi extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Motivo {
        /** Timeout, conexão recusada ou {@code 5xx}: vale tentar de novo. */
        INDISPONIVEL,
        /** {@code 401}, {@code 403} ou erro de OAuth ({@code 190}): token da página recusado. */
        NAO_AUTORIZADO,
        /** Outro {@code 4xx}: o pedido foi recusado (lead apagado, sem permissão de leitura...). */
        REJEITADA,
        /** Resposta que não é o JSON esperado. */
        RESPOSTA_INVALIDA
    }

    private final Motivo motivo;
    private final Integer status;
    private final Integer codigoDoMeta;

    public FalhaNaGraphApi(Motivo motivo, Integer status, Integer codigoDoMeta, Throwable causa) {
        super("Graph API do Meta: " + motivo + (status == null ? "" : " (HTTP " + status + ")")
                + (codigoDoMeta == null ? "" : " (código " + codigoDoMeta + ")"), causa);
        this.motivo = motivo;
        this.status = status;
        this.codigoDoMeta = codigoDoMeta;
    }

    public Motivo motivo() {
        return motivo;
    }

    public Integer status() {
        return status;
    }

    public Integer codigoDoMeta() {
        return codigoDoMeta;
    }
}
