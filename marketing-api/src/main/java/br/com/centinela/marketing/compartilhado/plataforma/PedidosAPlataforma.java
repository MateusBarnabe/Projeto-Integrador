package br.com.centinela.marketing.compartilhado.plataforma;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.centinela.marketing.compartilhado.mensageria.Evento;
import br.com.centinela.marketing.compartilhado.mensageria.PublicadorDeEventos;

/**
 * Pedidos à plataforma na exchange {@code identity.entrada} (Contrato §9.7, norteador §8), no
 * formato de {@code contratos/identity.asyncapi.yaml} (0.3.0): {@code identity.notificacao.criar}
 * e {@code identity.timeline.registrar}. O envelope, o {@code user_id = mq_marketing} e o envio
 * depois do commit são do {@link PublicadorDeEventos}; por isso estes métodos precisam rodar dentro
 * de uma transação, no tenant do contexto.
 *
 * <p>O que vai em {@code dados} fica em fila, em log e na {@code .dlq}: quem chama não põe dado
 * pessoal em título ou texto além do que o contrato exige.
 */
@Component
public class PedidosAPlataforma {

    public static final String NOTIFICACAO_CRIAR = "identity.notificacao.criar";
    public static final String TIMELINE_REGISTRAR = "identity.timeline.registrar";
    static final int VERSAO = 1;

    static final int MAX_TITULO = 120;
    static final int MAX_TEXTO = 500;
    static final int MAX_ROTA = 500;

    private final PublicadorDeEventos publicador;

    public PedidosAPlataforma(PublicadorDeEventos publicador) {
        this.publicador = publicador;
    }

    /** Pede à plataforma uma notificação para um usuário ({@code DadosNotificacao}). */
    public Evento criarNotificacao(Notificacao notificacao) {
        return publicador.pedirAPlataforma(NOTIFICACAO_CRIAR, VERSAO, Objects.requireNonNull(notificacao));
    }

    /** Pede o registro de um fato na timeline de uma empresa do CRM ({@code DadosTimeline}). */
    public Evento registrarNaTimeline(RegistroDeTimeline registro) {
        return publicador.pedirAPlataforma(TIMELINE_REGISTRAR, VERSAO, Objects.requireNonNull(registro));
    }

    /**
     * {@code dados} de {@code identity.notificacao.criar}.
     *
     * @param usuarioId destinatário (usuário do identity)
     * @param titulo    até 120 caracteres
     * @param texto     opcional, até 500 caracteres
     * @param rota      opcional, relativa ao front do módulo e sem o código (Contrato §12.2):
     *                  {@code /leads/{id}}; a casca abre {@code /app/marketing/leads/{id}}
     */
    public record Notificacao(UUID usuarioId, CategoriaNotificacao categoria, String titulo, String texto,
            String rota) {

        public Notificacao {
            Objects.requireNonNull(usuarioId, "usuarioId");
            Objects.requireNonNull(categoria, "categoria");
            titulo = exigirTexto(titulo, "titulo", MAX_TITULO);
            texto = opcional(texto, MAX_TEXTO);
            rota = opcional(rota, MAX_ROTA);
        }
    }

    /**
     * {@code dados} de {@code identity.timeline.registrar}. O texto chega pronto para exibição; a
     * plataforma não consulta outros módulos.
     *
     * @param empresaId  empresa do CRM (só o UUID, sem FK)
     * @param tipoDoFato tipo do fato que gerou o registro, como {@code marketing.lead.atribuido}
     * @param texto      até 500 caracteres
     * @param rota       opcional; para onde a casca navega ao clicar, com o prefixo do front
     *                   ({@code /modulos/marketing/leads/{id}}), como no exemplo do contrato
     */
    public record RegistroDeTimeline(UUID empresaId, String tipoDoFato, String texto, String rota) {

        public RegistroDeTimeline {
            Objects.requireNonNull(empresaId, "empresaId");
            tipoDoFato = exigirTexto(tipoDoFato, "tipoDoFato", Integer.MAX_VALUE);
            texto = exigirTexto(texto, "texto", MAX_TEXTO);
            rota = opcional(rota, MAX_ROTA);
        }
    }

    private static String exigirTexto(String valor, String campo, int maximo) {
        String limpo = opcional(valor, maximo);
        if (limpo == null) {
            throw new IllegalArgumentException(campo + " é obrigatório.");
        }
        return limpo;
    }

    /** Vazio vira nulo; texto acima do limite do contrato é cortado com reticências. */
    private static String opcional(String valor, int maximo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpo = valor.strip();
        return limpo.length() <= maximo ? limpo : limpo.substring(0, maximo - 1) + "…";
    }
}
