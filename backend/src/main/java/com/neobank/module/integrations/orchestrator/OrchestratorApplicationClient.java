package com.neobank.module.integrations.orchestrator;

import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Reads an application live from its owner for UC-03; no response is cached or persisted. */
@Component
public class OrchestratorApplicationClient {

    private final RestClient http;
    private final String applicationsUrl;

    public OrchestratorApplicationClient(
            RestClient http,
            @Value("${service.orchestrator-url:http://localhost:9000}") String orchestratorUrl) {
        this.http = http;
        this.applicationsUrl = orchestratorUrl + "/api/v1/applications";
    }

    public Application findById(String applicationId) {
        Application application = http.get()
                .uri(applicationsUrl + "/{applicationId}", applicationId)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        (request, response) -> {
                            throw new NoSuchElementException("no application for " + applicationId);
                        })
                .onStatus(HttpStatusCode::isError,
                        (request, response) -> {
                            throw new ApplicantUnavailableException("applicant service is unavailable");
                        })
                .body(Application.class);
        if (application == null) {
            throw new ApplicantUnavailableException("applicant service returned an empty response");
        }
        return application;
    }
}
