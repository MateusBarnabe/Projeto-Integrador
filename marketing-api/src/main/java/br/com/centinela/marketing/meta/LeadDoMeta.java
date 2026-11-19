package br.com.centinela.marketing.meta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.centinela.marketing.entrada.NormalizacaoDeContato;

/**
 * O lead como a Graph API devolve ({@code GET /{leadgen_id}}): {@code field_data} é uma lista de
 * {@code {name, values[]}}. Os campos de contato viram nome, e-mail e telefone do lead; todo o resto
 * são as respostas do formulário instantâneo, pelo nome da pergunta, que as regras {@code campo_meta}
 * pontuam (norteador §9.3). Contato não é repetido nas respostas: já tem coluna própria.
 *
 * @param nome      nome completo, ou nome e sobrenome juntos
 * @param email     já normalizado; nulo se ausente ou inválido
 * @param telefone  já normalizado (E.164 sem o {@code +}); nulo se ausente ou inválido
 * @param respostas valor de cada pergunta que não é contato: texto, ou lista quando há mais de um valor
 */
public record LeadDoMeta(String nome, String email, String telefone, Map<String, Object> respostas) {

    static final int TAMANHO_NOME = 200;
    static final int TAMANHO_EMAIL = 320;
    static final int TAMANHO_RESPOSTA = 1000;
    static final int MAXIMO_DE_RESPOSTAS = 100;

    private static final Set<String> CAMPOS_DE_EMAIL = Set.of("email", "work_email");
    private static final Set<String> CAMPOS_DE_TELEFONE = Set.of("phone_number", "phone", "work_phone_number");
    private static final Set<String> CAMPOS_DE_NOME = Set.of("full_name", "name");
    private static final Set<String> CAMPOS_PESSOAIS_FORA_DAS_RESPOSTAS = Set.of("first_name", "last_name",
            "street_address");

    public LeadDoMeta {
        respostas = respostas == null ? Map.of() : Map.copyOf(respostas);
    }

    /** Sem nome, e-mail e telefone. */
    @Override
    public String toString() {
        return "LeadDoMeta[respostas=" + respostas.size() + "]";
    }

    /** Lê o JSON da Graph API; o que não vier ou vier fora do formato é ignorado. */
    static LeadDoMeta de(JsonNode lead) {
        String nomeCompleto = null;
        String primeiro = null;
        String sobrenome = null;
        String email = null;
        String telefone = null;
        Map<String, Object> respostas = new LinkedHashMap<>();

        for (JsonNode campo : lead.path("field_data")) {
            String nome = campo.path("name").asText("").strip().toLowerCase(Locale.ROOT);
            List<String> valores = valores(campo.path("values"));
            if (nome.isEmpty() || valores.isEmpty()) {
                continue;
            }
            String primeiroValor = valores.get(0);
            if (CAMPOS_DE_NOME.contains(nome)) {
                nomeCompleto = nomeCompleto != null ? nomeCompleto : primeiroValor;
            } else if ("first_name".equals(nome)) {
                primeiro = primeiroValor;
            } else if ("last_name".equals(nome)) {
                sobrenome = primeiroValor;
            } else if (CAMPOS_DE_EMAIL.contains(nome)) {
                email = email != null ? email : primeiroValor;
            } else if (CAMPOS_DE_TELEFONE.contains(nome)) {
                telefone = telefone != null ? telefone : primeiroValor;
            } else if (!CAMPOS_PESSOAIS_FORA_DAS_RESPOSTAS.contains(nome) && respostas.size() < MAXIMO_DE_RESPOSTAS) {
                respostas.put(nome, valores.size() == 1 ? primeiroValor : List.copyOf(valores));
            }
        }

        if (nomeCompleto == null && (primeiro != null || sobrenome != null)) {
            nomeCompleto = ((primeiro == null ? "" : primeiro) + " " + (sobrenome == null ? "" : sobrenome)).strip();
        }
        String emailNormalizado = NormalizacaoDeContato.email(email);
        return new LeadDoMeta(
                limitar(NormalizacaoDeContato.nome(nomeCompleto), TAMANHO_NOME),
                emailNormalizado != null && emailNormalizado.length() <= TAMANHO_EMAIL ? emailNormalizado : null,
                NormalizacaoDeContato.telefone(telefone),
                respostas);
    }

    private static List<String> valores(JsonNode valores) {
        List<String> lidos = new ArrayList<>();
        for (JsonNode valor : valores) {
            if (valor.isValueNode() && !valor.isNull()) {
                String texto = limitar(valor.asText().strip(), TAMANHO_RESPOSTA);
                if (!texto.isEmpty()) {
                    lidos.add(texto);
                }
            }
        }
        return lidos;
    }

    private static String limitar(String valor, int tamanho) {
        return valor == null || valor.length() <= tamanho ? valor : valor.substring(0, tamanho);
    }
}
