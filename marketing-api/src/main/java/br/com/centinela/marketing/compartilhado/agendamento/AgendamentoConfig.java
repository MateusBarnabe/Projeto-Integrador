package br.com.centinela.marketing.compartilhado.agendamento;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga os jobs {@code @Scheduled} do módulo (norteador §4). Rodam sem token: cada job percorre os
 * tenants e define o {@code TenantContexto} antes de cada consulta.
 */
@Configuration
@EnableScheduling
public class AgendamentoConfig {
}
