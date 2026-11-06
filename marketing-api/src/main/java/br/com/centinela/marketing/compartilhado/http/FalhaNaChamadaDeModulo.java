package br.com.centinela.marketing.compartilhado.http;

import java.util.List;

import br.com.centinela.marketing.compartilhado.web.ErroCampo;

/**
 * Falha previsível numa chamada a outro módulo. Quem chama decide o que fazer pelo {@link Motivo}:
 * com {@link Motivo#INDISPONIVEL}, a tela mostra o que conseguiu e a rotina deixa a leitura
 * pendente (Contrato §9.6), em vez de responder {@code 500}.
 *
 * <p>A mensagem da exceção nunca traz token, segredo, corpo nem a mensagem do outro módulo (que
 * pode ter dado pessoal); essa fica em {@link #mensagemDoModulo()} e em {@link #erros()}.
 */
public class FalhaNaChamadaDeModulo extends RuntimeException {

    /** Classificação da falha, independente do módulo chamado. */
    public enum Motivo {
        /** Timeout, conexão recusada, nome que não resolve ou {@code 5xx}. Vale tentar de novo depois. */
        INDISPONIVEL,
        /** {@code 404}: o registro não existe ou é de outro tenant. */
        NAO_ENCONTRADO,
        /** {@code 401}, mesmo depois de renovar o token de serviço, ou credencial recusada pelo identity. */
        NAO_AUTENTICADO,
        /** {@code 403}: a permissão não declara {@code servicos: [marketing]}. */
        SEM_PERMISSAO,
        /** Outro {@code 4xx} ({@code 400}, {@code 409}, {@code 422}...): o pedido foi recusado. */
        REJEITADA,
        /** Resposta fora do envelope {@code {success, data, message, errors}} ou ilegível. */
        RESPOSTA_INVALIDA,
        /** Falta configuração local, como o {@code SVC_CLIENT_SECRET}. */
        NAO_CONFIGURADO
    }

    private final ModuloDestino modulo;
    private final Motivo motivo;
    private final Integer status;
    private final String mensagemDoModulo;
    private final List<ErroCampo> erros;

    public FalhaNaChamadaDeModulo(ModuloDestino modulo, Motivo motivo, Integer status,
            String mensagemDoModulo, List<ErroCampo> erros, Throwable causa) {
        super(descrever(modulo, motivo, status), causa);
        this.modulo = modulo;
        this.motivo = motivo;
        this.status = status;
        this.mensagemDoModulo = mensagemDoModulo;
        this.erros = erros == null ? List.of() : List.copyOf(erros);
    }

    public ModuloDestino modulo() {
        return modulo;
    }

    public Motivo motivo() {
        return motivo;
    }

    /** Status HTTP recebido, ou nulo quando não houve resposta. */
    public Integer status() {
        return status;
    }

    /** {@code message} do envelope do outro módulo, quando veio. Não vai para log. */
    public String mensagemDoModulo() {
        return mensagemDoModulo;
    }

    /** {@code errors[]} do envelope do outro módulo, quando veio. */
    public List<ErroCampo> erros() {
        return erros;
    }

    public boolean indisponivel() {
        return motivo == Motivo.INDISPONIVEL;
    }

    public boolean naoEncontrado() {
        return motivo == Motivo.NAO_ENCONTRADO;
    }

    private static String descrever(ModuloDestino modulo, Motivo motivo, Integer status) {
        return "Chamada ao módulo " + modulo.codigo() + " falhou: " + motivo
                + (status == null ? "" : " (HTTP " + status + ")");
    }
}
