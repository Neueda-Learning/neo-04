package com.neobank.module.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.neobank.module.model.CallbackStatus;
import com.neobank.module.model.ProcessingStatus;
import com.neobank.module.model.ScreeningOutcome;
import com.neobank.module.model.ScreeningRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * INTEGRATION TEST (name ends in {@code IT} → runs on {@code ./mvnw verify}, needs Docker).
 *
 * <p>Testcontainers boots a real MySQL 8.4, Liquibase creates {@code screening_record} (change set
 * 002) and drops {@code demo_showcase} (change set 003) on it, and Hibernate runs
 * {@code ddl-auto=validate} against that real DDL. Replaces {@code DemoShowcaseRepositoryIT} now
 * that this is the module's real table.</p>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional // roll back each test so methods don't leak rows into one another
class ScreeningRecordRepositoryIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("neo_04");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    ScreeningRecordRepository screeningRecords;

    @Test
    void schemaValidatesAndStartsEmpty() {
        // Reaching here proves Liquibase applied 002/003 and ddl-auto=validate passed on real MySQL.
        assertThat(screeningRecords.findAll()).isEmpty();
    }

    @Test
    void aRowRoundTripsThroughRealMysqlInProgress() {
        ScreeningRecord saved = screeningRecords.saveAndFlush(new ScreeningRecord("APP-1"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();   // @PrePersist ran

        ScreeningRecord reloaded = screeningRecords.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getApplicationId()).isEqualTo("APP-1");
        assertThat(reloaded.getMachineOutcome()).isEqualTo(ScreeningOutcome.PENDING.name());
        assertThat(reloaded.getFinalOutcome()).isEqualTo(ScreeningOutcome.PENDING.name());
        assertThat(reloaded.getProcessingStatus()).isEqualTo(ProcessingStatus.IN_PROGRESS.name());
        assertThat(reloaded.getCallbackStatus()).isEqualTo(CallbackStatus.PENDING.name());
    }

    @Test
    void theUniqueConstraintOnApplicationIdRejectsADuplicateRow() {
        screeningRecords.saveAndFlush(new ScreeningRecord("APP-DUP"));

        assertThat(
                org.assertj.core.api.Assertions.catchThrowable(
                        () -> screeningRecords.saveAndFlush(new ScreeningRecord("APP-DUP"))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void theBoardOrdersNewestFirst() {
        screeningRecords.saveAndFlush(new ScreeningRecord("APP-OLD"));
        screeningRecords.saveAndFlush(new ScreeningRecord("APP-NEW"));

        assertThat(screeningRecords.findAllByOrderByCreatedAtDescIdDesc())
                .extracting(ScreeningRecord::getApplicationId)
                .containsExactly("APP-NEW", "APP-OLD");
    }
}
