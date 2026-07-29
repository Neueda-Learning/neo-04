package com.neobank.module.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.integrations.orchestrator.ApplicantUnavailableException;
import com.neobank.module.service.AnalystQueueService;
import com.neobank.module.service.CasesService;
import com.neobank.module.service.OverrideCaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;

@WebMvcTest(CasesController.class)
class CasesControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CasesService cases;

    @MockBean
    private AnalystQueueService queue;

    @MockBean
    private OverrideCaseService overrides;

    @Test
    void returnsTheLiveApplicantProjection() throws Exception {
        when(cases.getApplicant("app-1360"))
                .thenReturn(new ApplicantView("Elena Petrova", "1991-02-03", "BY", "RU", "WEB"));

        mvc.perform(get("/api/v1/cases/app-1360/applicant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Elena Petrova"))
                .andExpect(jsonPath("$.dateOfBirth").value("1991-02-03"))
                .andExpect(jsonPath("$.countryOfResidence").value("BY"))
                .andExpect(jsonPath("$.nationality").value("RU"))
                .andExpect(jsonPath("$.channel").value("WEB"));
    }

    @Test
    void orchestratorFailureIsAReadableServiceUnavailableResponse() throws Exception {
        when(cases.getApplicant("app-1360"))
                .thenThrow(new ApplicantUnavailableException("applicant service is unavailable"));

        mvc.perform(get("/api/v1/cases/app-1360/applicant"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("applicant service is unavailable"));
    }
}
