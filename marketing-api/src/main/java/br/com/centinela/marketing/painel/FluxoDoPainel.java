package br.com.centinela.marketing.painel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.painel.ConsultasDoPainel.AssinaturaDeVisitas;
import br.com.centinela.marketing.painel.ConsultasDoPainel.FatoDoPainel;
import jakarta.annotation.PreDestroy;

/**
 * Fluxo SSE do painel (norteador §9.1): manda ao navegador os fatos que acontecem nos leads do tenant, só
 * ids e números. O {@code id} de cada mensagem é {@code lead_eventos.sequencia}, então quem reconecta com
 * {@code Last-Event-ID} recebe só o que perdeu, e funciona com várias instâncias do módulo.
 *
 * <p>Cada conexão consulta {@code lead_eventos} e {@code visitas_anuncio} do próprio tenant a cada poucos
 * segundos, por um agendador próprio (nada de {@code @Scheduled}: não há o que desligar nos testes). O
 * {@code TenantFiltro} limpa o contexto no fim da requisição, então o tenant é guardado na abertura e
 * definido de novo em cada consulta. Uma mensagem {@code : ping} sai quando a conexão fica quieta, para o
 * gateway não fechá-la, e há um limite de conexões por tenant.
 *
 * <p>Um fato que confirma depois de outro de {@code sequencia} maior (transações concorrentes) pode não
 * chegar ao fluxo aberto; a releitura do resumo (a tela refaz a cada 30 s sem fluxo, e a cada reconexão)
 * o corrige.
 */
@Component
public class FluxoDoPainel {

    private static final Logger LOG = LoggerFactory.getLogger(FluxoDoPainel.class);

    private final ConsultasDoPainel consultas;
    private final PainelPropriedades.Sse ajuste;
    private final ScheduledThreadPoolExecutor agendador;
    private final Map<UUID, Set<Conexao>> conexoes = new ConcurrentHashMap<>();

    FluxoDoPainel(ConsultasDoPainel consultas, PainelPropriedades propriedades) {
        this.consultas = consultas;
        this.ajuste = propriedades.sse();
        AtomicInteger numero = new AtomicInteger();
        this.agendador = new ScheduledThreadPoolExecutor(ajuste.threads(), tarefa -> {
            Thread thread = new Thread(tarefa, "painel-sse-" + numero.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.agendador.setRemoveOnCancelPolicy(true);
    }

    /**
     * Abre o fluxo do tenant. Sem {@code ultimoId}, começa do fato mais recente (o resumo cuida do que já
     * houve); com ele, reenvia o que veio depois.
     *
     * @param filtro  período e campanha do bloco {@code lead_zero_atualizado}
     * @param duracao quanto a conexão vive, no máximo (a expiração do token e o teto da configuração)
     * @throws LimiteDeConexoesException o tenant já tem o máximo de conexões abertas
     */
    SseEmitter abrir(UUID tenant, Long ultimoId, FiltroPainel filtro, Duration duracao) {
        Conexao conexao = new Conexao(tenant, filtro, new SseEmitter(duracao.toMillis()));
        reservar(conexao);
        try {
            TenantContexto.executar(tenant, () -> {
                conexao.cursor = ultimoId != null ? ultimoId : consultas.ultimaSequencia(tenant);
                conexao.visitas = consultas.assinaturaDasVisitas(tenant);
            });
            conexao.emitter.onCompletion(conexao::encerrar);
            conexao.emitter.onError(erro -> conexao.encerrar());
            conexao.emitter.onTimeout(conexao.emitter::complete);
            conexao.tarefa = agendador.scheduleWithFixedDelay(conexao::rodar, 0,
                    ajuste.intervaloConsulta().toMillis(), TimeUnit.MILLISECONDS);
            if (conexao.encerrada.get()) {
                conexao.tarefa.cancel(false);
            }
        } catch (RuntimeException excecao) {
            conexao.encerrar();
            throw excecao;
        }
        return conexao.emitter;
    }

    /** Conexões abertas do tenant. */
    int conexoesAbertas(UUID tenant) {
        Set<Conexao> abertas = conexoes.get(tenant);
        return abertas == null ? 0 : abertas.size();
    }

    /** Fecha todas as conexões (parada da aplicação e testes). */
    public void encerrarTodas() {
        conexoes.values().stream().flatMap(Set::stream).toList().forEach(conexao -> {
            conexao.encerrar();
            try {
                conexao.emitter.complete();
            } catch (RuntimeException excecao) {
                // já fechada pelo cliente
            }
        });
    }

    @PreDestroy
    void parar() {
        encerrarTodas();
        agendador.shutdownNow();
    }

    private void reservar(Conexao conexao) {
        boolean[] cheio = { false };
        conexoes.compute(conexao.tenant, (tenant, abertas) -> {
            Set<Conexao> conjunto = abertas == null ? ConcurrentHashMap.newKeySet() : abertas;
            if (conjunto.size() >= ajuste.limitePorTenant()) {
                cheio[0] = true;
            } else {
                conjunto.add(conexao);
            }
            return conjunto.isEmpty() ? null : conjunto;
        });
        if (cheio[0]) {
            throw new LimiteDeConexoesException(ajuste.limitePorTenant());
        }
    }

    private void liberar(Conexao conexao) {
        conexoes.computeIfPresent(conexao.tenant, (tenant, abertas) -> {
            abertas.remove(conexao);
            return abertas.isEmpty() ? null : abertas;
        });
    }

    /** Uma conexão aberta: o estado de leitura dela e a tarefa que a alimenta. */
    private final class Conexao {

        final UUID tenant;
        final FiltroPainel filtro;
        final SseEmitter emitter;
        final AtomicBoolean encerrada = new AtomicBoolean();
        volatile ScheduledFuture<?> tarefa;
        // Só a tarefa agendada mexe nestes (ela não se sobrepõe a si mesma)
        long cursor;
        AssinaturaDeVisitas visitas;
        Instant ultimoEnvio = Instant.now();

        Conexao(UUID tenant, FiltroPainel filtro, SseEmitter emitter) {
            this.tenant = tenant;
            this.filtro = filtro;
            this.emitter = emitter;
        }

        void rodar() {
            if (encerrada.get()) {
                return;
            }
            try {
                TenantContexto.executar(tenant, () -> {
                    try {
                        novidades();
                    } catch (IOException excecao) {
                        throw new UncheckedIOException(excecao);
                    }
                });
            } catch (UncheckedIOException | IllegalStateException excecao) {
                // Cliente que saiu ou emissor já completo
                encerrar();
                try {
                    emitter.complete();
                } catch (RuntimeException ignorada) {
                    // nada a fazer
                }
            } catch (RuntimeException excecao) {
                // Só o tipo: a mensagem pode carregar parâmetros da consulta
                LOG.warn("Falha ao consultar o painel em tempo real: {}", excecao.getClass().getSimpleName());
            }
        }

        private void novidades() throws IOException {
            List<FatoDoPainel> fatos = consultas.fatosDepois(tenant, cursor, ajuste.lote());
            for (FatoDoPainel fato : fatos) {
                enviar(fato);
                cursor = fato.sequencia();
            }

            AssinaturaDeVisitas atual = consultas.assinaturaDasVisitas(tenant);
            if (!atual.equals(visitas)) {
                visitas = atual;
                // Sem id: o bloco não vem de lead_eventos e não entra no Last-Event-ID
                emitter.send(SseEmitter.event().name("lead_zero_atualizado").data(
                        consultas.leadZero(tenant, filtro, OffsetDateTime.now(ZoneOffset.UTC)),
                        MediaType.APPLICATION_JSON));
                ultimoEnvio = Instant.now();
            }

            if (Duration.between(ultimoEnvio, Instant.now()).compareTo(ajuste.intervaloPing()) >= 0) {
                emitter.send(SseEmitter.event().comment(" ping"));
                ultimoEnvio = Instant.now();
            }
        }

        private void enviar(FatoDoPainel fato) throws IOException {
            String nome;
            Map<String, Object> dados = new LinkedHashMap<>();
            dados.put("leadId", fato.leadId().toString());
            switch (fato.tipoEvento()) {
                case "etapa_alterada" -> {
                    if (fato.etapaAnterior() == null) {
                        nome = "lead_criado";
                        dados.put("etapa", fato.etapaNova());
                        dados.put("origem", fato.origem());
                    } else {
                        nome = "lead_etapa_alterada";
                        dados.put("etapaAnterior", fato.etapaAnterior());
                        dados.put("etapaNova", fato.etapaNova());
                    }
                }
                case "qualificacao_alterada" -> {
                    nome = "lead_qualificado";
                    dados.put("faixa", fato.faixa());
                    dados.put("pontuacao", fato.pontuacao());
                }
                case "vendedor_atribuido" -> {
                    nome = "lead_atribuido";
                    dados.put("vendedorId", fato.vendedorId());
                }
                default -> {
                    return;
                }
            }
            emitter.send(SseEmitter.event().id(String.valueOf(fato.sequencia())).name(nome)
                    .data(dados, MediaType.APPLICATION_JSON));
            ultimoEnvio = Instant.now();
        }

        void encerrar() {
            if (!encerrada.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> agendada = tarefa;
            if (agendada != null) {
                agendada.cancel(false);
            }
            liberar(this);
        }
    }
}
