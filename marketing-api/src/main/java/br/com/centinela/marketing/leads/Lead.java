package br.com.centinela.marketing.leads;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import br.com.centinela.marketing.compartilhado.dados.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Registro central do lead (norteador §3). Existe a partir do momento em que há um identificador
 * de contato: lead do Meta (etapa 0), captura do chatbot (etapa 1) ou envio do Landing (etapa 3).
 * Visitante sem contato não chega ao Marketing.
 */
@Entity
@Table(name = "leads")
@SQLDelete(sql = "UPDATE marketing.leads SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Lead extends EntidadeBase {

    public static final int ETAPA_MINIMA = 0;
    public static final int ETAPA_MAXIMA = 3;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    @Column(name = "etapa", nullable = false)
    private short etapa;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "temperatura", insertable = false, updatable = false)
    @Convert(converter = Temperatura.Conversor.class)
    private Temperatura temperatura;

    @Column(name = "origem", nullable = false, updatable = false)
    @Convert(converter = OrigemLead.Conversor.class)
    private OrigemLead origem;

    @Column(name = "nome")
    private String nome;

    @Column(name = "email")
    private String email;

    @Column(name = "telefone")
    private String telefone;

    @Column(name = "whatsapp")
    private String whatsapp;

    @Column(name = "utm_source")
    private String utmSource;

    @Column(name = "utm_medium")
    private String utmMedium;

    @Column(name = "utm_campaign")
    private String utmCampaign;

    @Column(name = "utm_term")
    private String utmTerm;

    @Column(name = "utm_content")
    private String utmContent;

    @Column(name = "landing_page_url")
    private String landingPageUrl;

    @Column(name = "first_touch")
    private OffsetDateTime firstTouch;

    @Column(name = "last_touch")
    private OffsetDateTime lastTouch;

    @Column(name = "canal")
    @Convert(converter = CanalLead.Conversor.class)
    private CanalLead canal;

    @Column(name = "sessao_id")
    private UUID sessaoId;

    @Column(name = "envio_id")
    private UUID envioId;

    /** Id do formulário no Landing; sem FK, só o UUID. */
    @Column(name = "formulario_id")
    private UUID formularioId;

    /** Respostas do envio como vieram do Landing: {@code campoId}, {@code rotulo}, {@code tipo}, {@code valor}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_formulario")
    private List<Map<String, Object>> dadosFormulario;

    @Column(name = "envio_lido_em")
    private OffsetDateTime envioLidoEm;

    @Column(name = "consentimento_marketing", nullable = false)
    private boolean consentimentoMarketing;

    @Column(name = "consentimento_em")
    private OffsetDateTime consentimentoEm;

    @Column(name = "boas_vindas_enviada_em")
    private OffsetDateTime boasVindasEnviadaEm;

    @Column(name = "meta_lead_id")
    private String metaLeadId;

    @Column(name = "crm_empresa_id")
    private UUID crmEmpresaId;

    @Column(name = "crm_contato_id")
    private UUID crmContatoId;

    @Column(name = "crm_oportunidade_id")
    private UUID crmOportunidadeId;

    @Column(name = "pontuacao")
    private Integer pontuacao;

    @Column(name = "faixa_qualificacao")
    @Convert(converter = FaixaQualificacao.Conversor.class)
    private FaixaQualificacao faixaQualificacao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pontuacao_detalhe")
    private List<Map<String, Object>> pontuacaoDetalhe;

    @Column(name = "qualificado_em")
    private OffsetDateTime qualificadoEm;

    @Column(name = "vendedor_id")
    private UUID vendedorId;

    @Column(name = "vendedor_atribuido_em")
    private OffsetDateTime vendedorAtribuidoEm;

    @Column(name = "ultima_comunicacao_em")
    private OffsetDateTime ultimaComunicacaoEm;

    @Column(name = "tentativas_aquecimento", nullable = false)
    private int tentativasAquecimento;

    @Column(name = "status_aquecimento", nullable = false)
    @Convert(converter = StatusAquecimento.Conversor.class)
    private StatusAquecimento statusAquecimento = StatusAquecimento.ATIVO;

    @Column(name = "esgotado_em")
    private OffsetDateTime esgotadoEm;

    @Column(name = "opt_out_em")
    private OffsetDateTime optOutEm;

    @Column(name = "token_descadastro", nullable = false, updatable = false)
    private String tokenDescadastro;

    @Column(name = "anonimizado", nullable = false)
    private boolean anonimizado;

    protected Lead() {
    }

    public Lead(OrigemLead origem, int etapa) {
        this.origem = Objects.requireNonNull(origem, "origem");
        this.etapa = validarEtapa(etapa);
        this.tokenDescadastro = gerarTokenDescadastro();
    }

    /**
     * Leva o lead para a etapa informada. A etapa nunca é rebaixada (§2): uma etapa menor ou igual
     * à atual é ignorada.
     *
     * @return {@code true} se a etapa mudou
     */
    public boolean avancarEtapa(int novaEtapa) {
        short nova = validarEtapa(novaEtapa);
        if (nova <= etapa) {
            return false;
        }
        etapa = nova;
        return true;
    }

    /** Atualiza os dados de contato sem apagar o que já existe com um valor vazio. */
    public void atualizarContato(String nome, String email, String telefone, String whatsapp) {
        this.nome = preencher(this.nome, nome);
        this.email = preencher(this.email, email);
        this.telefone = preencher(this.telefone, telefone);
        this.whatsapp = preencher(this.whatsapp, whatsapp);
    }

    /** Liga o lead à sessão do chatbot, que também identifica o envio da mesma conversa. */
    public void vincularSessao(UUID sessaoId) {
        this.sessaoId = preencher(this.sessaoId, sessaoId);
    }

    /**
     * Liga o lead a um envio do Landing ({@code landing.formulario.recebido}). Um envio novo
     * substitui o anterior e deixa a leitura das respostas pendente até {@link #registrarRespostas}.
     */
    public void vincularEnvio(UUID envioId, UUID formularioId, CanalLead canal) {
        Objects.requireNonNull(envioId, "envioId");
        if (!envioId.equals(this.envioId)) {
            this.envioId = envioId;
            this.envioLidoEm = null;
        }
        this.formularioId = preencher(this.formularioId, formularioId);
        this.canal = preencher(this.canal, canal);
    }

    /**
     * Guarda a cópia das respostas lidas em {@code GET /api/landing/envios/{id}}. Envio anonimizado
     * no Landing chega com os valores nulos e não apaga a cópia que já existe (norteador §9.5).
     */
    public void registrarRespostas(List<Map<String, Object>> respostas, boolean anonimizadoNoLanding,
            OffsetDateTime lidoEm) {
        if (envioId == null) {
            throw new IllegalStateException("Lead sem envio do Landing para registrar respostas.");
        }
        if (!anonimizadoNoLanding || dadosFormulario == null) {
            this.dadosFormulario = respostas == null ? List.of() : List.copyOf(respostas);
        }
        this.envioLidoEm = Objects.requireNonNull(lidoEm, "lidoEm");
    }

    /** O envio já chegou, mas as respostas ainda não foram lidas no Landing (job 4.6). */
    public boolean leituraDoEnvioPendente() {
        return envioId != null && envioLidoEm == null;
    }

    /** Empresa e contato criados ou reaproveitados no CRM pelo Landing. Só o UUID. */
    public void vincularCrm(UUID empresaId, UUID contatoId) {
        this.crmEmpresaId = preencher(this.crmEmpresaId, empresaId);
        this.crmContatoId = preencher(this.crmContatoId, contatoId);
    }

    /**
     * Oportunidade criada pelo Landing no CRM, recebida em {@code crm.oportunidade.criada}.
     *
     * @return {@code true} se o lead passou a apontar para ela agora
     */
    public boolean vincularOportunidade(UUID oportunidadeId) {
        Objects.requireNonNull(oportunidadeId, "oportunidadeId");
        if (oportunidadeId.equals(crmOportunidadeId)) {
            return false;
        }
        crmOportunidadeId = oportunidadeId;
        return true;
    }

    /** Guarda o {@code leadgen_id} do Meta, usado para descartar reentregas do webhook. */
    public void vincularMetaLead(String metaLeadId) {
        this.metaLeadId = preencher(this.metaLeadId, metaLeadId);
    }

    /**
     * Atualiza o estado atual do consentimento de marketing. A evidência de cada aceite e revogação
     * é gravada à parte, em {@link LeadConsentimento}.
     */
    public void registrarConsentimento(boolean aceito, OffsetDateTime registradoEm) {
        this.consentimentoMarketing = aceito;
        this.consentimentoEm = Objects.requireNonNull(registradoEm, "registradoEm");
    }

    /**
     * Grava o resultado do motor de pontuação (§9.3): a soma, a faixa, as regras que pontuaram e a
     * data do cálculo. Quem registra a mudança de faixa na timeline é o motor.
     */
    public void registrarPontuacao(int pontuacao, FaixaQualificacao faixa, List<Map<String, Object>> detalhe,
            OffsetDateTime calculadoEm) {
        if (pontuacao < 0) {
            throw new IllegalArgumentException("Pontuação não pode ser negativa: " + pontuacao);
        }
        this.pontuacao = pontuacao;
        this.faixaQualificacao = Objects.requireNonNull(faixa, "faixa");
        this.pontuacaoDetalhe = detalhe == null ? List.of() : List.copyOf(detalhe);
        this.qualificadoEm = Objects.requireNonNull(calculadoEm, "calculadoEm");
    }

    /**
     * Grava o vendedor do lead, escolhido pelo rodízio ou na reatribuição manual (§9.3). Só o UUID
     * do usuário do identity. Quem registra {@code vendedor_atribuido} na timeline é o rodízio.
     */
    public void atribuirVendedor(UUID vendedorId, OffsetDateTime atribuidoEm) {
        this.vendedorId = Objects.requireNonNull(vendedorId, "vendedorId");
        this.vendedorAtribuidoEm = Objects.requireNonNull(atribuidoEm, "atribuidoEm");
    }

    // --- Entrada pelo Landing (E1; reaproveitado por E5/E6) ---------------------------------------
    // Os valores chegam já normalizados (entrada.NormalizacaoDeContato). "atualizar" é para o fato
    // mais recente: valor preenchido substitui, vazio nunca apaga (§3). "completar" é para o fato que
    // chega atrasado (ex.: captura do chatbot depois do envio): só preenche o que falta (§2).

    /** Contato vindo de um fato atrasado: só preenche os campos vazios. */
    public void completarContato(String nome, String email, String telefone, String whatsapp) {
        this.nome = completar(this.nome, nome);
        this.email = completar(this.email, email);
        this.telefone = completar(this.telefone, telefone);
        this.whatsapp = completar(this.whatsapp, whatsapp);
    }

    /** UTM da última origem do visitante (§3). Valor vazio nunca sobrescreve um preenchido. */
    public void atualizarUtm(String source, String medium, String campaign, String term, String content) {
        this.utmSource = preencher(this.utmSource, source);
        this.utmMedium = preencher(this.utmMedium, medium);
        this.utmCampaign = preencher(this.utmCampaign, campaign);
        this.utmTerm = preencher(this.utmTerm, term);
        this.utmContent = preencher(this.utmContent, content);
    }

    /** UTM vinda de um fato atrasado: só preenche os campos vazios. */
    public void completarUtm(String source, String medium, String campaign, String term, String content) {
        this.utmSource = completar(this.utmSource, source);
        this.utmMedium = completar(this.utmMedium, medium);
        this.utmCampaign = completar(this.utmCampaign, campaign);
        this.utmTerm = completar(this.utmTerm, term);
        this.utmContent = completar(this.utmContent, content);
    }

    /**
     * Formulário do Landing e canal do contato sem envio (captura do chatbot). O envio usa
     * {@link #vincularEnvio}. Valor nulo não apaga o que existe.
     */
    public void vincularFormulario(UUID formularioId, CanalLead canal) {
        this.formularioId = preencher(this.formularioId, formularioId);
        this.canal = preencher(this.canal, canal);
    }

    private static String completar(String atual, String novo) {
        return temTexto(atual) ? atual : preencher(atual, novo);
    }

    // --- fim da entrada pelo Landing -------------------------------------------------------------

    /** Só é lead quem tem um identificador de contato: Meta, e-mail ou telefone (§3). */
    public boolean possuiIdentificadorDeContato() {
        return temTexto(metaLeadId) || temTexto(email) || temTexto(telefone);
    }

    private static short validarEtapa(int etapa) {
        if (etapa < ETAPA_MINIMA || etapa > ETAPA_MAXIMA) {
            throw new IllegalArgumentException("Etapa do funil deve estar entre 0 e 3: " + etapa);
        }
        return (short) etapa;
    }

    private static String preencher(String atual, String novo) {
        return temTexto(novo) ? novo.strip() : atual;
    }

    private static <T> T preencher(T atual, T novo) {
        return novo != null ? novo : atual;
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static String gerarTokenDescadastro() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public int getEtapa() {
        return etapa;
    }

    public Temperatura getTemperatura() {
        return temperatura;
    }

    public OrigemLead getOrigem() {
        return origem;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getWhatsapp() {
        return whatsapp;
    }

    public String getUtmSource() {
        return utmSource;
    }

    public String getUtmMedium() {
        return utmMedium;
    }

    public String getUtmCampaign() {
        return utmCampaign;
    }

    public String getUtmTerm() {
        return utmTerm;
    }

    public String getUtmContent() {
        return utmContent;
    }

    public String getLandingPageUrl() {
        return landingPageUrl;
    }

    public OffsetDateTime getFirstTouch() {
        return firstTouch;
    }

    public OffsetDateTime getLastTouch() {
        return lastTouch;
    }

    public CanalLead getCanal() {
        return canal;
    }

    public UUID getSessaoId() {
        return sessaoId;
    }

    public UUID getEnvioId() {
        return envioId;
    }

    public UUID getFormularioId() {
        return formularioId;
    }

    public List<Map<String, Object>> getDadosFormulario() {
        return dadosFormulario;
    }

    public OffsetDateTime getEnvioLidoEm() {
        return envioLidoEm;
    }

    public boolean isConsentimentoMarketing() {
        return consentimentoMarketing;
    }

    public OffsetDateTime getConsentimentoEm() {
        return consentimentoEm;
    }

    public OffsetDateTime getBoasVindasEnviadaEm() {
        return boasVindasEnviadaEm;
    }

    public String getMetaLeadId() {
        return metaLeadId;
    }

    public UUID getCrmEmpresaId() {
        return crmEmpresaId;
    }

    public UUID getCrmContatoId() {
        return crmContatoId;
    }

    public UUID getCrmOportunidadeId() {
        return crmOportunidadeId;
    }

    public Integer getPontuacao() {
        return pontuacao;
    }

    public FaixaQualificacao getFaixaQualificacao() {
        return faixaQualificacao;
    }

    public List<Map<String, Object>> getPontuacaoDetalhe() {
        return pontuacaoDetalhe;
    }

    public OffsetDateTime getQualificadoEm() {
        return qualificadoEm;
    }

    public UUID getVendedorId() {
        return vendedorId;
    }

    public OffsetDateTime getVendedorAtribuidoEm() {
        return vendedorAtribuidoEm;
    }

    public OffsetDateTime getUltimaComunicacaoEm() {
        return ultimaComunicacaoEm;
    }

    public int getTentativasAquecimento() {
        return tentativasAquecimento;
    }

    public StatusAquecimento getStatusAquecimento() {
        return statusAquecimento;
    }

    public OffsetDateTime getEsgotadoEm() {
        return esgotadoEm;
    }

    public OffsetDateTime getOptOutEm() {
        return optOutEm;
    }

    public String getTokenDescadastro() {
        return tokenDescadastro;
    }

    public boolean isAnonimizado() {
        return anonimizado;
    }

    // --- E5/E11: leitura do envio, leitura pendente (job 4.6) e junção de duplicados -------------
    // Colunas da V10. A leitura de GET /api/landing/envios/{id} que falha fica pendente; as falhas
    // seguidas são contadas e, no limite, a leitura é abandonada (§4.6). A junção (§5, passo 3.4)
    // leva para o lead que fica os dados do outro, que é marcado como excluído.

    private static final int TAMANHO_MAXIMO_URL = 2048;

    /** Falhas seguidas ao ler o envio no Landing; volta a zero na leitura e num envio novo. */
    @Column(name = "leitura_envio_falhas", nullable = false)
    private short leituraEnvioFalhas;

    /** Quando a leitura do envio foi abandonada: o detalhe mostra "respostas indisponíveis". */
    @Column(name = "leitura_envio_desistida_em")
    private OffsetDateTime leituraEnvioDesistidaEm;

    /**
     * {@link #vincularEnvio} do {@code landing.formulario.recebido}: um envio diferente do atual
     * recomeça a contagem de falhas da leitura.
     */
    public void vincularEnvioDoFormulario(UUID envioId, UUID formularioId, CanalLead canal) {
        boolean outroEnvio = !Objects.equals(envioId, this.envioId);
        vincularEnvio(envioId, formularioId, canal);
        if (outroEnvio) {
            zerarFalhasDeLeitura();
        }
    }

    /** {@link #registrarRespostas} da leitura bem-sucedida, que zera a contagem de falhas. */
    public void registrarLeituraDoEnvio(List<Map<String, Object>> respostas, boolean anonimizadoNoLanding,
            OffsetDateTime lidoEm) {
        registrarRespostas(respostas, anonimizadoNoLanding, lidoEm);
        zerarFalhasDeLeitura();
    }

    /**
     * Conta uma falha da leitura pendente do envio e, ao chegar a {@code limite}, abandona a leitura.
     *
     * @return {@code true} se a leitura foi abandonada agora
     */
    public boolean registrarFalhaNaLeituraDoEnvio(int limite, OffsetDateTime agora) {
        if (!leituraDoEnvioPendente() || leituraEnvioDesistidaEm != null) {
            return false;
        }
        leituraEnvioFalhas = (short) Math.min(leituraEnvioFalhas + 1, Short.MAX_VALUE);
        if (leituraEnvioFalhas < limite) {
            return false;
        }
        leituraEnvioDesistidaEm = Objects.requireNonNull(agora, "agora");
        return true;
    }

    /** A leitura do envio continua pendente, mas o job 4.6 desistiu dela. */
    public boolean leituraDoEnvioAbandonada() {
        return leituraDoEnvioPendente() && leituraEnvioDesistidaEm != null;
    }

    /**
     * Página de entrada e toques da origem do envio (§3): vazio não apaga; o primeiro toque só recua
     * e o último só avança.
     */
    public void registrarToques(String paginaEntrada, OffsetDateTime primeiro, OffsetDateTime ultimo) {
        this.landingPageUrl = limitar(preencher(this.landingPageUrl, paginaEntrada), TAMANHO_MAXIMO_URL);
        if (primeiro != null && (firstTouch == null || primeiro.isBefore(firstTouch))) {
            firstTouch = primeiro;
        }
        if (ultimo != null && (lastTouch == null || ultimo.isAfter(lastTouch))) {
            lastTouch = ultimo;
        }
    }

    /** Identificadores únicos por tenant que o lead juntado a outro deixa livres para o que fica. */
    public record IdentificadoresLiberados(UUID sessaoId, UUID envioId, String metaLeadId) {
    }

    /**
     * Primeira metade da junção: tira deste lead os identificadores únicos por tenant (sessão, envio,
     * Meta) e o marca como excluído. Grave-o antes de o outro lead receber os identificadores
     * ({@link #absorver}), porque a unicidade vale também para as linhas excluídas.
     */
    public IdentificadoresLiberados liberarParaJuncao() {
        if (getId() == null) {
            throw new IllegalStateException("Lead ainda não gravado.");
        }
        IdentificadoresLiberados liberados = new IdentificadoresLiberados(sessaoId, envioId, metaLeadId);
        sessaoId = null;
        envioId = null;
        metaLeadId = null;
        marcarExcluida();
        return liberados;
    }

    /**
     * Segunda metade da junção: este lead, o mais antigo, fica com o que o outro tinha e ele não. O
     * que este já tem prevalece; o envio do outro só vem se este não tiver envio, com as respostas
     * lidas. Opt-out do outro vale para este. Etapa, pontuação e consentimentos ficam com quem chama.
     */
    public void absorver(Lead juntado, IdentificadoresLiberados liberados) {
        Objects.requireNonNull(juntado, "juntado");
        Objects.requireNonNull(liberados, "liberados");
        sessaoId = sessaoId != null ? sessaoId : liberados.sessaoId();
        metaLeadId = completar(metaLeadId, liberados.metaLeadId());
        if (envioId == null && liberados.envioId() != null) {
            envioId = liberados.envioId();
            formularioId = juntado.formularioId;
            canal = juntado.canal;
            dadosFormulario = juntado.dadosFormulario;
            envioLidoEm = juntado.envioLidoEm;
            leituraEnvioFalhas = juntado.leituraEnvioFalhas;
            leituraEnvioDesistidaEm = juntado.leituraEnvioDesistidaEm;
        }
        formularioId = formularioId != null ? formularioId : juntado.formularioId;
        canal = canal != null ? canal : juntado.canal;
        completarContato(juntado.nome, juntado.email, juntado.telefone, juntado.whatsapp);
        completarUtm(juntado.utmSource, juntado.utmMedium, juntado.utmCampaign, juntado.utmTerm,
                juntado.utmContent);
        landingPageUrl = completar(landingPageUrl, juntado.landingPageUrl);
        registrarToques(null, juntado.firstTouch, juntado.lastTouch);
        crmEmpresaId = crmEmpresaId != null ? crmEmpresaId : juntado.crmEmpresaId;
        crmContatoId = crmContatoId != null ? crmContatoId : juntado.crmContatoId;
        crmOportunidadeId = crmOportunidadeId != null ? crmOportunidadeId : juntado.crmOportunidadeId;
        completarInteracao(juntado);
        if (vendedorId == null && juntado.vendedorId != null) {
            vendedorId = juntado.vendedorId;
            vendedorAtribuidoEm = juntado.vendedorAtribuidoEm;
        }
        boasVindasEnviadaEm = maisAntigo(boasVindasEnviadaEm, juntado.boasVindasEnviadaEm);
        ultimaComunicacaoEm = maisRecente(ultimaComunicacaoEm, juntado.ultimaComunicacaoEm);
        tentativasAquecimento = Math.max(tentativasAquecimento, juntado.tentativasAquecimento);
        if (juntado.statusAquecimento == StatusAquecimento.OPT_OUT && statusAquecimento != StatusAquecimento.OPT_OUT) {
            statusAquecimento = StatusAquecimento.OPT_OUT;
            optOutEm = juntado.optOutEm;
        }
    }

    private void zerarFalhasDeLeitura() {
        leituraEnvioFalhas = 0;
        leituraEnvioDesistidaEm = null;
    }

    private static String limitar(String valor, int tamanho) {
        return valor == null || valor.length() <= tamanho ? valor : valor.substring(0, tamanho);
    }

    private static OffsetDateTime maisAntigo(OffsetDateTime a, OffsetDateTime b) {
        return a == null ? b : b == null || !b.isBefore(a) ? a : b;
    }

    private static OffsetDateTime maisRecente(OffsetDateTime a, OffsetDateTime b) {
        return a == null ? b : b == null || !b.isAfter(a) ? a : b;
    }

    public int getLeituraEnvioFalhas() {
        return leituraEnvioFalhas;
    }

    public OffsetDateTime getLeituraEnvioDesistidaEm() {
        return leituraEnvioDesistidaEm;
    }

    // --- fim de E5/E11 ---------------------------------------------------------------------------

    // --- R1: visita do anúncio (V11) ---------------------------------------------------------------
    // Contexto técnico do aparelho e comportamento na landing page da visita que converteu. As colunas
    // não dependem da fonte: hoje é a visita de anúncio (rastreamento/); depois, um pixel ou o Landing.

    /** {@code tipoDispositivo}, {@code sistema}, {@code navegador}, {@code localizacao}, {@code idioma}, {@code fuso}, {@code tela}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contexto_tecnico")
    private Map<String, Object> contextoTecnico;

    /** {@code tempoNaPaginaSegundos}, {@code profundidadeRolagem}, {@code elementosClicados}, {@code paginasVisitadas}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "comportamento")
    private Map<String, Object> comportamento;

    /**
     * Guarda o contexto técnico e o comportamento da visita ligada ao lead. A visita mais recente é o
     * fato mais recente: substitui a anterior; vazio não apaga.
     */
    public void registrarInteracao(Map<String, Object> contextoTecnico, Map<String, Object> comportamento) {
        if (contextoTecnico != null && !contextoTecnico.isEmpty()) {
            this.contextoTecnico = new LinkedHashMap<>(contextoTecnico);
        }
        if (comportamento != null && !comportamento.isEmpty()) {
            this.comportamento = new LinkedHashMap<>(comportamento);
        }
    }

    /** Na junção, o lead que fica recebe a interação do outro se não tiver a sua. */
    void completarInteracao(Lead juntado) {
        this.contextoTecnico = this.contextoTecnico != null ? this.contextoTecnico : juntado.contextoTecnico;
        this.comportamento = this.comportamento != null ? this.comportamento : juntado.comportamento;
    }

    public Map<String, Object> getContextoTecnico() {
        return contextoTecnico;
    }

    public Map<String, Object> getComportamento() {
        return comportamento;
    }

    // --- fim de R1 -------------------------------------------------------------------------------
}
