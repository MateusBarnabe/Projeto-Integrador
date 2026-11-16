package br.com.centinela.marketing.entrada;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.centinela.marketing.compartilhado.http.ClientesDeModulos;
import br.com.centinela.marketing.compartilhado.http.FalhaNaChamadaDeModulo;
import br.com.centinela.marketing.compartilhado.http.FalhaNaChamadaDeModulo.Motivo;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.CanalLead;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadConsentimento;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.OrigemConsentimento;
import br.com.centinela.marketing.leads.TipoEventoLead;
import br.com.centinela.marketing.qualificacao.PontuacaoDeLeads;
import br.com.centinela.marketing.qualificacao.ResultadoPontuacao;
import br.com.centinela.marketing.rastreamento.VinculoDeVisitas;
import br.com.centinela.marketing.rodizio.ResultadoAtribuicao;
import br.com.centinela.marketing.rodizio.RodizioDeVendedores;

/**
 * Leitura do envio no Landing e o que vem dela (norteador §5, passos 3.3 a 3.6, e job 4.6). Usada na
 * chegada de {@code landing.formulario.recebido} ({@link EnvioDoFormulario}, E5) e pelo job da leitura
 * pendente (E11), que aplicam o mesmo fluxo:
 *
 * <ol>
 * <li>{@link #ler}: {@code GET /api/landing/envios/{id}} com token de serviço e {@code X-Tenant-Id}, 3 s.
 * Qualquer falha ({@link FalhaNaChamadaDeModulo}) volta como {@link Leitura} sem envio, nunca como
 * exceção: o lead fica com a leitura pendente;</li>
 * <li>{@link #aplicar}: grava o contato, junta os duplicados de mesmo e-mail ou telefone (o mais antigo
 * fica, {@link JuncaoDeLeads}), grava respostas ({@code dados_formulario}), origem (UTM da última,
 * página e toques), ids do CRM e o consentimento (texto e data, origem
 * {@code landing.formulario.recebido}), marca {@code envio_lido_em}, registra {@code respostas_lidas}
 * na timeline, sobe para a etapa 3, pontua e, se for MQL sem vendedor, atribui pelo rodízio (que já
 * notifica o vendedor);</li>
 * <li>{@link #registrarFalha}: conta a falha seguida e, na {@value #MAXIMO_DE_FALHAS}ª, abandona a
 * leitura ({@code respostas_indisponiveis} na timeline) e pontua com o que o lead tem.</li>
 * </ol>
 *
 * Roda na transação e no tenant de quem chama, com o lead já travado. Nada de contato, respostas ou
 * texto do termo vai para log ou timeline.
 */
@Service
public class LeituraDoEnvio {

    /** Falhas seguidas do mesmo lead antes de desistir da leitura (§4.6). */
    public static final int MAXIMO_DE_FALHAS = 10;

    static final String CAMINHO_ENVIO = "/api/landing/envios/{id}";

    private static final Logger LOG = LoggerFactory.getLogger(LeituraDoEnvio.class);

    private static final int TAMANHO_NOME = 200;
    private static final int TAMANHO_UTM = 255;

    private final ClientesDeModulos clientes;
    private final JuncaoDeLeads juncao;
    private final GravacaoDeLeads gravacao;
    private final RegistroDeConsentimentos consentimentos;
    private final PontuacaoDeLeads pontuacao;
    private final RodizioDeVendedores rodizio;
    private final LeadEventoRepository eventos;
    private final VinculoDeVisitas vinculoDeVisitas;

    LeituraDoEnvio(ClientesDeModulos clientes, JuncaoDeLeads juncao, GravacaoDeLeads gravacao,
            RegistroDeConsentimentos consentimentos, PontuacaoDeLeads pontuacao, RodizioDeVendedores rodizio,
            LeadEventoRepository eventos, VinculoDeVisitas vinculoDeVisitas) {
        this.clientes = clientes;
        this.juncao = juncao;
        this.gravacao = gravacao;
        this.consentimentos = consentimentos;
        this.pontuacao = pontuacao;
        this.rodizio = rodizio;
        this.eventos = eventos;
        this.vinculoDeVisitas = vinculoDeVisitas;
    }

    /**
     * Resultado da leitura: o envio, ou o motivo da falha.
     *
     * @param envio  o envio lido; nulo na falha
     * @param motivo por que a leitura falhou; nulo quando leu
     */
    public record Leitura(EnvioDoLanding envio, Motivo motivo) {

        public boolean lido() {
            return envio != null;
        }
    }

    /**
     * Lê o envio no Landing, no tenant do contexto. Não toca no banco: chame antes de travar o lead,
     * para não segurar a linha durante a chamada.
     */
    public Leitura ler(UUID envioId) {
        try {
            EnvioDoLanding envio = clientes.landing().get(TenantContexto.exigir(), CAMINHO_ENVIO,
                    EnvioDoLanding.class, envioId);
            if (envio == null || (envio.id() != null && !envio.id().equals(envioId))) {
                LOG.warn("Envio {} lido no Landing sem o envio pedido", envioId);
                return new Leitura(null, Motivo.RESPOSTA_INVALIDA);
            }
            return new Leitura(envio, null);
        } catch (FalhaNaChamadaDeModulo falha) {
            LOG.warn("Leitura do envio {} no Landing falhou: {}; fica pendente", envioId, falha.motivo());
            return new Leitura(null, falha.motivo());
        }
    }

    /**
     * Aplica o envio lido ao lead (travado, com a leitura pendente do mesmo envio).
     *
     * @return o lead que fica: o próprio ou o mais antigo de mesmo contato, ao qual ele foi unido
     */
    @Transactional
    public Lead aplicar(Lead lead, EnvioDoLanding envio, Procedencia procedencia) {
        UUID envioId = lead.getEnvioId();
        if (envioId == null) {
            throw new IllegalStateException("Lead sem envio do Landing para ler.");
        }
        UUID formularioId = envio.formularioId() != null ? envio.formularioId() : lead.getFormularioId();
        CanalLead canal = canal(envio.canal(), lead.getCanal());
        String nome = limitar(NormalizacaoDeContato.nome(envio.nome()), TAMANHO_NOME);
        String email = NormalizacaoDeContato.email(envio.emailDoContato());
        String telefone = NormalizacaoDeContato.telefone(envio.telefoneDoContato());

        // O envio é o fato mais recente do lead: contato preenchido substitui, vazio não apaga (§3)
        lead.atualizarContato(nome, email, telefone, null);
        Lead fica = juncao.juntarDuplicados(lead, procedencia);
        if (fica != lead) {
            fica.atualizarContato(nome, email, telefone, null);
        }

        fica.vincularEnvioDoFormulario(envioId, formularioId, canal);
        fica.vincularCrm(envio.empresaId(), envio.contatoId());
        gravacao.avancarEtapa(fica, EnvioDoFormulario.ETAPA, procedencia);
        EnvioDoLanding.Toque ultimo = envio.ultimoToque();
        fica.atualizarUtm(limitar(ultimo.utmSource(), TAMANHO_UTM), limitar(ultimo.utmMedium(), TAMANHO_UTM),
                limitar(ultimo.utmCampaign(), TAMANHO_UTM), limitar(ultimo.utmTerm(), TAMANHO_UTM),
                limitar(ultimo.utmContent(), TAMANHO_UTM));
        fica.registrarToques(ultimo.paginaEntrada(), envio.primeiroToque().ocorridoEm(), ultimo.ocorridoEm());
        fica.registrarLeituraDoEnvio(envio.respostasParaCopia(), envio.anonimizado(),
                OffsetDateTime.now(ZoneOffset.UTC));
        Lead gravado = gravacao.salvar(fica);
        eventos.save(new LeadEvento(gravado.getId(), TipoEventoLead.RESPOSTAS_LIDAS,
                respostasLidas(procedencia, envio, gravado)));

        boolean consentimentoNovo = registrarConsentimento(gravado, envio, procedencia);
        if (!gravado.possuiIdentificadorDeContato()) {
            LOG.warn("Envio {} lido sem e-mail nem telefone utilizável; lead {} segue sem contato", envioId,
                    gravado.getId());
        }
        // R1: o mkt_visita na página de entrada liga a visita do anúncio, que as regras de comportamento pontuam
        vinculoDeVisitas.vincular(gravado, paginasDeEntrada(envio), procedencia.fonte(), procedencia.eventoId());

        ResultadoPontuacao resultado = pontuacao.pontuar(gravado);
        ResultadoAtribuicao atribuicao = rodizio.atribuir(gravado);
        LOG.info("Envio {} lido no lead {} (respostas: {}, consentimento novo: {}, faixa {}, rodízio {})", envioId,
                gravado.getId(), envio.quantidadeDeRespostas(), consentimentoNovo, resultado.faixa().valor(),
                atribuicao.situacao());
        return gravado;
    }

    /**
     * Conta uma falha seguida da leitura do envio do lead (travado). Na {@value #MAXIMO_DE_FALHAS}ª,
     * desiste: registra {@code respostas_indisponiveis} e pontua e atribui com o que o lead já tem.
     *
     * @return {@code true} se a leitura foi abandonada agora
     */
    @Transactional
    public boolean registrarFalha(Lead lead, Motivo motivo, Procedencia procedencia) {
        boolean desistiu = lead.registrarFalhaNaLeituraDoEnvio(MAXIMO_DE_FALHAS, OffsetDateTime.now(ZoneOffset.UTC));
        Lead gravado = gravacao.salvar(lead);
        if (!desistiu) {
            return false;
        }
        Map<String, Object> payload = procedencia.payload();
        payload.put("envioId", gravado.getEnvioId().toString());
        payload.put("falhas", gravado.getLeituraEnvioFalhas());
        payload.put("motivo", motivo == null ? null : motivo.name());
        eventos.save(new LeadEvento(gravado.getId(), TipoEventoLead.RESPOSTAS_INDISPONIVEIS, payload));
        ResultadoPontuacao resultado = pontuacao.pontuar(gravado);
        rodizio.atribuir(gravado);
        LOG.warn("Leitura do envio {} do lead {} abandonada depois de {} falhas seguidas (faixa {})",
                gravado.getEnvioId(), gravado.getId(), gravado.getLeituraEnvioFalhas(), resultado.faixa().valor());
        return true;
    }

    private boolean registrarConsentimento(Lead lead, EnvioDoLanding envio, Procedencia procedencia) {
        EnvioDoLanding.Consentimento aceite = envio.consentimento();
        if (aceite == null || !Boolean.TRUE.equals(aceite.aceito())) {
            return false;
        }
        OffsetDateTime aceitoEm = aceite.aceitoEm() != null ? aceite.aceitoEm() : envio.recebidoEm();
        if (aceitoEm == null) {
            return false;
        }
        return consentimentos.registrar(lead, LeadConsentimento.doLanding(lead.getId(),
                OrigemConsentimento.FORMULARIO_RECEBIDO, true, aceitoEm, aceite.texto(), lead.getFormularioId(),
                lead.getEnvioId(), null, procedencia.eventoId()));
    }

    /** Páginas de entrada da última e da primeira origem do envio, nessa ordem. */
    private static List<String> paginasDeEntrada(EnvioDoLanding envio) {
        List<String> paginas = new ArrayList<>();
        paginas.add(envio.ultimoToque().paginaEntrada());
        paginas.add(envio.primeiroToque().paginaEntrada());
        return paginas;
    }

    // Sem as respostas: só quantas vieram e de onde
    private static Map<String, Object> respostasLidas(Procedencia procedencia, EnvioDoLanding envio, Lead lead) {
        Map<String, Object> payload = procedencia.payload();
        payload.put("envioId", lead.getEnvioId().toString());
        payload.put("formularioId", lead.getFormularioId() == null ? null : lead.getFormularioId().toString());
        payload.put("canal", lead.getCanal() == null ? null : lead.getCanal().valor());
        payload.put("respostas", envio.quantidadeDeRespostas());
        payload.put("anonimizadoNoLanding", envio.anonimizado());
        return payload;
    }

    /** {@code canal} do Landing; um valor que o Marketing não conhece não apaga o que o lead tem. */
    static CanalLead canal(String valor, CanalLead atual) {
        if (valor == null) {
            return atual;
        }
        for (CanalLead canal : CanalLead.values()) {
            if (canal.valor().equals(valor.strip())) {
                return canal;
            }
        }
        LOG.warn("Canal de envio desconhecido no Landing; mantido o do lead");
        return atual;
    }

    private static String limitar(String valor, int tamanho) {
        return valor == null || valor.length() <= tamanho ? valor : valor.substring(0, tamanho);
    }
}
