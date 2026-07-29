package com.neobank.module.controller;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.dto.AnalystClaimRequest;
import com.neobank.module.dto.AnalystQueueView;
import com.neobank.module.dto.AnalystResolutionRequest;
import com.neobank.module.dto.CaseSearchView;
import com.neobank.module.dto.OverrideCaseRequest;
import com.neobank.module.dto.ScreeningRecordDetailView;
import com.neobank.module.service.AnalystQueueService;
import com.neobank.module.service.CasesService;
import com.neobank.module.service.OverrideCaseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * uc-01: Search screening cases.
 *
 * Bank employees find a case by application ID or applicant name without this module
 * ever storing applicant data.
 */
@RestController
@RequestMapping("/api/v1/cases")
public class CasesController {

    private final CasesService cases;
    private final AnalystQueueService queue;
    private final OverrideCaseService overrides;

    public CasesController(CasesService cases, AnalystQueueService queue,
                           OverrideCaseService overrides) {
        this.cases = cases;
        this.queue = queue;
        this.overrides = overrides;
    }

    /**
     * Search cases by application ID or applicant name.
     *
     * @param q     search query (application ID or applicant name)
     * @param limit result limit, capped at 10
     * @return list of matching cases, newest first
     */
    @GetMapping
    public List<?> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String outcome,
            @RequestParam(name = "unclaimed-first", defaultValue = "true") boolean unclaimedFirst) {
        if ("REVIEW".equalsIgnoreCase(outcome)) {
            return queue.openQueue(unclaimedFirst);
        }
        return cases.search(q, limit);
    }

    /** Proxies the live applicant record without copying customer data into this schema. */
    @GetMapping("/{applicationId}/applicant")
    public ApplicantView applicant(@PathVariable String applicationId) {
        return cases.getApplicant(applicationId);
    }

    @PostMapping("/{applicationId}/claim")
    public AnalystQueueView claim(@PathVariable String applicationId,
                                  @Valid @RequestBody AnalystClaimRequest request) {
        return queue.claim(applicationId, request.analyst());
    }

    @PostMapping("/{applicationId}/release")
    public AnalystQueueView release(@PathVariable String applicationId,
                                    @Valid @RequestBody AnalystClaimRequest request) {
        return queue.release(applicationId, request.analyst());
    }

    @PostMapping("/{applicationId}/resolution")
    public AnalystQueueView resolve(@PathVariable String applicationId,
                                    @Valid @RequestBody AnalystResolutionRequest request) {
        return queue.resolve(applicationId, request);
    }

    @PostMapping("/{applicationId}/override")
    public ScreeningRecordDetailView override(
            @PathVariable String applicationId,
            @Valid @RequestBody OverrideCaseRequest request) {
        return overrides.override(applicationId, request);
    }
}
