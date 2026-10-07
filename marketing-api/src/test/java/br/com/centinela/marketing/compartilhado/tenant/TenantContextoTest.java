package br.com.centinela.marketing.compartilhado.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextoTest {

    private static final UUID EXTERNO = UUID.randomUUID();
    private static final UUID INTERNO = UUID.randomUUID();

    @AfterEach
    void limpar() {
        TenantContexto.limpar();
    }

    @Test
    void calcularRestauraTenantAnterior() {
        TenantContexto.definir(EXTERNO);

        UUID resultado = TenantContexto.calcular(INTERNO, TenantContexto::exigir);

        assertThat(resultado).isEqualTo(INTERNO);
        assertThat(TenantContexto.exigir()).isEqualTo(EXTERNO);
    }

    @Test
    void calcularLimpaQuandoNaoHaviaTenantAnterior() {
        TenantContexto.calcular(INTERNO, TenantContexto::exigir);

        assertThat(TenantContexto.atual()).isEmpty();
    }
}