package io.github.quanghung304.jobhunter.source.himalayas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.quanghung304.jobhunter.Fixtures;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HimalayasMapperTest {

    @ParameterizedTest(name = "{0}[{1}]")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            page1.json     | 0 | freebalance/jobs/r-d-global-manager            | FreeBalance                | Poland        | Full Time  | -      | -      | -   | -     | UTC+1                                                     | 1792249654
            page1.json     | 1 | bright-vision-technologies/jobs/sap-abap-programmer-9135717819 | Bright Vision Technologies | Jordan | Full Time | 100000 | 150000 | USD | YEAR  | UTC+3                                    | 1792249618
            page1.json     | 2 | tripleten/jobs/capacitacion-de-consultor-de-ventas-educativas | TripleTen | Colombia      | Full Time  | 1200   | 3500   | USD | MONTH | UTC-5                                                     | 1792249446
            page1.json     | 3 | scharfsinn-recruiting/jobs/facebook-werbeanzeigen-betreuung-m-w-d-100-homeoffice-teilzeit-25-30-h | Scharfsinn Recruiting | Austria | Full Time | 1287.13 | 1544.56 | EUR | MONTH | UTC+1 | 1792249348
            page1.json     | 4 | patriot-software/jobs/qa-analyst-ii            | Patriot Software           | United States | Full Time  | 65000  | 78000  | USD | YEAR  | UTC-10, UTC-9, UTC-8, UTC-7, UTC-6, UTC-5, UTC+14         | 1792249285
            page2.json     | 0 | welocalize/jobs/korean-culture-expert          | Welocalize                 | South Korea   | Contractor | 35     | 35     | USD | HOUR  | UTC+9                                                     | 1792249185
            page2.json     | 1 | veriff/jobs/senior-software-engineer-verification-platform | Veriff | Spain        | Full Time  | -      | -      | -   | -     | UTC+0, UTC+1                                              | 1792249089
            page2.json     | 2 | snapscale/jobs/promotional-products-coordinator-client-presentations-order-management-in | Snapscale | India | Full Time | - | - | - | - | UTC+5:30                                  | 1792249074
            last-page.json | 0 | gravity9/jobs/java-developer                   | gravity9                   | Worldwide     | Full Time  | -      | -      | -   | -     | none                                                      | 1788033271
            last-page.json | 1 | pontoonglobal/jobs/java-developer-2666863949   | Pontoonglobal              | Worldwide     | Full Time  | 700000 | 1500000 | INR | YEAR | none                                                      | 1791827368
            last-page.json | 2 | rekluti/jobs/desarrollador-backend-java-db2-oracle | XKTalent Inc. - Rimutee | Worldwide     | Contractor | -      | -      | -   | -     | none                                                      | 1791258632
            """)
    void mapsEachFixtureItem(String file, int index, String path, String company, String location, String employment,
            BigDecimal min, BigDecimal max, String currency, SalaryPeriod period, String timezones, long pubDate) {
        JsonNode raw = Fixtures.json("himalayas/" + file).get("jobs").get(index);

        NormalizedPosting posting = HimalayasMapper.toPosting(dto(raw), raw);

        String url = "https://himalayas.app/companies/" + path;
        assertThat(posting.sourceCode()).isEqualTo("himalayas");
        assertThat(posting.externalId()).isEqualTo(url);
        assertThat(posting.url()).isEqualTo(url);
        assertThat(posting.applyUrl()).isEqualTo(raw.get("applicationLink").asText());
        assertThat(posting.companyName()).isEqualTo(company);
        assertThat(posting.title()).isEqualTo(raw.get("title").asText().strip());
        assertThat(posting.locationText()).isEqualTo(location);
        assertThat(posting.remoteHint()).isEqualTo(RemoteHint.REMOTE);
        assertThat(posting.employmentHint()).isEqualTo(employment);
        if (min == null) {
            assertThat(posting.salaryMin()).isNull();
        } else {
            assertThat(posting.salaryMin()).isEqualByComparingTo(min);
            assertThat(posting.salaryMax()).isEqualByComparingTo(max);
        }
        assertThat(posting.salaryCurrency()).isEqualTo(currency);
        assertThat(posting.salaryPeriod()).isEqualTo(period);
        assertThat(posting.postedAt()).isEqualTo(Instant.ofEpochSecond(pubDate));
        assertThat(posting.descriptionText())
                .doesNotContain("<p>", "<li>", "</", "&amp;", "&nbsp;")
                .contains("\nTimezone restrictions: " + timezones)
                .contains("Location restrictions: " + ("Worldwide".equals(location) ? "none listed" : location));
        assertThat(posting.rawPayload()).isSameAs(raw);
    }

    @Test
    void tagsAreCategoriesThenSeniorityInLowerCase() {
        JsonNode raw = Fixtures.json("himalayas/page2.json").get("jobs").get(1);

        NormalizedPosting posting = HimalayasMapper.toPosting(dto(raw), raw);

        assertThat(posting.tags()).startsWith("software engineer", "backend engineer", "platform engineer")
                .endsWith("verification engineer", "senior")
                .doesNotHaveDuplicates()
                .allMatch(tag -> tag.equals(tag.toLowerCase()) && !tag.contains("-"));
    }

    @Test
    void currencyWithoutAmountsIsDropped() {
        JsonNode raw = Fixtures.json("himalayas/last-page.json").get("jobs").get(2); // "currency": "USD", no amounts

        NormalizedPosting posting = HimalayasMapper.toPosting(dto(raw), raw);

        assertThat(raw.get("currency").asText()).isEqualTo("USD");
        assertThat(posting.salaryCurrency()).isNull();
        assertThat(posting.descriptionText()).doesNotContain("Salary:");
    }

    @Test
    void weeklySalaryIsKeptAsTextOnly() {
        var raw = (ObjectNode) Fixtures.json("himalayas/page2.json").get("jobs").get(0).deepCopy();
        raw.put("minSalary", 1000).put("maxSalary", 1500).put("salaryPeriod", "weekly");

        NormalizedPosting posting = HimalayasMapper.toPosting(dto(raw), raw);

        assertThat(posting.salaryMin()).isNull();
        assertThat(posting.salaryPeriod()).isNull();
        assertThat(posting.descriptionText()).endsWith("\nSalary: 1000 - 1500 USD (weekly)");
    }

    @Test
    void emptyTimezoneListMeansNone() {
        var raw = (ObjectNode) Fixtures.json("himalayas/page1.json").get("jobs").get(0).deepCopy();
        raw.putArray("timezoneRestrictions");

        assertThat(HimalayasMapper.toPosting(dto(raw), raw).descriptionText()).endsWith("Timezone restrictions: none");
    }

    @Test
    void missingApplicationLinkFallsBackToTheJobPage() {
        var raw = (ObjectNode) Fixtures.json("himalayas/page1.json").get("jobs").get(0).deepCopy();
        raw.remove("applicationLink");

        NormalizedPosting posting = HimalayasMapper.toPosting(dto(raw), raw);

        assertThat(posting.applyUrl()).isEqualTo(posting.url());
    }

    @Test
    void jobWithoutGuidIsRejected() {
        var raw = (ObjectNode) Fixtures.json("himalayas/page1.json").get("jobs").get(0).deepCopy();
        raw.remove("guid");

        assertThatThrownBy(() -> HimalayasMapper.toPosting(dto(raw), raw))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("missing guid");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"1, UTC+1", "-3, UTC-3", "0, UTC+0", "5.5, UTC+5:30", "-9.5, UTC-9:30", "5.75, UTC+5:45", "14, UTC+14"})
    void formatsUtcOffsets(BigDecimal hours, String expected) {
        assertThat(HimalayasMapper.utcOffset(hours)).isEqualTo(expected);
    }

    private static HimalayasJobDto dto(JsonNode raw) {
        try {
            return Fixtures.JSON.treeToValue(raw, HimalayasJobDto.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
