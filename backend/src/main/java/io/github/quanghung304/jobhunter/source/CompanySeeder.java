package io.github.quanghung304.jobhunter.source;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.quanghung304.jobhunter.source.persistence.Company;
import io.github.quanghung304.jobhunter.source.persistence.CompanyRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upserts {@code config/companies.yaml} (config-spec.md §4) into {@code company} on startup,
 * keyed by (ats, slug). The check columns ({@code last_check_status}, {@code last_checked_at},
 * {@code not_found_count}) are never overwritten. {@code enabled} follows the file: a board in
 * the file is enabled unless it hit {@value CompanyBoards#MAX_NOT_FOUND} consecutive 404s (a
 * successful check enables it again); a board removed from the file is disabled. A missing file
 * is a warning; an invalid entry fails startup.
 */
@Component
public class CompanySeeder {

    private static final Logger log = LoggerFactory.getLogger(CompanySeeder.class);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);

    private final CompanyRepository companies;
    private final CompaniesProperties properties;

    public CompanySeeder(CompanyRepository companies, CompaniesProperties properties) {
        this.companies = companies;
        this.properties = properties;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void onStartup() {
        seed(properties.file());
    }

    /** @return the number of boards in the file */
    @Transactional
    public int seed(Path file) {
        if (!Files.exists(file)) {
            log.warn("No company list at {}; the ATS collectors have no boards", file.toAbsolutePath().normalize());
            return 0;
        }
        List<Entry> entries = read(file);
        Map<String, Company> existing = new HashMap<>();
        companies.findAll().forEach(c -> existing.put(key(c.getAts(), c.getBoardSlug()), c));
        int created = 0;
        Set<String> inFile = new HashSet<>();
        for (Entry entry : entries) {
            String key = key(entry.ats(), entry.slug().strip());
            inFile.add(key);
            Company company = existing.get(key);
            if (company == null) {
                companies.save(new Company(entry.name().strip(), entry.ats(), entry.slug().strip()));
                created++;
                continue;
            }
            company.setName(entry.name().strip());
            company.setEnabled(company.getNotFoundCount() < CompanyBoards.MAX_NOT_FOUND);
        }
        int removed = 0;
        for (var e : existing.entrySet()) {
            if (!inFile.contains(e.getKey()) && e.getValue().isEnabled()) {
                e.getValue().setEnabled(false);
                removed++;
            }
        }
        log.info("Seeded {} company boards from {} ({} new, {} disabled as removed from the file)", entries.size(),
                file.toAbsolutePath().normalize(), created, removed);
        return entries.size();
    }

    private static List<Entry> read(Path file) {
        CompaniesFile parsed;
        try {
            parsed = YAML.readValue(file.toFile(), CompaniesFile.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file + ": " + e.getMessage(), e);
        }
        List<Entry> entries = parsed == null || parsed.companies() == null ? List.of() : parsed.companies();
        var problems = new ArrayList<String>();
        var keys = new HashSet<String>();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            String at = "companies[" + i + "]";
            if (entry == null) {
                problems.add(at + ": empty entry");
                continue;
            }
            if (entry.name() == null || entry.name().isBlank()) {
                problems.add(at + ".name: must not be blank");
            }
            if (entry.ats() == null) {
                problems.add(at + ".ats: must be one of GREENHOUSE, LEVER, LEVER_EU, ASHBY");
            }
            if (entry.slug() == null || entry.slug().isBlank()) {
                problems.add(at + ".slug: must not be blank");
            } else if (entry.ats() != null && !keys.add(key(entry.ats(), entry.slug().strip()))) {
                problems.add(at + ": duplicate board " + entry.ats() + " " + entry.slug().strip());
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid " + file + ": " + String.join("; ", problems));
        }
        return entries;
    }

    private static String key(AtsType ats, String slug) {
        return ats + ":" + slug.toLowerCase(Locale.ROOT);
    }

    record CompaniesFile(List<Entry> companies) {
    }

    /** One board; {@code notes} is for the owner only. */
    record Entry(String name, AtsType ats, String slug, String notes) {
    }
}
