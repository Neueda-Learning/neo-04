package com.neobank.module.service;

import com.neobank.module.dto.CaseSearchView;
import com.neobank.module.integrations.orchestrator.OrchestratorApplicationSearchClient;
import com.neobank.module.repository.ScreeningRecordRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * uc-01: Search screening cases by application ID or applicant name without storing applicant data.
 *
 * <p>ID search hits the local table directly. Name search resolves IDs through the orchestrator
 * (v5 contract addition: {@code GET /applications?name=...}), then fetches the matching
 * screening records locally. This module never stores applicant data for search purposes.</p>
 */
@Service
public class CasesService {

    private static final Logger log = LoggerFactory.getLogger(CasesService.class);
    private static final int RESULT_LIMIT = 10;

    private final ScreeningRecordRepository cases;
    private final OrchestratorApplicationSearchClient orchestrator;

    public CasesService(ScreeningRecordRepository cases,
                        OrchestratorApplicationSearchClient orchestrator) {
        this.cases = cases;
        this.orchestrator = orchestrator;
    }

    /**
     * Search screening cases. Query can be an application ID or an applicant name.
     * Returns at most 10 results, newest first.
     */
    @Transactional(readOnly = true)
    public List<CaseSearchView> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String q = query.trim();
        limit = Math.min(limit, RESULT_LIMIT);

        // Heuristic: if the query looks like an ID (must contain hyphen or digit to distinguish
        // from pure names like "viktor"), try ID search first. Otherwise, try name search.
        // Patterns: "SIM-15" (hyphen+digit), "sim-15" (lowercase), "SIM15" (no hyphen),
        // but "viktor" (pure letters) → name search.
        boolean looksLikeId = q.matches(".*[\\-\\d].*");

        if (looksLikeId) {
            log.debug("Searching by application ID: {}", q);
            return cases.findByApplicationIdContainingIgnoreCaseOrderByCreatedAtDesc(q)
                    .stream()
                    .limit(limit)
                    .map(CaseSearchView::of)
                    .toList();
        } else {
            log.debug("Searching by applicant name via orchestrator: {}", q);
            // Name search: orchestrator only (v5 contract: GET /applications?name=...)
            // This module never stores applicant data, so name search cannot be done locally.
            try {
                log.debug("Orchestrator client type: {}", orchestrator.getClass().getSimpleName());
                List<String> applicationIds = orchestrator.searchApplicationsByName(q, limit);
                log.debug("Orchestrator returned {} results for query: {}", applicationIds.size(), q);
                if (applicationIds.isEmpty()) {
                    return List.of();
                }
                return cases.findByApplicationIdInOrderByCreatedAtDesc(applicationIds)
                        .stream()
                        .limit(limit)
                        .map(CaseSearchView::of)
                        .toList();
            } catch (Exception e) {
                log.warn("Could not search by name in orchestrator: {}", e.getMessage());
                // Orchestrator down or does not support name search -> no results (AC7 — rows render with ids, names show "—")
                return List.of();
            }
        }
    }
}
