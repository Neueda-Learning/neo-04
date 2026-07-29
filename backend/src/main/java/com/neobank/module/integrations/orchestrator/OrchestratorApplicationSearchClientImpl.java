package com.neobank.module.integrations.orchestrator;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Development implementation of orchestrator application search.
 * Queries the orchestrator (or sidecar) for applications matching an applicant name.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.search.enabled", havingValue = "true", matchIfMissing = false)
public class OrchestratorApplicationSearchClientImpl implements OrchestratorApplicationSearchClient {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorApplicationSearchClientImpl.class);

    private final RestClient restClient;

    public OrchestratorApplicationSearchClientImpl(@Value("${orchestrator.url}") String orchestratorUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(orchestratorUrl)
                .build();
    }

    /**
     * Search the orchestrator by applicant name. Returns application IDs whose applicant names
     * contain the query string (substring match, case-insensitive).
     *
     * This delegates to the orchestrator's v5 contract: GET /api/v1/applications?name={query}.
     * If the orchestrator does not support the endpoint, returns an empty list; the service
     * gracefully handles no results, and the case board shows no matches (AC7).
     */
    @Override
    public List<String> searchApplicationsByName(String nameQuery, int limit) {
        if (nameQuery == null || nameQuery.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> results = restClient.get()
                    .uri("/api/v1/applications?name={name}&limit={limit}", nameQuery, limit)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (results == null || results.isEmpty()) {
                return List.of();
            }

            return results.stream()
                    .map(app -> (String) app.get("applicationId"))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("Orchestrator name search failed for '{}': {}", nameQuery, e.getMessage());
            // Orchestrator down or does not support ?name= yet -> no results
            return List.of();
        }
    }
}
