package com.neobank.module.integrations.orchestrator;

import java.util.List;

/**
 * v5 contract addition: name-based search to resolve applicant names to application IDs.
 * Used by uc-01 (the search board) to find cases without storing applicant data.
 */
public interface OrchestratorApplicationSearchClient {

    /**
     * Search the orchestrator's application list by applicant name.
     * Returns up to {@code limit} application IDs that match the query.
     */
    List<String> searchApplicationsByName(String nameQuery, int limit);
}
