package br.com.centinela.marketing.entrada;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Dados declarados, de origem, técnicos e comportamentais recebidos no evento de captação. */
public record LeadEnriquecido(
        UUID visitanteId,
        @NotBlank String nome,
        @NotBlank @Email String email,
        String whatsapp,
        String telefone,
        @JsonProperty("utm_source") String utmSource,
        @JsonProperty("utm_medium") String utmMedium,
        @JsonProperty("utm_campaign") String utmCampaign,
        @JsonProperty("utm_content") String utmContent,
        String referrer,
        @JsonProperty("device_type") String deviceType,
        String os,
        String browser,
        String location,
        String timezone,
        String language,
        @JsonProperty("dwell_time_seconds") Integer dwellTimeSeconds,
        @JsonProperty("scroll_depth_percent") Integer scrollDepthPercent,
        @JsonProperty("clicked_elements") List<String> clickedElements,
        @JsonProperty("pages_visited") List<String> pagesVisited) {
}