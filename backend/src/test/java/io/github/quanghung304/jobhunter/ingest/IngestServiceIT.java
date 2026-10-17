package io.github.quanghung304.jobhunter.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.common.Hashing;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.ingest.persistence.Job;
import io.github.quanghung304.jobhunter.ingest.persistence.JobRepository;
import io.github.quanghung304.jobhunter.ingest.persistence.RawPosting;
import io.github.quanghung304.jobhunter.ingest.persistence.RawPostingRepository;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@PostgresJpaTest
@Import({IngestService.class, IngestServiceIT.ClockConfig.class})
@RecordApplicationEvents
class IngestServiceIT {

    static final Instant T0 = Instant.parse("2026-10-17T07:00:00Z");

    @Autowired
    IngestService ingest;

    @Autowired
    JobRepository jobs;

    @Autowired
    RawPostingRepository raws;

    @Autowired
    TestEntityManager em;

    @Autowired
    MutableClock clock;

    @Autowired
    ApplicationEvents events;

    @BeforeEach
    void resetClock() {
        clock.set(T0);
    }

    @Test
    void samePostingTwiceIsOneRawAndOneJob() {
        var posting = posting("arbeitnow", "a-1").build();

        IngestResult first = ingestAndClear(posting);
        clock.set(T0.plus(Duration.ofHours(12)));
        IngestResult second = ingestAndClear(posting);

        assertThat(first).isEqualTo(new IngestResult(1, 1, 0, 0));
        assertThat(second).isEqualTo(new IngestResult(0, 0, 0, 0));
        assertThat(raws.count()).isEqualTo(1);
        assertThat(jobs.count()).isEqualTo(1);
        RawPosting raw = raws.findAll().get(0);
        assertThat(raw.getFirstSeenAt()).isEqualTo(T0);
        assertThat(raw.getLastSeenAt()).isEqualTo(T0.plus(Duration.ofHours(12)));
        Job job = jobs.findAll().get(0);
        assertThat(job.getLastSeenAt()).isEqualTo(T0.plus(Duration.ofHours(12)));
        assertThat(raw.getJobId()).isEqualTo(job.getId());
    }

    @Test
    void newJobGetsNormalizedKeysAndHashes() {
        var posting = posting("arbeitnow", "a-1").company("Acme, Inc.").title("Java Developer (m/f/d)").build();

        ingestAndClear(posting);

        Job job = jobs.findAll().get(0);
        assertThat(job.getCompanyName()).isEqualTo("Acme, Inc.");
        assertThat(job.getCompanyKey()).isEqualTo("acme");
        assertThat(job.getFingerprint()).isEqualTo(Fingerprint.of("acme", "java developer"));
        assertThat(job.getDescriptionHash()).isEqualTo(Hashing.sha256Hex(posting.descriptionText()));
        assertThat(job.getFirstSeenAt()).isEqualTo(T0);
        assertThat(job.getPostedAt()).isEqualTo(posting.postedAt());
        assertThat(job.getTags()).containsExactly("java");
        assertThat(job.getApplyUrl()).isEqualTo(posting.url());
        assertThat(events.stream(JobDescriptionChanged.class)).isEmpty();
    }

    @Test
    void sameJobFromTwoSourcesIsMergedByTheRules() {
        var board = posting("arbeitnow", "a-1").company("Acme, Inc.").title("Senior Backend Engineer (Remote) - m/f/d")
                .description("Short text.").postedAt(T0.minus(Duration.ofDays(1)))
                .url("https://www.arbeitnow.com/jobs/acme/a-1").tags("java", "spring").build();
        var ats = posting("greenhouse", "gh-9").company("ACME Inc").title("Senior Backend Engineer")
                .description("A much longer description with all the details of the role.")
                .postedAt(T0.minus(Duration.ofDays(3))).url("https://boards.greenhouse.io/acme/jobs/9")
                .salary("60000", "80000", "EUR", SalaryPeriod.YEAR).tags("Spring", "kubernetes").build();

        ingestAndClear(board);
        IngestResult second = ingestAndClear(ats);

        assertThat(second).isEqualTo(new IngestResult(1, 0, 1, 0));
        assertThat(raws.count()).isEqualTo(2);
        assertThat(jobs.count()).isEqualTo(1);
        Job job = jobs.findAll().get(0);
        assertThat(job.getTitle()).isEqualTo("Senior Backend Engineer (Remote) - m/f/d"); // first form kept
        assertThat(job.getDescriptionText()).isEqualTo(ats.descriptionText());           // longest
        assertThat(job.getDescriptionHash()).isEqualTo(Hashing.sha256Hex(ats.descriptionText()));
        assertThat(job.getPostedAt()).isEqualTo(T0.minus(Duration.ofDays(3)));          // earliest
        assertThat(job.getSalaryMin()).isEqualByComparingTo("60000");                    // structured salary
        assertThat(job.getSalaryPeriod()).isEqualTo(SalaryPeriod.YEAR);
        assertThat(job.getApplyUrl()).isEqualTo("https://boards.greenhouse.io/acme/jobs/9"); // ATS link
        assertThat(job.getTags()).containsExactly("java", "spring", "kubernetes");
        assertThat(raws.findByJobId(job.getId())).hasSize(2);
        assertThat(events.stream(JobDescriptionChanged.class)).containsExactly(new JobDescriptionChanged(job.getId()));
    }

    @Test
    void atsLinkIsNotReplacedByABoardLinkAndSalaryIsNotOverwritten() {
        var ats = posting("lever", "l-1").url("https://jobs.lever.co/acme/1").salary("50", null, "EUR", SalaryPeriod.HOUR)
                .description("Long enough description from the ATS.").build();
        var board = posting("arbeitnow", "a-1").url("https://www.arbeitnow.com/jobs/acme/a-1")
                .salary("90000", "99000", "USD", SalaryPeriod.YEAR).description("Short.").build();

        ingestAndClear(ats);
        IngestResult second = ingestAndClear(board);

        Job job = jobs.findAll().get(0);
        assertThat(job.getApplyUrl()).isEqualTo("https://jobs.lever.co/acme/1");
        assertThat(job.getSalaryMin()).isEqualByComparingTo("50");
        assertThat(job.getSalaryCurrency()).isEqualTo("EUR");
        assertThat(job.getDescriptionText()).isEqualTo("Long enough description from the ATS.");
        assertThat(second.updatedJobs()).isZero();
        assertThat(events.stream(JobDescriptionChanged.class)).isEmpty();
    }

    @Test
    void longerDescriptionFromTheSameSourcePublishesAnEvent() {
        ingestAndClear(posting("arbeitnow", "a-1").description("Version one.").build());
        var edited = posting("arbeitnow", "a-1").description("Version two, now longer.").payload("v2").build();

        IngestResult second = ingestAndClear(edited);

        Job job = jobs.findAll().get(0);
        assertThat(second).isEqualTo(new IngestResult(0, 0, 1, 0));
        assertThat(job.getDescriptionText()).isEqualTo("Version two, now longer.");
        assertThat(events.stream(JobDescriptionChanged.class)).containsExactly(new JobDescriptionChanged(job.getId()));
        RawPosting raw = raws.findAll().get(0);
        assertThat(raw.getPayload().get("v").asText()).isEqualTo("v2");
        // Hash of the payload as received (jsonb may reorder keys when stored).
        assertThat(raw.getPayloadHash()).isEqualTo(Hashing.sha256Hex(edited.rawPayload().toString()));
    }

    @Test
    void jobUnseenForMoreThan60DaysIsARepostingWithASuffixedFingerprint() {
        ingestAndClear(posting("arbeitnow", "a-1").build());
        Instant later = T0.plus(Duration.ofDays(61));
        clock.set(later);

        IngestResult repost = ingestAndClear(posting("arbeitnow", "a-2").build());
        clock.set(later.plus(Duration.ofDays(1)));
        IngestResult again = ingestAndClear(posting("himalayas", "h-1").build());

        assertThat(repost).isEqualTo(new IngestResult(1, 1, 0, 0));
        assertThat(again).isEqualTo(new IngestResult(1, 0, 0, 0));
        String fingerprint = Fingerprint.ofPosting("Acme", "Java Developer");
        assertThat(jobs.findAll()).extracting(Job::getFingerprint)
                .containsExactlyInAnyOrder(fingerprint, fingerprint + "#20261217");
        Job newest = jobs.findByFingerprint(fingerprint + "#20261217").orElseThrow();
        assertThat(raws.findByJobId(newest.getId())).hasSize(2);
    }

    @Test
    void jobSeenWithin60DaysIsMerged() {
        ingestAndClear(posting("arbeitnow", "a-1").build());
        clock.set(T0.plus(Duration.ofDays(59)));

        IngestResult result = ingestAndClear(posting("himalayas", "h-1").build());

        assertThat(result.newJobs()).isZero();
        assertThat(jobs.count()).isEqualTo(1);
    }

    @Test
    void expiredJobSeenAgainIsActive() {
        ingestAndClear(posting("arbeitnow", "a-1").build());
        Job job = jobs.findAll().get(0);
        job.setStatus(JobStatus.EXPIRED);
        jobs.saveAndFlush(job);
        em.clear();

        ingestAndClear(posting("arbeitnow", "a-1").build());

        assertThat(jobs.findAll().get(0).getStatus()).isEqualTo(JobStatus.ACTIVE);
    }

    @Test
    void invalidPostingsAreSkipped() {
        var postings = List.of(
                posting("arbeitnow", "x-1").title(" ").build(),
                posting("arbeitnow", "x-2").company("").build(),
                posting("arbeitnow", "x-3").url(null).build(),
                posting("arbeitnow", "x-4").description("  ").build(),
                posting("arbeitnow", "ok").build());

        IngestResult result = ingestAndClear(postings.toArray(NormalizedPosting[]::new));

        assertThat(result).isEqualTo(new IngestResult(1, 1, 0, 4));
        assertThat(raws.findAll()).extracting(RawPosting::getExternalId).containsExactly("ok");
    }

    @Test
    void missingExternalIdUsesTheHashOfTheUrl() {
        var posting = posting("weworkremotely", null).url("https://weworkremotely.com/remote-jobs/acme-java").build();

        ingestAndClear(posting);
        ingestAndClear(posting);

        assertThat(raws.findAll()).singleElement().extracting(RawPosting::getExternalId)
                .isEqualTo(Hashing.sha256Hex("https://weworkremotely.com/remote-jobs/acme-java"));
    }

    @Test
    void missingPostedAtUsesNow() {
        ingestAndClear(posting("arbeitnow", "a-1").postedAt(null).build());

        assertThat(jobs.findAll().get(0).getPostedAt()).isEqualTo(T0);
    }

    @Test
    void longDescriptionIsTruncatedTo30000Characters() {
        ingestAndClear(posting("arbeitnow", "a-1").description("x".repeat(35_000)).build());

        assertThat(jobs.findAll().get(0).getDescriptionText()).hasSize(30_000);
    }

    @Test
    void manyPostingsAcrossBatchesAndDuplicatesWithinOneCall() {
        List<NormalizedPosting> postings = new ArrayList<>();
        IntStream.range(0, 120).forEach(i -> postings.add(
                posting("arbeitnow", "a-" + i).title("Java Developer " + i).build()));
        // the same job from a second source, inside the same call and in a later batch
        postings.add(posting("himalayas", "h-0").title("Java Developer 0 (m/f/d)")
                .description("A longer description for job zero from Himalayas.").build());

        IngestResult result = ingestAndClear(postings.toArray(NormalizedPosting[]::new));

        assertThat(result).isEqualTo(new IngestResult(121, 120, 0, 0));
        assertThat(jobs.count()).isEqualTo(120);
        assertThat(raws.count()).isEqualTo(121);
    }

    private IngestResult ingestAndClear(NormalizedPosting... postings) {
        IngestResult result = ingest.ingest(List.of(postings));
        em.flush();
        em.clear();
        return result;
    }

    private static PostingBuilder posting(String source, String externalId) {
        return new PostingBuilder(source, externalId);
    }

    /** Defaults describe "Java Developer" at "Acme", posted one day before T0. */
    static final class PostingBuilder {
        private final String source;
        private final String externalId;
        private String company = "Acme";
        private String title = "Java Developer";
        private String description = "Build services with Java and Spring Boot.";
        private String url;
        private Instant postedAt = T0.minus(Duration.ofDays(1));
        private BigDecimal salaryMin;
        private BigDecimal salaryMax;
        private String currency;
        private SalaryPeriod period;
        private List<String> tags = List.of("java");
        private String payloadVersion = "v1";

        PostingBuilder(String source, String externalId) {
            this.source = source;
            this.externalId = externalId;
            this.url = "https://example.com/" + source + "/" + externalId;
        }

        PostingBuilder company(String value) {
            company = value;
            return this;
        }

        PostingBuilder title(String value) {
            title = value;
            return this;
        }

        PostingBuilder description(String value) {
            description = value;
            return this;
        }

        PostingBuilder url(String value) {
            url = value;
            return this;
        }

        PostingBuilder postedAt(Instant value) {
            postedAt = value;
            return this;
        }

        PostingBuilder salary(String min, String max, String cur, SalaryPeriod per) {
            salaryMin = min == null ? null : new BigDecimal(min);
            salaryMax = max == null ? null : new BigDecimal(max);
            currency = cur;
            period = per;
            return this;
        }

        PostingBuilder tags(String... values) {
            tags = List.of(values).stream().map(String::toLowerCase).toList();
            return this;
        }

        PostingBuilder payload(String version) {
            payloadVersion = version;
            return this;
        }

        NormalizedPosting build() {
            JsonNode payload = JsonNodeFactory.instance.objectNode().put("id", String.valueOf(externalId)).put("v", payloadVersion);
            return new NormalizedPosting(source, externalId, url, title, company, "Berlin", RemoteHint.REMOTE,
                    "Full Time", description, null, salaryMin, salaryMax, currency, period, tags, postedAt, payload);
        }
    }

    /** A Clock the test can move; replaces the system clock in this slice. */
    static final class MutableClock extends Clock {
        private volatile Instant now = T0;

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {
        @Bean
        MutableClock clock() {
            return new MutableClock();
        }
    }
}
