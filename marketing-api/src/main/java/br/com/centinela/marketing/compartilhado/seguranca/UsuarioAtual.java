package br.com.centinela.marketing.compartilhado.seguranca;

import java.util.UUID;

/** Identidade do usuário autenticado e do tenant da requisição atual. */
public record UsuarioAtual(UUID tenantId, UUID usuarioId) {
}
