package br.com.centinela.marketing.rastreamento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Job 4.7, descarte das visitas que não converteram (R1): o "lead 0" que não enviou o formulário nem
 * deu sinal dentro do prazo do tenant tem o contexto técnico e o comportamento apagados. A linha fica
 * como {@code descartada}, só com o link, a UTM e as datas, para as contagens do rastreamento.
 *
 * <p>Uma instrução só, para todos os tenants, em lotes com {@code FOR UPDATE SKIP LOCKED}: a visita
 * que estiver recebendo um sinal ou sendo ligada a um lead fica para a próxima execução.
 */
@Service
public class DescarteDeVisitas {

    private static final Logger LOG = LoggerFactory.getLogger(DescarteDeVisitas.class);

    /** Visitas por lote; o job repete até acabar as vencidas. */
    static final int LOTE = 500;

    private final AcessoPublico acesso;

    DescarteDeVisitas(AcessoPublico acesso) {
        this.acesso = acesso;
    }

    /** @return quantas visitas foram descartadas nesta execução */
    public int descartar() {
        int total = 0;
        int noLote;
        do {
            noLote = acesso.descartarVencidas(LOTE);
            total += noLote;
        } while (noLote == LOTE);
        if (total > 0) {
            LOG.info("Descarte de visitas: {} visita(s) sem conversão no prazo", total);
        }
        return total;
    }
}
