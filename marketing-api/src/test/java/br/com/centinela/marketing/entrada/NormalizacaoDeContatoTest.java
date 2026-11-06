package br.com.centinela.marketing.entrada;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NormalizacaoDeContatoTest {

    @Test
    void emailFicaSemEspacosEEmMinusculas() {
        assertThat(NormalizacaoDeContato.email("  Ana.Souza@Empresa.COM.br ")).isEqualTo("ana.souza@empresa.com.br");
        assertThat(NormalizacaoDeContato.email("ana @ empresa.com")).isEqualTo("ana@empresa.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "sem-arroba", "@empresa.com", "ana@", "ana@@empresa.com", "a@b@c" })
    void emailSemFormaDeEmailViraNulo(String bruto) {
        assertThat(NormalizacaoDeContato.email(bruto)).isNull();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            // Formato do Landing: só dígitos, com DDD
            "62999998888, 5562999998888",
            "6233334444, 556233334444",
            // Com máscara
            "'(62) 99999-8888', 5562999998888",
            "'62 3333-4444', 556233334444",
            // Já com o DDI 55
            "5562999998888, 5562999998888",
            "'+55 (62) 99999-8888', 5562999998888",
            "'55 62 3333-4444', 556233334444",
            // Prefixo de discagem
            "'0 62 99999-8888', 5562999998888",
            "'0055 62 99999-8888', 5562999998888",
            // DDD 55 (RS) sem DDI não se confunde com o DDI
            "55999998888, 5555999998888",
            // Outro país, com +
            "'+1 415 555 2671', 14155552671",
            "'+351 912 345 678', 351912345678",
            // Sem DDD: não dá para completar, fica só com os dígitos
            "99999-8888, 999998888" })
    void telefoneViraE164SemMais(String bruto, String esperado) {
        assertThat(NormalizacaoDeContato.telefone(bruto)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "abc", "1234567", "+1234567890123456", "0000000000" })
    void telefoneSemDigitosSuficientesViraNulo(String bruto) {
        assertThat(NormalizacaoDeContato.telefone(bruto)).isNull();
    }

    @Test
    void normalizarDuasVezesDaOMesmoResultado() {
        String telefone = NormalizacaoDeContato.telefone("(62) 99999-8888");
        String email = NormalizacaoDeContato.email(" Ana@Empresa.com ");

        assertThat(NormalizacaoDeContato.telefone(telefone)).isEqualTo(telefone);
        assertThat(NormalizacaoDeContato.email(email)).isEqualTo(email);
    }

    @Test
    void nomeSoPerdeOsEspacosDasPontas() {
        assertThat(NormalizacaoDeContato.nome("  Ana Souza ")).isEqualTo("Ana Souza");
        assertThat(NormalizacaoDeContato.nome(" ")).isNull();
        assertThat(NormalizacaoDeContato.nome(null)).isNull();
    }

    @Test
    void chavesNormalizamOContatoENaoMostramNoToString() {
        UUID sessao = UUID.randomUUID();
        ChavesDeIdentidade chaves = ChavesDeIdentidade.daSessao(sessao).comContato(" Ana@Empresa.com ", "62999998888");

        assertThat(chaves.sessaoId()).isEqualTo(sessao);
        assertThat(chaves.email()).isEqualTo("ana@empresa.com");
        assertThat(chaves.telefone()).isEqualTo("5562999998888");
        assertThat(chaves.temContato()).isTrue();
        assertThat(chaves.toString()).doesNotContain("ana@").doesNotContain("99999").contains(sessao.toString());
        assertThat(ChavesDeIdentidade.daSessao(sessao).comContato("invalido", "123").temContato()).isFalse();
    }
}
