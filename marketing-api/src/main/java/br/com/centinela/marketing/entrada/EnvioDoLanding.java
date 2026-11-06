package br.com.centinela.marketing.entrada;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Schema {@code Envio} de {@code GET /api/landing/envios/{id}} ({@code landing.yaml} do infra): o que o
 * Marketing lê do envio depois de {@code landing.formulario.recebido}. Tem dado pessoal (nome, e-mail,
 * respostas): nunca vai para log ({@link #toString()} mascara).
 *
 * <p>O {@code Envio} não tem telefone fora das respostas: o telefone é a primeira resposta do tipo
 * {@code TELEFONE}. O e-mail é o {@code email} do envio (campo mapeado para o contato) ou, sem ele, a
 * primeira resposta do tipo {@code EMAIL}. Envio anonimizado no Landing vem com {@code anonimizado =
 * true}, sem nome e e-mail e com o {@code valor} das respostas nulo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnvioDoLanding(
        UUID id,
        UUID formularioId,
        String formularioNome,
        UUID paginaId,
        String canal,
        String nome,
        String email,
        OffsetDateTime recebidoEm,
        boolean anonimizado,
        List<Resposta> respostas,
        Origem origem,
        Consentimento consentimento,
        UUID empresaId,
        UUID contatoId) {

    static final String TIPO_EMAIL = "EMAIL";
    static final String TIPO_TELEFONE = "TELEFONE";

    /** {@code RespostaEnvio}: na ordem dos campos, com o rótulo da época do envio. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resposta(UUID campoId, String rotulo, String tipo, Object valor) {
        @Override
        public String toString() {
            return "Resposta[campoId=" + campoId + ", tipo=" + tipo + ", valor=***]";
        }
    }

    /** {@code Origem}: primeira e última origem do visitante. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Origem(Toque primeira, Toque ultima) {
    }

    /** {@code ToqueOrigem}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Toque(String utmSource, String utmMedium, String utmCampaign, String utmTerm, String utmContent,
            String referenciador, String paginaEntrada, OffsetDateTime ocorridoEm) {

        static final Toque NENHUM = new Toque(null, null, null, null, null, null, null, null);
    }

    /** {@code ConsentimentoRegistrado}: aceite, texto exibido e data. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Consentimento(Boolean aceito, String texto, OffsetDateTime aceitoEm) {
        @Override
        public String toString() {
            return "Consentimento[aceito=" + aceito + ", aceitoEm=" + aceitoEm + "]";
        }
    }

    /** E-mail do contato: o do envio ou, sem ele, a primeira resposta de e-mail. Sem normalizar. */
    public String emailDoContato() {
        return temTexto(email) ? email : primeiraResposta(TIPO_EMAIL);
    }

    /** Telefone do contato: a primeira resposta de telefone. Sem normalizar. */
    public String telefoneDoContato() {
        return primeiraResposta(TIPO_TELEFONE);
    }

    /** Última origem (UTM e página de entrada); sem ela, a primeira; sem as duas, um toque vazio. */
    public Toque ultimoToque() {
        if (origem != null && origem.ultima() != null) {
            return origem.ultima();
        }
        return primeiroToque();
    }

    public Toque primeiroToque() {
        if (origem != null && origem.primeira() != null) {
            return origem.primeira();
        }
        return origem != null && origem.ultima() != null ? origem.ultima() : Toque.NENHUM;
    }

    /** Cópia das respostas para {@code leads.dados_formulario}: {@code campoId}, {@code rotulo}, {@code tipo}, {@code valor}. */
    public List<Map<String, Object>> respostasParaCopia() {
        List<Map<String, Object>> copia = new ArrayList<>();
        if (respostas == null) {
            return copia;
        }
        for (Resposta resposta : respostas) {
            if (resposta == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("campoId", resposta.campoId() == null ? null : resposta.campoId().toString());
            item.put("rotulo", resposta.rotulo());
            item.put("tipo", resposta.tipo());
            item.put("valor", resposta.valor());
            copia.add(item);
        }
        return copia;
    }

    public int quantidadeDeRespostas() {
        return respostas == null ? 0 : respostas.size();
    }

    private String primeiraResposta(String tipo) {
        if (respostas == null) {
            return null;
        }
        return respostas.stream()
                .filter(resposta -> resposta != null && tipo.equalsIgnoreCase(resposta.tipo()))
                .map(Resposta::valor)
                .filter(valor -> valor instanceof String || valor instanceof Number)
                .map(String::valueOf)
                .filter(EnvioDoLanding::temTexto)
                .findFirst()
                .orElse(null);
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    @Override
    public String toString() {
        // Sem nome, e-mail, respostas nem o texto do termo
        return "EnvioDoLanding[id=" + id + ", formularioId=" + formularioId + ", canal=" + canal + ", anonimizado="
                + anonimizado + ", respostas=" + quantidadeDeRespostas() + "]";
    }
}
