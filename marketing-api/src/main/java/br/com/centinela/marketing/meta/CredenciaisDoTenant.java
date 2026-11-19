package br.com.centinela.marketing.meta;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.configuracoes.CanalAnuncio;
import br.com.centinela.marketing.configuracoes.CanalAnuncioRepository;
import br.com.centinela.marketing.configuracoes.CanalAnuncioTipo;
import br.com.centinela.marketing.configuracoes.CifradorDeSegredo;

/**
 * Lê do canal {@code meta_ads} do tenant (D6) os segredos do Meta e os decifra. Só lê: quem grava é a
 * tela de Configurações. Sem a chave de cifra ({@code MARKETING_CHAVE_CIFRA}) ou com um segredo que não
 * decifra, o tenant fica sem credenciais, e quem chama trata como "não configurado" sem saber o motivo.
 */
@Component
class CredenciaisDoTenant {

    private static final Logger LOG = LoggerFactory.getLogger(CredenciaisDoTenant.class);

    private final CanalAnuncioRepository canais;
    private final CifradorDeSegredo cifrador;

    CredenciaisDoTenant(CanalAnuncioRepository canais, CifradorDeSegredo cifrador) {
        this.canais = canais;
        this.cifrador = cifrador;
    }

    /** Credenciais do tenant, no contexto dele; {@link CredenciaisDoMeta#NENHUMA} se não há o que usar. */
    CredenciaisDoMeta doTenant(UUID tenant) {
        return TenantContexto.calcular(tenant, this::doTenantAtual);
    }

    private CredenciaisDoMeta doTenantAtual() {
        CanalAnuncio canal = canais.findByCanal(CanalAnuncioTipo.META_ADS).orElse(null);
        if (canal == null) {
            return CredenciaisDoMeta.NENHUMA;
        }
        try {
            return new CredenciaisDoMeta(canal.decifrarMetaAppSecret(cifrador),
                    canal.decifrarMetaVerifyToken(cifrador), canal.decifrarMetaPageAccessToken(cifrador));
        } catch (CifradorDeSegredo.ChaveDeCifraAusente | IllegalStateException indisponivel) {
            // Só o tipo: a mensagem não traz segredo, mas também não ajuda quem está de fora
            LOG.warn("Segredos do Meta do tenant {} não puderam ser decifrados: {}", TenantContexto.exigir(),
                    indisponivel.getClass().getSimpleName());
            return CredenciaisDoMeta.NENHUMA;
        }
    }
}
