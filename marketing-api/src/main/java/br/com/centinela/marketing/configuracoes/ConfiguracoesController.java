package br.com.centinela.marketing.configuracoes;

import java.net.URI;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.centinela.marketing.compartilhado.web.ErroCampo;
import br.com.centinela.marketing.compartilhado.web.Pagina;
import br.com.centinela.marketing.compartilhado.web.Paginacao;
import br.com.centinela.marketing.compartilhado.web.Resposta;
import br.com.centinela.marketing.comunicacao.CicloVidaConfiguracao;
import br.com.centinela.marketing.comunicacao.CicloVidaConfiguracaoRepository;
import br.com.centinela.marketing.comunicacao.ModeloMensagem;
import br.com.centinela.marketing.comunicacao.ModeloMensagemRepository;
import br.com.centinela.marketing.comunicacao.TipoModeloMensagem;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.ConfiguracaoCicloVida;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.ConfiguracaoQualificacao;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.EntradaVendedorRodizio;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.FormularioQualificacao;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.ItemCanalAnuncio;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.ItemModeloMensagem;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.ItemRegraPontuacao;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.ItemVendedorRodizio;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.PedidoCanalAnuncio;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.PedidoCicloVida;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.PedidoEscalaVendedores;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.PedidoModeloMensagem;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.PedidoQualificacao;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.PedidoRegraPontuacao;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.PreviaFaixas;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.qualificacao.FonteRegra;
import br.com.centinela.marketing.qualificacao.MetricaComportamento;
import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracao;
import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracaoRepository;
import br.com.centinela.marketing.qualificacao.RegraPontuacao;
import br.com.centinela.marketing.qualificacao.RegraPontuacaoRepository;
import br.com.centinela.marketing.qualificacao.VendedorRodizio;
import br.com.centinela.marketing.qualificacao.VendedorRodizioRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

/**
 * APIs de configuração do Marketing (A3, norteador §6 e §9.4): canais de anúncio, modelos de
 * mensagem, ciclo de vida, qualificação, formulários do Landing, regras de pontuação e escala do
 * rodízio.
 */
@RestController
public class ConfiguracoesController {

    private final ValoresIniciaisDoTenant valoresIniciais;
    private final CanalAnuncioRepository canais;
    private final CifradorDeSegredo cifrador;
    private final ModeloMensagemRepository modelos;
    private final CicloVidaConfiguracaoRepository ciclos;
    private final QualificacaoConfiguracaoRepository qualificacoes;
    private final RegraPontuacaoRepository regras;
    private final VendedorRodizioRepository vendedores;
    private final LeadRepository leads;
    private final FormulariosDaQualificacao formularios;
    private final String urlPublica;

    public ConfiguracoesController(
            ValoresIniciaisDoTenant valoresIniciais,
            CanalAnuncioRepository canais,
            CifradorDeSegredo cifrador,
            ModeloMensagemRepository modelos,
            CicloVidaConfiguracaoRepository ciclos,
            QualificacaoConfiguracaoRepository qualificacoes,
            RegraPontuacaoRepository regras,
            VendedorRodizioRepository vendedores,
            LeadRepository leads,
            FormulariosDaQualificacao formularios,
            @Value("${marketing.rastreamento.url-publica:http://localhost:8087}") String urlPublica) {
        this.valoresIniciais = valoresIniciais;
        this.canais = canais;
        this.cifrador = cifrador;
        this.modelos = modelos;
        this.ciclos = ciclos;
        this.qualificacoes = qualificacoes;
        this.regras = regras;
        this.vendedores = vendedores;
        this.leads = leads;
        this.formularios = formularios;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    // --- Canais de anúncio (/api/marketing/canais-anuncio) ---------------------------------------

    @GetMapping("/api/marketing/canais-anuncio")
    @PreAuthorize("hasAuthority('marketing.configuracao.ver')")
    @Operation(summary = "Preferências dos canais de anúncio (google_ads e meta_ads), sem expor os segredos")
    @Transactional
    public Resposta<List<ItemCanalAnuncio>> listarCanaisAnuncio() {
        valoresIniciais.garantirCanaisAnuncio();
        return Resposta.ok(canais.findAllByOrderByCanalAsc().stream()
                .map(c -> ItemCanalAnuncio.de(c, urlPublica))
                .toList());
    }

    @PutMapping("/api/marketing/canais-anuncio")
    @PreAuthorize("hasAuthority('marketing.configuracao.editar')")
    @Operation(summary = "Atualiza as preferências de um canal de anúncio e, no meta_ads, cifra os segredos informados")
    @Transactional
    public ResponseEntity<Resposta<ItemCanalAnuncio>> atualizarCanalAnuncio(
            @Valid @RequestBody PedidoCanalAnuncio pedido) {
        CanalAnuncioTipo tipo = interpretarCanal(pedido.canal());
        if (pedido.orcamentoMensal() != null && pedido.orcamentoMensal().scale() > 2) {
            return ResponseEntity.badRequest().body(Resposta.erro("Dados inválidos.",
                    List.of(new ErroCampo("orcamentoMensal", "PRECISAO_EXCEDIDA",
                            "O orçamento mensal aceita no máximo 2 casas decimais."))));
        }
        valoresIniciais.garantirCanaisAnuncio();
        CanalAnuncio canal = canais.findByCanal(tipo).orElseGet(() -> new CanalAnuncio(tipo));
        canal.atualizarPreferencias(
                pedido.orcamentoMensal(),
                pedido.publicoAlvo(),
                pedido.objetivo(),
                pedido.ativo() != null ? pedido.ativo() : canal.isAtivo());
        if (tipo == CanalAnuncioTipo.META_ADS) {
            canal.atualizarCredenciaisMeta(
                    pedido.metaPageId(),
                    pedido.metaAppSecret(),
                    pedido.metaVerifyToken(),
                    pedido.metaPageAccessToken(),
                    cifrador);
        }
        return ResponseEntity.ok(Resposta.ok(ItemCanalAnuncio.de(canais.save(canal), urlPublica)));
    }

    // --- Modelos de mensagem (/api/marketing/modelos-mensagem) -----------------------------------

    @GetMapping("/api/marketing/modelos-mensagem")
    @PreAuthorize("hasAuthority('marketing.template.ver')")
    @Operation(summary = "Lista paginada de modelos de mensagem (boas-vindas e cadência de aquecimento)")
    @Transactional
    public Resposta<Pagina<ItemModeloMensagem>> listarModelosMensagem(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho) {
        valoresIniciais.garantirBoasVindas();
        Sort ordenacao = Sort.by(Sort.Order.desc("tipo"), Sort.Order.asc("ordemSequencia"), Sort.Order.asc("criadoEm"));
        PageRequest pageable = Paginacao.pedido(pagina, tamanho, null, Map.of("criadoEm", "criadoEm"))
                .withSort(ordenacao);
        Page<ModeloMensagem> resultado = tipo == null || tipo.isBlank()
                ? modelos.findAll(pageable)
                : modelos.findByTipo(interpretarTipoModelo(tipo), pageable);
        return Resposta.ok(Pagina.de(resultado, ItemModeloMensagem::de));
    }

    @PostMapping("/api/marketing/modelos-mensagem")
    @PreAuthorize("hasAuthority('marketing.template.criar')")
    @Operation(summary = "Cria um modelo de mensagem na cadência de aquecimento")
    @Transactional
    public ResponseEntity<Resposta<ItemModeloMensagem>> criarModeloMensagem(
            @Valid @RequestBody PedidoModeloMensagem pedido) {
        TipoModeloMensagem tipo = pedido.tipo() == null || pedido.tipo().isBlank()
                ? TipoModeloMensagem.AQUECIMENTO
                : interpretarTipoModelo(pedido.tipo());
        if (tipo == TipoModeloMensagem.BOAS_VINDAS) {
            if (modelos.findFirstByTipoAndAtivoTrue(TipoModeloMensagem.BOAS_VINDAS).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Resposta.erro(
                        "Já existe um modelo de boas-vindas para este tenant; edite o modelo existente.",
                        List.of(new ErroCampo("tipo", "MODELO_BOAS_VINDAS_JA_EXISTE",
                                "Só pode existir um modelo de boas-vindas ativo por tenant."))));
            }
            ModeloMensagem criado = modelos.save(
                    ModeloMensagem.deBoasVindas(pedido.nome(), pedido.assunto(), pedido.corpo()));
            return ResponseEntity.created(URI.create("/api/marketing/modelos-mensagem/" + criado.getId()))
                    .body(Resposta.ok(ItemModeloMensagem.de(criado)));
        }
        if (pedido.ordemSequencia() == null) {
            throw new IllegalArgumentException("Informe a posição na sequência para o modelo de aquecimento.");
        }
        if (pedido.diasAposAnterior() == null) {
            throw new IllegalArgumentException("Informe os dias de espera após o toque anterior.");
        }
        ModeloMensagem criado = modelos.save(ModeloMensagem.deAquecimento(
                pedido.nome(),
                pedido.etapaAlvo(),
                pedido.ordemSequencia(),
                pedido.diasAposAnterior(),
                pedido.assunto(),
                pedido.corpo(),
                !Boolean.FALSE.equals(pedido.ativo())));
        return ResponseEntity.created(URI.create("/api/marketing/modelos-mensagem/" + criado.getId()))
                .body(Resposta.ok(ItemModeloMensagem.de(criado)));
    }

    @PutMapping("/api/marketing/modelos-mensagem/{id}")
    @PreAuthorize("hasAuthority('marketing.template.editar')")
    @Operation(summary = "Edita um modelo de mensagem de boas-vindas ou de aquecimento")
    @Transactional
    public ResponseEntity<Resposta<ItemModeloMensagem>> alterarModeloMensagem(@PathVariable UUID id,
            @Valid @RequestBody PedidoModeloMensagem pedido) {
        return modelos.findById(id).map(modelo -> {
            if (modelo.getTipo() == TipoModeloMensagem.BOAS_VINDAS) {
                modelo.alterarBoasVindas(pedido.nome(), pedido.assunto(), pedido.corpo());
            } else {
                int ordem = pedido.ordemSequencia() != null ? pedido.ordemSequencia() : modelo.getOrdemSequencia();
                int dias = pedido.diasAposAnterior() != null ? pedido.diasAposAnterior() : modelo.getDiasAposAnterior();
                boolean ativo = pedido.ativo() != null ? pedido.ativo() : modelo.isAtivo();
                modelo.alterarAquecimento(pedido.nome(), pedido.etapaAlvo(), ordem, dias, pedido.assunto(),
                        pedido.corpo(), ativo);
            }
            return ResponseEntity.ok(Resposta.ok(ItemModeloMensagem.de(modelos.save(modelo))));
        }).orElseGet(() -> naoEncontrado("Modelo de mensagem não encontrado."));
    }

    @DeleteMapping("/api/marketing/modelos-mensagem/{id}")
    @PreAuthorize("hasAuthority('marketing.template.excluir')")
    @Operation(summary = "Exclui um modelo de aquecimento (soft delete); o de boas-vindas não pode ser excluído")
    @Transactional
    public ResponseEntity<Resposta<Void>> excluirModeloMensagem(@PathVariable UUID id) {
        return modelos.findById(id).map(modelo -> {
            if (modelo.getTipo() == TipoModeloMensagem.BOAS_VINDAS) {
                return ResponseEntity.unprocessableEntity().body(Resposta.<Void>erro(
                        "O modelo de boas-vindas é obrigatório e não pode ser excluído.",
                        List.of(new ErroCampo("tipo", "REGRA_NEGOCIO",
                                "O modelo de boas-vindas não pode ser excluído."))));
            }
            modelos.delete(modelo);
            return ResponseEntity.ok(Resposta.<Void>ok(null));
        }).orElseGet(() -> naoEncontrado("Modelo de mensagem não encontrado."));
    }

    // --- Ciclo de vida (/api/marketing/ciclo-vida) -----------------------------------------------

    @GetMapping("/api/marketing/ciclo-vida")
    @PreAuthorize("hasAuthority('marketing.automacao.ver')")
    @Operation(summary = "Configuração do ciclo de vida do lead: máximo de tentativas e prazos de retenção LGPD")
    @Transactional
    public Resposta<ConfiguracaoCicloVida> obterCicloVida() {
        return Resposta.ok(ConfiguracaoCicloVida.de(valoresIniciais.garantirCicloVida()));
    }

    @PutMapping("/api/marketing/ciclo-vida")
    @PreAuthorize("hasAuthority('marketing.automacao.editar')")
    @Operation(summary = "Atualiza o máximo de tentativas de aquecimento e os prazos de retenção LGPD")
    @Transactional
    public Resposta<ConfiguracaoCicloVida> alterarCicloVida(@Valid @RequestBody PedidoCicloVida pedido) {
        CicloVidaConfiguracao configuracao = ciclos.findFirstBy().orElseGet(CicloVidaConfiguracao::padrao);
        configuracao.alterar(
                pedido.maxTentativasAquecimento(),
                pedido.mesesRetencaoPosEsgotamento(),
                pedido.mesesRetencaoPosOptout());
        return Resposta.ok(ConfiguracaoCicloVida.de(ciclos.save(configuracao)));
    }

    // --- Qualificação, formulários, regras e rodízio ---------------------------------------------

    @GetMapping("/api/marketing/qualificacao")
    @PreAuthorize("hasAuthority('marketing.qualificacao.ver')")
    @Operation(summary = "Configuração de qualificação (faixas, horário comercial, rodízio) e prévia de leads por faixa")
    @Transactional
    public Resposta<ConfiguracaoQualificacao> obterQualificacao() {
        QualificacaoConfiguracao configuracao = valoresIniciais.garantirQualificacao();
        return Resposta.ok(ConfiguracaoQualificacao.de(configuracao, calcularPreviaFaixas(configuracao)));
    }

    @PutMapping("/api/marketing/qualificacao")
    @PreAuthorize("hasAuthority('marketing.qualificacao.editar')")
    @Operation(summary = "Atualiza faixas de qualificação, horário comercial, dias de atendimento e rodízio")
    @Transactional
    public Resposta<ConfiguracaoQualificacao> alterarQualificacao(@Valid @RequestBody PedidoQualificacao pedido) {
        QualificacaoConfiguracao configuracao = qualificacoes.findFirstBy()
                .orElseGet(QualificacaoConfiguracao::padrao);
        configuracao.definirLimites(pedido.limiteMqlQuente(), pedido.limiteMqlMorno());
        configuracao.definirAtendimento(
                interpretarHora(pedido.horarioComercialInicio(), "horarioComercialInicio"),
                interpretarHora(pedido.horarioComercialFim(), "horarioComercialFim"),
                interpretarDias(pedido.diasAtendimento()));
        configuracao.definirRodizio(pedido.rodizioAtivo(), pedido.minutosTransbordo());
        QualificacaoConfiguracao salva = qualificacoes.save(configuracao);
        return Resposta.ok(ConfiguracaoQualificacao.de(salva, calcularPreviaFaixas(salva)));
    }

    @GetMapping("/api/marketing/qualificacao/formularios")
    @PreAuthorize("hasAuthority('marketing.qualificacao.ver')")
    @Operation(summary = "Formulários do Landing que já trouxeram lead, com os campos lidos no resumo do formulário")
    @Transactional(readOnly = true)
    public Resposta<List<FormularioQualificacao>> listarFormulariosQualificacao() {
        return Resposta.ok(formularios.listar());
    }

    @GetMapping("/api/marketing/regras-pontuacao")
    @PreAuthorize("hasAuthority('marketing.qualificacao.ver')")
    @Operation(summary = "Regras de pontuação do tenant (cria as iniciais de UTM e comportamento no primeiro uso)")
    @Transactional
    public Resposta<List<ItemRegraPontuacao>> listarRegrasPontuacao() {
        valoresIniciais.garantirRegrasIniciais();
        return Resposta.ok(regras.findAll(Sort.by(Sort.Order.desc("pontos"), Sort.Order.asc("criterio"))).stream()
                .map(ItemRegraPontuacao::de)
                .toList());
    }

    @PostMapping("/api/marketing/regras-pontuacao")
    @PreAuthorize("hasAuthority('marketing.qualificacao.editar')")
    @Operation(summary = "Cria uma regra de pontuação (campo_formulario, campo_meta, utm ou comportamento)")
    @Transactional
    public ResponseEntity<Resposta<ItemRegraPontuacao>> criarRegraPontuacao(
            @Valid @RequestBody PedidoRegraPontuacao pedido) {
        valoresIniciais.garantirRegrasIniciais();
        RegraPontuacao regra = montarRegra(null, pedido);
        RegraPontuacao criada = regras.save(regra);
        return ResponseEntity.created(URI.create("/api/marketing/regras-pontuacao/" + criada.getId()))
                .body(Resposta.ok(ItemRegraPontuacao.de(criada)));
    }

    @PutMapping("/api/marketing/regras-pontuacao/{id}")
    @PreAuthorize("hasAuthority('marketing.qualificacao.editar')")
    @Operation(summary = "Altera uma regra de pontuação")
    @Transactional
    public ResponseEntity<Resposta<ItemRegraPontuacao>> alterarRegraPontuacao(@PathVariable UUID id,
            @Valid @RequestBody PedidoRegraPontuacao pedido) {
        return regras.findById(id).map(existente -> {
            RegraPontuacao atualizada = montarRegra(existente, pedido);
            return ResponseEntity.ok(Resposta.ok(ItemRegraPontuacao.de(regras.save(atualizada))));
        }).orElseGet(() -> naoEncontrado("Regra de pontuação não encontrada."));
    }

    @DeleteMapping("/api/marketing/regras-pontuacao/{id}")
    @PreAuthorize("hasAuthority('marketing.qualificacao.editar')")
    @Operation(summary = "Exclui uma regra de pontuação (soft delete)")
    @Transactional
    public ResponseEntity<Resposta<Void>> excluirRegraPontuacao(@PathVariable UUID id) {
        return regras.findById(id).map(regra -> {
            regras.delete(regra);
            return ResponseEntity.ok(Resposta.<Void>ok(null));
        }).orElseGet(() -> naoEncontrado("Regra de pontuação não encontrada."));
    }

    @GetMapping("/api/marketing/rodizio/vendedores")
    @PreAuthorize("hasAuthority('marketing.qualificacao.ver')")
    @Operation(summary = "Vendedores cadastrados na escala do rodízio do tenant")
    @Transactional(readOnly = true)
    public Resposta<List<ItemVendedorRodizio>> listarVendedoresRodizio() {
        return Resposta.ok(vendedores.findAll(Sort.by(Sort.Order.asc("criadoEm"))).stream()
                .map(ItemVendedorRodizio::de)
                .toList());
    }

    @PutMapping("/api/marketing/rodizio/vendedores")
    @PreAuthorize("hasAuthority('marketing.qualificacao.editar')")
    @Operation(summary = "Atualiza a escala de vendedores do rodízio do tenant")
    @Transactional
    public Resposta<List<ItemVendedorRodizio>> atualizarVendedoresRodizio(
            @Valid @RequestBody PedidoEscalaVendedores pedido) {
        Map<UUID, Boolean> desejados = new LinkedHashMap<>();
        for (EntradaVendedorRodizio item : pedido.vendedores()) {
            desejados.put(item.vendedorId(), !Boolean.FALSE.equals(item.ativo()));
        }

        List<VendedorRodizio> atuais = vendedores.findAll();
        Map<UUID, VendedorRodizio> porVendedorId = new LinkedHashMap<>();
        for (VendedorRodizio atual : atuais) {
            if (!desejados.containsKey(atual.getVendedorId())) {
                vendedores.delete(atual);
            } else {
                porVendedorId.put(atual.getVendedorId(), atual);
            }
        }

        List<ItemVendedorRodizio> resultado = new ArrayList<>();
        for (Map.Entry<UUID, Boolean> entrada : desejados.entrySet()) {
            VendedorRodizio registro = porVendedorId.get(entrada.getKey());
            if (registro == null) {
                registro = new VendedorRodizio(entrada.getKey());
            }
            if (entrada.getValue()) {
                registro.ativar();
            } else {
                registro.desativar();
            }
            resultado.add(ItemVendedorRodizio.de(vendedores.save(registro)));
        }
        return Resposta.ok(resultado);
    }

    // --- Tratamento de erros de validação de domínio ---------------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Resposta<Void>> regraInvalida(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Resposta.erro("Dados inválidos.",
                List.of(new ErroCampo(null, "CAMPO_INVALIDO", ex.getMessage()))));
    }

    // --- Apoio -----------------------------------------------------------------------------------

    private PreviaFaixas calcularPreviaFaixas(QualificacaoConfiguracao configuracao) {
        long quente = 0;
        long morno = 0;
        long nutricao = 0;
        long semPontuacao = 0;
        for (Lead lead : leads.findAll()) {
            Integer pts = lead.getPontuacao();
            if (pts == null) {
                semPontuacao++;
            } else if (pts >= configuracao.getLimiteMqlQuente()) {
                quente++;
            } else if (pts >= configuracao.getLimiteMqlMorno()) {
                morno++;
            } else {
                nutricao++;
            }
        }
        return new PreviaFaixas(quente, morno, nutricao, semPontuacao);
    }

    private static RegraPontuacao montarRegra(RegraPontuacao existente, PedidoRegraPontuacao pedido) {
        FonteRegra fonte = interpretarFonte(pedido.fonte());
        RegraPontuacao regra = existente;
        switch (fonte) {
            case CAMPO_FORMULARIO -> {
                if (pedido.formularioId() == null || pedido.campoId() == null) {
                    throw new IllegalArgumentException(
                            "Regra sobre campo do formulário exige formularioId e campoId.");
                }
                List<String> valoresTexto = pedido.valores().stream().map(String::valueOf).toList();
                if (regra == null) {
                    regra = RegraPontuacao.porCampo(pedido.criterio(), pedido.formularioId(), pedido.campoId(),
                            valoresTexto, pedido.pontos());
                } else {
                    regra.alterar(pedido.criterio(), valoresTexto, pedido.pontos());
                    regra.apontarParaCampo(pedido.formularioId(), pedido.campoId());
                }
            }
            case CAMPO_META -> {
                if (pedido.campoMeta() == null || pedido.campoMeta().isBlank()) {
                    throw new IllegalArgumentException("Regra sobre o Meta exige o nome da pergunta (campoMeta).");
                }
                List<String> valoresTexto = pedido.valores().stream().map(String::valueOf).toList();
                if (regra == null) {
                    regra = RegraPontuacao.porCampoMeta(pedido.criterio(), pedido.campoMeta(), valoresTexto,
                            pedido.pontos());
                } else {
                    regra.alterar(pedido.criterio(), valoresTexto, pedido.pontos());
                    regra.apontarParaCampoMeta(pedido.campoMeta());
                }
            }
            case UTM -> {
                if (regra == null) {
                    regra = RegraPontuacao.porUtm(pedido.criterio(), pedido.valores(), pedido.pontos());
                } else {
                    regra.alterar(pedido.criterio(), pedido.valores(), pedido.pontos());
                    regra.apontarParaUtm();
                }
            }
            case COMPORTAMENTO -> {
                MetricaComportamento metrica = MetricaComportamento.de(pedido.campoComportamento())
                        .orElseThrow(() -> new IllegalArgumentException("Métrica de comportamento inválida: "
                                + pedido.campoComportamento()
                                + ". Use tempo_na_pagina, profundidade_rolagem, pagina_visitada, elemento_clicado ou tipo_dispositivo."));
                if (regra == null) {
                    regra = RegraPontuacao.porComportamento(pedido.criterio(), metrica, pedido.valores(),
                            pedido.pontos());
                } else {
                    regra.alterar(pedido.criterio(), pedido.valores(), pedido.pontos());
                    regra.apontarParaComportamento(metrica);
                }
            }
        }
        if (Boolean.FALSE.equals(pedido.ativo())) {
            regra.desativar();
        } else {
            regra.ativar();
        }
        return regra;
    }

    private static CanalAnuncioTipo interpretarCanal(String valor) {
        if (valor != null) {
            for (CanalAnuncioTipo tipo : CanalAnuncioTipo.values()) {
                if (tipo.valor().equals(valor.strip())) {
                    return tipo;
                }
            }
        }
        throw new IllegalArgumentException("Canal inválido: " + valor + ". Use google_ads ou meta_ads.");
    }

    private static TipoModeloMensagem interpretarTipoModelo(String valor) {
        if (valor != null) {
            for (TipoModeloMensagem tipo : TipoModeloMensagem.values()) {
                if (tipo.valor().equals(valor.strip())) {
                    return tipo;
                }
            }
        }
        throw new IllegalArgumentException("Tipo de modelo inválido: " + valor + ". Use boas_vindas ou aquecimento.");
    }

    private static FonteRegra interpretarFonte(String valor) {
        if (valor != null) {
            for (FonteRegra fonte : FonteRegra.values()) {
                if (fonte.valor().equals(valor.strip())) {
                    return fonte;
                }
            }
        }
        throw new IllegalArgumentException("Fonte de regra inválida: " + valor
                + ". Use campo_formulario, campo_meta, utm ou comportamento.");
    }

    private static LocalTime interpretarHora(String valor, String campo) {
        try {
            return LocalTime.parse(valor.strip());
        } catch (DateTimeParseException erro) {
            throw new IllegalArgumentException("Horário inválido em " + campo + ": use o formato HH:mm.");
        }
    }

    private static Set<DayOfWeek> interpretarDias(List<Integer> valores) {
        if (valores == null || valores.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um dia de atendimento.");
        }
        Set<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        for (Integer dia : valores) {
            if (dia == null || dia < 1 || dia > 7) {
                throw new IllegalArgumentException("Dia da semana inválido: " + dia + ". Use de 1 (segunda) a 7 (domingo).");
            }
            dias.add(DayOfWeek.of(dia));
        }
        return dias;
    }

    private static <T> ResponseEntity<Resposta<T>> naoEncontrado(String mensagem) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Resposta.erro(mensagem));
    }
}
