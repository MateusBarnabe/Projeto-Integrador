package br.com.centinela.marketing.compartilhado.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Converte exceções em respostas com o envelope padrão e os códigos da §8.4 do Contrato.
 */
@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Resposta<Void>> dadosInvalidos(MethodArgumentNotValidException ex) {
        List<ErroCampo> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErroCampo(e.getField(), "CAMPO_INVALIDO", e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(Resposta.erro("Dados inválidos.", erros));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Resposta<Void>> corpoIlegivel(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Resposta.erro("Corpo da requisição inválido."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Resposta<Void>> naoEncontrado(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Resposta.erro("Recurso não encontrado."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Resposta<Void>> metodoNaoSuportado(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Resposta.erro("Método não suportado."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Resposta<Void>> erroInesperado(Exception ex) {
        log.error("Erro inesperado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Resposta.erro("Erro inesperado."));
    }
}
