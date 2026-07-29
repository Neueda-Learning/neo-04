package com.neobank.module.service;

import com.neobank.module.dto.CaseSearchView;
import com.neobank.module.dto.ApplicantView;
import com.neobank.module.integrations.orchestrator.OrchestratorApplicationClient;
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
    private final OrchestratorApplicationClient applicationClient;

    public CasesService(ScreeningRecordRepository cases,
                        OrchestratorApplicationSearchClient orchestrator,
                        OrchestratorApplicationClient applicationClient) {
        this.cases = cases;
        this.orchestrator = orchestrator;
        this.applicationClient = applicationClient;
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

        // Heuristic: if the query looks like an ID (uppercase, hyphen, numbers),
        // try ID search first. Otherwise, try name search.
        boolean looksLikeId = q.matches("[A-Z0-9\\-]+");

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
                List<String> applicationIds = orchestrator.searchApplicationsByName(q, limit);
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

    /** Fetches applicant data from its owner on every request; this path performs no database write. */
    public ApplicantView getApplicant(String applicationId) {
        return ApplicantView.of(applicationClient.findById(applicationId));
    }
}
