package com.neobank.module.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.OverrideCaseRequest;
import com.neobank.module.dto.OverrideLogView;
import com.neobank.module.dto.ScreeningRecordDetailView;
import com.neobank.module.service.AnalystQueueService;
import com.neobank.module.service.CasesService;
import com.neobank.module.service.OverrideCaseService;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CasesController.class)
class OverrideCaseControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CasesService cases;

    @MockBean
    private AnalystQueueService queue;

    @MockBean
    private OverrideCaseService overrides;

    @Test
    void overridesACaseAndReturnsItsAuditHistory() throws Exception {
        var audit = new OverrideLogView("HIT", "CLEAR", "passport verified distinct",
                "b.dimovski", Instant.parse("2026-07-29T08:00:00Z"));
        var detail = new ScreeningRecordDetailView("app-1401", "HIT", "CLEAR", "COMPLETE", "SENT",
                "SCR_EXACT_MATCH", 1, new ObjectMapper().readTree("{}"),
                null, null, null, null, null, null, List.of(audit),
                Instant.parse("2026-07-29T07:00:00Z"), Instant.parse("2026-07-29T08:00:00Z"));
        when(overrides.override(any(), any())).thenReturn(detail);

        mvc.perform(post("/api/v1/cases/app-1401/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newOutcome":"CLEAR","reason":"passport verified distinct",
                                 "operator":"b.dimovski"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.machineOutcome").value("HIT"))
                .andExpect(jsonPath("$.finalOutcome").value("CLEAR"))
                .andExpect(jsonPath("$.overrides[0].oldOutcome").value("HIT"))
                .andExpect(jsonPath("$.overrides[0].newOutcome").value("CLEAR"))
                .andExpect(jsonPath("$.overrides[0].operator").value("b.dimovski"));

        verify(overrides).override(any(String.class), any(OverrideCaseRequest.class));
    }

    @Test
    void rejectsMissingReasonAndOperator() throws Exception {
        mvc.perform(post("/api/v1/cases/app-1401/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newOutcome\":\"CLEAR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("reason")))
                .andExpect(jsonPath("$.message").value(containsString("operator")));

        verifyNoInteractions(overrides);
    }

    @Test
    void rejectsAnOutcomeOutsideTheManualSet() throws Exception {
        mvc.perform(post("/api/v1/cases/app-1401/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newOutcome":"PENDING","reason":"wrong","operator":"b.dimovski"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(overrides);
    }

    @Test
    void answers404ForAnUnknownCase() throws Exception {
        when(overrides.override(any(), any()))
                .thenThrow(new NoSuchElementException("no case for app-9999"));

        mvc.perform(post("/api/v1/cases/app-9999/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newOutcome":"CLEAR","reason":"verified","operator":"b.dimovski"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("app-9999")));
    }
}
