package com.neobank.module.controller;

import com.neobank.module.dto.CaseSearchView;
import com.neobank.module.dto.ApplicantView;
import com.neobank.module.service.CasesService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
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

    public CasesController(CasesService cases) {
        this.cases = cases;
    }

    /**
     * Search cases by application ID or applicant name.
     *
     * @param q     search query (application ID or applicant name)
     * @param limit result limit, capped at 10
     * @return list of matching cases, newest first
     */
    @GetMapping
    public List<CaseSearchView> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int limit) {
        return cases.search(q, limit);
    }

    /** Proxies the live applicant record without copying customer data into this schema. */
    @GetMapping("/{applicationId}/applicant")
    public ApplicantView applicant(@PathVariable String applicationId) {
        return cases.getApplicant(applicationId);
    }
}
