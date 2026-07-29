package com.neobank.module.integrations.orchestrator;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock implementation of orchestrator application search.
 * Used in development when the real orchestrator search is not available.
 * 
 * This is a placeholder — name search will return empty results.
 * Once the orchestrator supports v5's GET /applications?name=..., replace this with
 * OrchestratorApplicationSearchClientImpl.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.search.enabled", havingValue = "false", matchIfMissing = true)
public class MockOrchestratorApplicationSearchClient implements OrchestratorApplicationSearchClient {

    @Override
    public List<String> searchApplicationsByName(String nameQuery, int limit) {
        // No real orchestrator search available in development.
        // The search board still works via ID search on the local table.
        return List.of();
    }
}
