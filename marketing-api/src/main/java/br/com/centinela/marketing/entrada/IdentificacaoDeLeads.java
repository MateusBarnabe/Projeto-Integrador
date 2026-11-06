package br.com.centinela.marketing.entrada;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import br.com.centinela.marketing.entrada.ChavesDeIdentidade.Chave;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadRepository;

/**
 * Resolução de identidade do lead (norteador §5): tenta as chaves do fato na ordem de
 * {@link Chave} e devolve o primeiro lead que achar. Com mais de um lead pelo mesmo e-mail, telefone ou
 * contato do CRM, vale o mais antigo. Roda no tenant do contexto (o do envelope do evento) e na
 * transação de quem chama; não cria nem altera nada.
 */
@Component
public class IdentificacaoDeLeads {

    private final LeadRepository leads;

    public IdentificacaoDeLeads(LeadRepository leads) {
        this.leads = leads;
    }

    /** Lead achado e a chave que o achou (para a timeline e para o log, sem dado pessoal). */
    public record LeadIdentificado(Lead lead, Chave por) {
        public LeadIdentificado {
            Objects.requireNonNull(lead, "lead");
            Objects.requireNonNull(por, "por");
        }
    }

    public Optional<LeadIdentificado> identificar(ChavesDeIdentidade chaves) {
        Objects.requireNonNull(chaves, "chaves");
        return tentar(Chave.META_LEAD, chaves.metaLeadId(), leads::findByMetaLeadId)
                .or(() -> tentar(Chave.ENVIO, chaves.envioId(), leads::findByEnvioId))
                .or(() -> tentar(Chave.SESSAO, chaves.sessaoId(), leads::findBySessaoId))
                .or(() -> tentar(Chave.CONTATO_CRM, chaves.crmContatoId(), leads::findFirstByCrmContatoIdOrderByCriadoEmAsc))
                .or(() -> tentar(Chave.EMAIL, chaves.email(), email -> primeiro(leads.buscarPorEmail(email))))
                .or(() -> tentar(Chave.TELEFONE, chaves.telefone(), telefone -> primeiro(leads.buscarPorTelefone(telefone))));
    }

    /**
     * Todos os leads com o mesmo e-mail ou telefone, do mais antigo para o mais novo, sem repetir.
     * É a base do merge do envio (E5, §5 passo 3.4): o primeiro fica, os outros se juntam a ele.
     */
    public List<Lead> mesmoContato(String email, String telefone) {
        Map<UUID, Lead> porId = new LinkedHashMap<>();
        String emailNormalizado = NormalizacaoDeContato.email(email);
        String telefoneNormalizado = NormalizacaoDeContato.telefone(telefone);
        if (emailNormalizado != null) {
            leads.buscarPorEmail(emailNormalizado).forEach(lead -> porId.putIfAbsent(lead.getId(), lead));
        }
        if (telefoneNormalizado != null) {
            leads.buscarPorTelefone(telefoneNormalizado).forEach(lead -> porId.putIfAbsent(lead.getId(), lead));
        }
        List<Lead> encontrados = new ArrayList<>(porId.values());
        encontrados.sort((a, b) -> {
            int porData = a.getCriadoEm().compareTo(b.getCriadoEm());
            return porData != 0 ? porData : a.getId().compareTo(b.getId());
        });
        return encontrados;
    }

    private static <T> Optional<LeadIdentificado> tentar(Chave chave, T valor, Function<T, Optional<Lead>> busca) {
        if (valor == null) {
            return Optional.empty();
        }
        return busca.apply(valor).map(lead -> new LeadIdentificado(lead, chave));
    }

    private static Optional<Lead> primeiro(List<Lead> encontrados) {
        return encontrados.isEmpty() ? Optional.empty() : Optional.of(encontrados.get(0));
    }
}
