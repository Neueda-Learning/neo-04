package com.neobank.module.service.matching;

import com.neobank.module.model.ScreeningOutcome;
import java.util.List;

/**
 * What {@link ScreeningMatcher#match} computed: the outcome, every reason code that contributed to
 * it (uc-02 AC8 — multiple REVIEW causes are ALL reported), and the evidence behind it.
 */
public record MatchVerdict(ScreeningOutcome outcome, List<String> reasonCodes, MatchEvidence evidence) {

    public String reasonCode() {
        return String.join(",", reasonCodes);
    }
}
