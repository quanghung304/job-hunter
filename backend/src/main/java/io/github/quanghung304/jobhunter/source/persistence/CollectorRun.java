package io.github.quanghung304.jobhunter.source.persistence;

import io.github.quanghung304.jobhunter.source.CollectStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One run of one collector (source module).
 */
@Entity
@Table(name = "collector_run")
public class CollectorRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_code", nullable = false, updatable = false)
    private String sourceCode;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private CollectStatus status;

    @Column(name = "fetched_count", nullable = false)
    private int fetchedCount;

    @Column(name = "new_raw_count", nullable = false)
    private int newRawCount;

    @Column(name = "new_job_count", nullable = false)
    private int newJobCount;

    @Column(name = "error_message")
    private String errorMessage;

    protected CollectorRun() {
        // for JPA
    }

    public CollectorRun(String sourceCode, Instant startedAt) {
        this.sourceCode = sourceCode;
        this.startedAt = startedAt;
    }

    public Long getId() {
        return id;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public CollectStatus getStatus() {
        return status;
    }

    public void setStatus(CollectStatus status) {
        this.status = status;
    }

    public int getFetchedCount() {
        return fetchedCount;
    }

    public void setFetchedCount(int fetchedCount) {
        this.fetchedCount = fetchedCount;
    }

    public int getNewRawCount() {
        return newRawCount;
    }

    public void setNewRawCount(int newRawCount) {
        this.newRawCount = newRawCount;
    }

    public int getNewJobCount() {
        return newJobCount;
    }

    public void setNewJobCount(int newJobCount) {
        this.newJobCount = newJobCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
