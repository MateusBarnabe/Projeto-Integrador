package br.com.centinela.marketing.compartilhado.mensageria;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Ids dos eventos já tratados (Contrato §9.7, {@code V1}). Precisa rodar na mesma transação do
 * efeito: se o efeito falhar, o registro volta junto e a nova tentativa processa de novo.
 */
@Repository
class EventosProcessados {

    private final JdbcTemplate jdbc;

    EventosProcessados(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return {@code true} na primeira vez que o id aparece; {@code false} se já foi processado. Com
     *         duas entregas simultâneas, a segunda espera o commit da primeira e recebe {@code false}.
     */
    boolean registrar(UUID eventoId, String tipo) {
        int inseridos = jdbc.update(
                "INSERT INTO marketing.eventos_processados (evento_id, tipo) VALUES (?, ?) ON CONFLICT (evento_id) DO NOTHING",
                eventoId, tipo);
        return inseridos == 1;
    }
}
