package io.github.quanghung304.jobhunter.source.persistence;

import io.github.quanghung304.jobhunter.source.AtsType;
import io.github.quanghung304.jobhunter.source.CompanyCheckStatus;
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
 * A company job board on an ATS; seeded from companies.yaml.
 */
@Entity
@Table(name = "company")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "ats", nullable = false, updatable = false)
    private AtsType ats;

    @Column(name = "board_slug", nullable = false, updatable = false)
    private String boardSlug;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_check_status")
    private CompanyCheckStatus lastCheckStatus;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    protected Company() {
        // for JPA
    }

    public Company(String name, AtsType ats, String boardSlug) {
        this.name = name;
        this.ats = ats;
        this.boardSlug = boardSlug;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AtsType getAts() {
        return ats;
    }

    public String getBoardSlug() {
        return boardSlug;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public CompanyCheckStatus getLastCheckStatus() {
        return lastCheckStatus;
    }

    public void setLastCheckStatus(CompanyCheckStatus lastCheckStatus) {
        this.lastCheckStatus = lastCheckStatus;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }
}
