package br.com.centinela.marketing.compartilhado.http;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Servidor HTTP mínimo (JDK) que faz o papel do identity, do Landing e do CRM nos testes do cliente,
 * sem Docker nem WireMock. Cada rota responde pelo que o teste programou e todo pedido fica gravado.
 */
final class ServidorDeTeste implements AutoCloseable {

    record Pedido(String metodo, String caminho, Map<String, List<String>> cabecalhos, String corpo) {

        String cabecalho(String nome) {
            return cabecalhos.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(nome))
                    .findFirst()
                    .map(e -> e.getValue().get(0))
                    .orElse(null);
        }
    }

    record Resposta(int status, String corpo, long atrasoMs) {

        static Resposta json(int status, String corpo) {
            return new Resposta(status, corpo, 0);
        }
    }

    private final HttpServer servidor;
    private final java.util.concurrent.ExecutorService atendentes = java.util.concurrent.Executors.newCachedThreadPool();
    private final Map<String, Function<Pedido, Resposta>> rotas = new ConcurrentHashMap<>();
    private final List<Pedido> pedidos = new CopyOnWriteArrayList<>();

    ServidorDeTeste() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::atender);
        servidor.setExecutor(atendentes);
        servidor.start();
    }

    String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    void responder(String metodoECaminho, Function<Pedido, Resposta> resposta) {
        rotas.put(metodoECaminho, resposta);
    }

    void responder(String metodoECaminho, int status, String corpo) {
        responder(metodoECaminho, p -> Resposta.json(status, corpo));
    }

    List<Pedido> pedidos(String metodoECaminho) {
        return pedidos.stream().filter(p -> (p.metodo() + " " + p.caminho()).equals(metodoECaminho)).toList();
    }

    private void atender(HttpExchange troca) throws IOException {
        String caminho = troca.getRequestURI().getPath()
                + (troca.getRequestURI().getQuery() == null ? "" : "?" + troca.getRequestURI().getQuery());
        Pedido pedido = new Pedido(troca.getRequestMethod(), caminho, Map.copyOf(troca.getRequestHeaders()),
                new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        pedidos.add(pedido);

        Function<Pedido, Resposta> rota = rotas.get(pedido.metodo() + " " + pedido.caminho());
        Resposta resposta = rota == null
                ? Resposta.json(404, "{\"success\":false,\"data\":null,\"message\":\"Rota não programada.\",\"errors\":[]}")
                : rota.apply(pedido);
        try {
            if (resposta.atrasoMs() > 0) {
                Thread.sleep(resposta.atrasoMs());
            }
            byte[] corpo = resposta.corpo() == null ? new byte[0] : resposta.corpo().getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().set("Content-Type", "application/json");
            troca.sendResponseHeaders(resposta.status(), corpo.length == 0 ? -1 : corpo.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(corpo);
            }
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
        } catch (IOException excecao) {
            // o cliente desistiu (timeout): nada a fazer
        } finally {
            troca.close();
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
        atendentes.shutdownNow();
    }
}
