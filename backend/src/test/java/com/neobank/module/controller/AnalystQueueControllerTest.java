package com.neobank.module.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalystQueueControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanAndSeed() {
        jdbc.update("delete from override_log");
        jdbc.update("delete from screening_record");
        insertReview("app-1360", "SCR_HIGH_RISK_COUNTRY", "{\"countryRisk\":{\"highRisk\":true}}");
        insertReview("app-1372", "SCR_PARTIAL_MATCH",
                "{\"candidates\":[{\"verdict\":\"partial\"}]}");
    }

    @Test
    void queueListsOpenReviewsWithTheirCause() throws Exception {
        mvc.perform(get("/api/v1/cases?outcome=REVIEW&unclaimed-first=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].applicationId").value("app-1360"))
                .andExpect(jsonPath("$[0].causes[0]").value("country"))
                .andExpect(jsonPath("$[1].causes[0]").value("partial"));
    }

    @Test
    void claimIsIdempotentForOwnerAndConflictsForAnotherAnalyst() throws Exception {
        claim("app-1360", "r.iqbal").andExpect(status().isOk())
                .andExpect(jsonPath("$.claimedBy").value("r.iqbal"));
        claim("app-1360", "r.iqbal").andExpect(status().isOk());
        claim("app-1360", "b.dimovski").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("r.iqbal")));
    }

    @Test
    void resolutionValidatesReasonAndLeavesTheQueue() throws Exception {
        claim("app-1360", "r.iqbal").andExpect(status().isOk());

        mvc.perform(post("/api/v1/cases/app-1360/resolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"CLEAR\",\"reason\":\"\",\"analyst\":\"r.iqbal\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/cases/app-1360/resolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"CLEAR\",\"reason\":\"jurisdiction only\","
                                + "\"analyst\":\"r.iqbal\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CLEAR"))
                .andExpect(jsonPath("$.machineOutcome").value("REVIEW"))
                .andExpect(jsonPath("$.resolvedBy").value("r.iqbal"));

        mvc.perform(get("/api/v1/cases?outcome=REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    private org.springframework.test.web.servlet.ResultActions claim(String id, String analyst) throws Exception {
        return mvc.perform(post("/api/v1/cases/{id}/claim", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"analyst\":\"" + analyst + "\"}"));
    }

    private void insertReview(String id, String reasonCode, String evidence) {
        jdbc.update("""
                insert into screening_record
                (application_id, machine_outcome, final_outcome, callback_status, processing_status,
                 reason_code, config_version, evidence, created_at, updated_at)
                values (?, 'REVIEW', 'REVIEW', 'SENT', 'COMPLETE', ?, 1, ?, ?, ?)
                """, id, reasonCode, evidence, Instant.parse("2026-07-15T08:00:00Z"),
                Instant.parse("2026-07-15T08:00:01Z"));
    }
}
