package br.com.centinela.marketing.painel;

/** O tenant já tem o máximo de conexões SSE abertas: vira {@code 429} com envelope. */
class LimiteDeConexoesException extends RuntimeException {

    LimiteDeConexoesException(int limite) {
        super("O limite de " + limite + " conexões em tempo real por empresa foi atingido.");
    }
}
