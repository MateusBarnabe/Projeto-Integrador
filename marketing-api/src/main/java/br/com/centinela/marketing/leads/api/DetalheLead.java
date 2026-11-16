package br.com.centinela.marketing.leads.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadConsentimento;
import br.com.centinela.marketing.leads.LeadEvento;

/**
 * Detalhe do lead, {@code GET /api/marketing/leads/{id}} (norteador §9.2), organizado em seções para
 * a tela e para receber as que faltam sem quebrar o formato: e-mails enviados ({@code lead_comunicacoes},
 * D3), nome do formulário lido no Landing e resumo da empresa e do contato no CRM.
 *
 * <p>É a única saída da evidência de consentimento (IP, User-Agent, texto do termo). Lead anonimizado
 * sai sem contato, sem respostas e sem IP/User-Agent, mesmo que alguma coluna tenha escapado da
 * anonimização. O {@code token_descadastro} nunca sai.
 */
public record DetalheLead(UUID id, int etapa, String temperatura, boolean anonimizado, OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm, Contato contato, Origem origem, Respostas respostas, Qualificacao qualificacao,
        Vendedor vendedor, Consentimento consentimento, Aquecimento aquecimento, Crm crm,
        List<ItemTimeline> timeline, Interacao interacao) {

    /** Dados de contato; todos nulos em lead anonimizado. */
    public record Contato(String nome, String email, String telefone, String whatsapp) {
    }

    /** De onde o lead veio: origem, canal do envio, UTM da última origem e primeiro e último toque. */
    public record Origem(String origem, String canal, String utmSource, String utmMedium, String utmCampaign,
            String utmTerm, String utmContent, String landingPageUrl, OffsetDateTime primeiroToque,
            OffsetDateTime ultimoToque, UUID sessaoId, String metaLeadId) {
    }

    /**
     * Respostas do envio, como foram lidas do Landing ({@code dados_formulario}). {@code situacao}:
     * {@code sem_envio}, {@code aguardando_landing} (leitura pendente, job 4.6), {@code indisponiveis}
     * (o job 4.6 desistiu depois de 10 falhas) ou {@code lidas}.
     */
    public record Respostas(UUID formularioId, UUID envioId, String situacao, OffsetDateTime lidasEm,
            List<Map<String, Object>> itens) {

        static final String SEM_ENVIO = "sem_envio";
        static final String AGUARDANDO_LANDING = "aguardando_landing";
        static final String INDISPONIVEIS = "indisponiveis";
        static final String LIDAS = "lidas";
    }

    /** Pontuação com as regras que somaram pontos ({@code pontuacao_detalhe}, §9.3). */
    public record Qualificacao(Integer pontuacao, String faixaQualificacao, OffsetDateTime qualificadoEm,
            List<Map<String, Object>> detalhe) {
    }

    public record Vendedor(UUID vendedorId, OffsetDateTime atribuidoEm) {
    }

    /** Estado atual do consentimento de marketing e o histórico de aceites e revogações. */
    public record Consentimento(boolean marketing, OffsetDateTime aceitoEm, List<RegistroConsentimento> historico) {
    }

    /** Linha de {@code lead_consentimentos}: a evidência de um aceite ou revogação. */
    public record RegistroConsentimento(UUID id, String finalidade, boolean aceito, OffsetDateTime registradoEm,
            String origem, String textoTermo, UUID formularioId, UUID envioId, UUID sessaoId, String ip,
            String userAgent) {
    }

    /** Cadência de aquecimento e boas-vindas (4.2 e 4.5). Os e-mails enviados entram com a D3. */
    public record Aquecimento(String status, int tentativas, OffsetDateTime ultimaComunicacaoEm,
            OffsetDateTime boasVindasEnviadaEm, OffsetDateTime esgotadoEm, OffsetDateTime optOutEm) {
    }

    /** Ids no CRM: empresa e contato do envio e a oportunidade de {@code crm.oportunidade.criada}. */
    public record Crm(UUID empresaId, UUID contatoId, UUID oportunidadeId) {
    }

    /**
     * Visita do anúncio que converteu (R1): contexto técnico do aparelho e comportamento na landing
     * page. Nulos sem visita ligada e no lead anonimizado (a E10 zera as duas colunas).
     */
    public record Interacao(Map<String, Object> contextoTecnico, Map<String, Object> comportamento) {
    }

    /** Item da timeline ({@code lead_eventos}), em ordem de {@code sequencia}. */
    public record ItemTimeline(long sequencia, String tipo, Integer etapaAnterior, Integer etapaNova,
            Map<String, Object> payload, OffsetDateTime ocorridoEm) {
    }

    static DetalheLead de(Lead lead, List<LeadConsentimento> consentimentos, List<LeadEvento> eventos) {
        boolean anonimizado = lead.isAnonimizado();
        Contato contato = anonimizado
                ? new Contato(null, null, null, null)
                : new Contato(lead.getNome(), lead.getEmail(), lead.getTelefone(), lead.getWhatsapp());
        Origem origem = new Origem(Valores.de(lead.getOrigem()), Valores.de(lead.getCanal()), lead.getUtmSource(),
                lead.getUtmMedium(), lead.getUtmCampaign(), lead.getUtmTerm(), lead.getUtmContent(),
                lead.getLandingPageUrl(), lead.getFirstTouch(), lead.getLastTouch(), lead.getSessaoId(),
                lead.getMetaLeadId());
        Qualificacao qualificacao = new Qualificacao(lead.getPontuacao(), Valores.de(lead.getFaixaQualificacao()),
                lead.getQualificadoEm(), lead.getPontuacaoDetalhe() == null ? List.of() : lead.getPontuacaoDetalhe());
        Consentimento consentimento = new Consentimento(lead.isConsentimentoMarketing(), lead.getConsentimentoEm(),
                consentimentos.stream().map(registro -> registro(registro, anonimizado)).toList());
        Aquecimento aquecimento = new Aquecimento(Valores.de(lead.getStatusAquecimento()),
                lead.getTentativasAquecimento(), lead.getUltimaComunicacaoEm(), lead.getBoasVindasEnviadaEm(),
                lead.getEsgotadoEm(), lead.getOptOutEm());
        return new DetalheLead(lead.getId(), lead.getEtapa(), Valores.de(lead.getTemperatura()), anonimizado,
                lead.getCriadoEm(), lead.getAtualizadoEm(), contato, origem, respostas(lead, anonimizado),
                qualificacao, new Vendedor(lead.getVendedorId(), lead.getVendedorAtribuidoEm()), consentimento,
                aquecimento, new Crm(lead.getCrmEmpresaId(), lead.getCrmContatoId(), lead.getCrmOportunidadeId()),
                eventos.stream().map(DetalheLead::item).toList(),
                anonimizado ? new Interacao(null, null)
                        : new Interacao(lead.getContextoTecnico(), lead.getComportamento()));
    }

    private static Respostas respostas(Lead lead, boolean anonimizado) {
        String situacao = lead.getEnvioId() == null ? Respostas.SEM_ENVIO
                : lead.leituraDoEnvioAbandonada() ? Respostas.INDISPONIVEIS
                : lead.leituraDoEnvioPendente() ? Respostas.AGUARDANDO_LANDING : Respostas.LIDAS;
        List<Map<String, Object>> itens = anonimizado || lead.getDadosFormulario() == null
                ? List.of() : lead.getDadosFormulario();
        return new Respostas(lead.getFormularioId(), lead.getEnvioId(), situacao, lead.getEnvioLidoEm(), itens);
    }

    private static RegistroConsentimento registro(LeadConsentimento registro, boolean anonimizado) {
        return new RegistroConsentimento(registro.getId(), registro.getFinalidade(), registro.isAceito(),
                registro.getRegistradoEm(), Valores.de(registro.getOrigem()), registro.getTextoTermo(),
                registro.getFormularioId(), registro.getEnvioId(), registro.getSessaoId(),
                anonimizado ? null : registro.getIp(), anonimizado ? null : registro.getUserAgent());
    }

    private static ItemTimeline item(LeadEvento evento) {
        return new ItemTimeline(evento.getSequencia(), Valores.de(evento.getTipoEvento()), evento.getEtapaAnterior(),
                evento.getEtapaNova(), evento.getPayload(), evento.getCriadoEm());
    }
}
