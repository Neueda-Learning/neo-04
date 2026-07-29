package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.orchestrator.ApplicantUnavailableException;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorApplicationClient;
import com.neobank.module.integrations.orchestrator.OrchestratorApplicationSearchClient;
import com.neobank.module.repository.ScreeningRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CasesServiceTest {

    private ScreeningRecordRepository cases;
    private OrchestratorApplicationClient applicationClient;
    private CasesService service;

    @BeforeEach
    void setUp() {
        cases = mock(ScreeningRecordRepository.class);
        applicationClient = mock(OrchestratorApplicationClient.class);
        service = new CasesService(cases, mock(OrchestratorApplicationSearchClient.class), applicationClient);
    }

    @Test
    void applicantIsFetchedLiveAndReducedToTheUc03View() {
        Application application = new Application(
                "app-1360", "WEB", "2026-07-15T08:00:00Z",
                new Application.Applicant("Elena Petrova", "1991-02-03", null, null,
                        "RU", "BY", null, null, null, null, null),
                null, null, null, null, null, null);
        when(applicationClient.findById("app-1360")).thenReturn(application);

        var result = service.getApplicant("app-1360");

        assertThat(result.fullName()).isEqualTo("Elena Petrova");
        assertThat(result.dateOfBirth()).isEqualTo("1991-02-03");
        assertThat(result.countryOfResidence()).isEqualTo("BY");
        assertThat(result.nationality()).isEqualTo("RU");
        assertThat(result.channel()).isEqualTo("WEB");
        verify(applicationClient).findById("app-1360");
    }

    @Test
    void orchestratorFailureRemainsRetryableAndDoesNotTouchTheCaseDatabase() {
        when(applicationClient.findById("app-1360"))
                .thenThrow(new ApplicantUnavailableException("applicant service is unavailable"));

        assertThatThrownBy(() -> service.getApplicant("app-1360"))
                .isInstanceOf(ApplicantUnavailableException.class)
                .hasMessage("applicant service is unavailable");
        verify(applicationClient).findById("app-1360");
        org.mockito.Mockito.verifyNoInteractions(cases);
    }
}
