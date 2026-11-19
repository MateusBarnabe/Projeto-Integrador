package br.com.centinela.marketing.meta;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class LeadDoMetaTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void separaOContatoDasRespostasDoFormulario() throws Exception {
        LeadDoMeta lead = ler("""
                {"id": "1", "created_time": "2026-11-05T12:00:00+0000", "field_data": [
                  {"name": "full_name", "values": ["Ana Souza"]},
                  {"name": "email", "values": ["ANA@Empresa.com"]},
                  {"name": "phone_number", "values": ["+5562999998888"]},
                  {"name": "company_name", "values": ["Centinela"]},
                  {"name": "Qual_o_seu_cargo", "values": ["Decisor"]},
                  {"name": "interesses", "values": ["crm", "automação"]},
                  {"name": "vazia", "values": []},
                  {"name": "em_branco", "values": ["  "]}]}""");

        assertThat(lead.nome()).isEqualTo("Ana Souza");
        assertThat(lead.email()).isEqualTo("ana@empresa.com");
        assertThat(lead.telefone()).isEqualTo("5562999998888");
        assertThat(lead.respostas()).containsOnlyKeys("company_name", "qual_o_seu_cargo", "interesses")
                .containsEntry("qual_o_seu_cargo", "Decisor")
                .containsEntry("interesses", List.of("crm", "automação"));
    }

    @Test
    void juntaNomeESobrenomeQuandoNaoHaNomeCompleto() throws Exception {
        LeadDoMeta lead = ler("""
                {"field_data": [{"name": "first_name", "values": ["Ana"]}, {"name": "last_name", "values": ["Souza"]},
                  {"name": "work_email", "values": ["ana@empresa.com"]}]}""");

        assertThat(lead.nome()).isEqualTo("Ana Souza");
        assertThat(lead.email()).isEqualTo("ana@empresa.com");
        assertThat(lead.respostas()).isEmpty();
    }

    @Test
    void contatoInvalidoOuAusenteViraNuloENaoQuebra() throws Exception {
        assertThat(ler("{\"field_data\": [{\"name\": \"email\", \"values\": [\"sem-arroba\"]}, "
                + "{\"name\": \"phone_number\", \"values\": [\"12\"]}]}"))
                .satisfies(lead -> {
                    assertThat(lead.email()).isNull();
                    assertThat(lead.telefone()).isNull();
                });
        assertThat(ler("{}").respostas()).isEmpty();
        assertThat(ler("{\"field_data\": \"nada\"}").nome()).isNull();
    }

    @Test
    void toStringNaoTrazDadoPessoal() throws Exception {
        LeadDoMeta lead = ler("{\"field_data\": [{\"name\": \"full_name\", \"values\": [\"Ana Souza\"]}, "
                + "{\"name\": \"email\", \"values\": [\"ana@empresa.com\"]}]}");

        assertThat(lead.toString()).doesNotContain("Ana", "ana@empresa.com");
    }

    private static LeadDoMeta ler(String json) throws Exception {
        JsonNode raiz = JSON.readTree(json);
        return LeadDoMeta.de(raiz);
    }
}
