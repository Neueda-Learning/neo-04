package com.neobank.module.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ScreeningConfigControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDb() {
        jdbc.update("delete from override_log");
        jdbc.update("delete from screening_record");
        jdbc.update("delete from watchlist_entry");
        jdbc.update("delete from country_risk_entry");
        jdbc.update("delete from screening_config");
    }

    @Test
    void createReturns201AndDetailShape() throws Exception {
        mvc.perform(post("/api/v1/screening-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload(true)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/screening-configs/1"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.samplingFrequency").value(10))
                .andExpect(jsonPath("$.currentVersion").value(true))
                .andExpect(jsonPath("$.createdBy").value("ops-team"))
                .andExpect(jsonPath("$.watchlistEntries[0].listId").value("OFAC-SDN"))
                .andExpect(jsonPath("$.countryRiskEntries[0].countryCode").value("IRN"))
                .andExpect(jsonPath("$.countryRiskEntries[0].riskLevel").value("HIGH"));
    }

    @Test
    void listAndCurrentRespectActivationSemantics() throws Exception {
        mvc.perform(post("/api/v1/screening-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload(false)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/screening-configs/current"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("activated")));

        mvc.perform(put("/api/v1/screening-configs/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(true));

        mvc.perform(get("/api/v1/screening-configs/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void activateAlreadyCurrentReturns409() throws Exception {
        mvc.perform(post("/api/v1/screening-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload(true)))
                .andExpect(status().isCreated());

        mvc.perform(put("/api/v1/screening-configs/1/activate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already current")));
    }

    @Test
    void duplicateCountryCodeReturns400() throws Exception {
        mvc.perform(post("/api/v1/screening-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "samplingFrequency": 10,
                                  "createdBy": "ops-team",
                                  "activate": false,
                                  "watchlistEntries": [],
                                  "countryRiskEntries": [
                                    {"countryCode": "irn", "countryName": "Iran", "riskLevel": "HIGH"},
                                    {"countryCode": "IRN", "countryName": "Iran 2", "riskLevel": "LOW"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("duplicate countryCode")));
    }

    @Test
    void deleteCurrentVersionReturns409() throws Exception {
        mvc.perform(post("/api/v1/screening-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload(true)))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/v1/screening-configs/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("cannot be deleted")));
    }

    @Test
    void deleteReferencedVersionReturns409() throws Exception {
        mvc.perform(post("/api/v1/screening-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload(false)))
                .andExpect(status().isCreated());

        jdbc.update("""
                insert into screening_record
                (application_id, machine_outcome, final_outcome, callback_status, processing_status,
                 reason_code, config_version, evidence, callback_time, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp, current_timestamp)
                """,
                "APP-1", "CLEAR", "CLEAR", "completed", "FINISHED",
                "RULE_OK", 1, "{}");

        mvc.perform(delete("/api/v1/screening-configs/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("referenced by screening records")));
    }

    @Test
    void deleteNonCurrentUnusedVersionReturns204() throws Exception {
        mvc.perform(post("/api/v1/screening-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload(false)))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/v1/screening-configs/1"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/screening-configs/1"))
                .andExpect(status().isNotFound());
    }

    private String validCreatePayload(boolean activate) {
        return """
                {
                  "samplingFrequency": 10,
                  "createdBy": "ops-team",
                  "activate": %s,
                  "watchlistEntries": [
                    {
                      "listId": "OFAC-SDN",
                      "firstName": "Jane",
                      "lastName": "Doe",
                      "dateOfBirth": "1980-04-12",
                      "nationality": "GB",
                      "listType": "SANCTIONS",
                      "source": "OFAC"
                    }
                  ],
                  "countryRiskEntries": [
                    {
                      "countryCode": "IRN",
                      "countryName": "Iran",
                      "riskLevel": "HIGH"
                    }
                  ]
                }
                """.formatted(activate);
    }
}