package io.github.quanghung304.jobhunter.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JobMergerTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(nullValues = "-", textBlock = """
            https://boards.greenhouse.io/acme/jobs/1,         true
            https://job-boards.greenhouse.io/acme/jobs/1,     true
            https://jobs.lever.co/acme/123,                   true
            https://jobs.eu.lever.co/acme/123,                true
            https://jobs.ashbyhq.com/acme/123,                true
            https://www.arbeitnow.com/jobs/companies/acme/x,  false
            https://notgreenhouse.io/x,                       false
            https://greenhouse.io.evil.com/x,                 false
            not a url,                                        false
            -,                                                false
            """)
    void recognizesAtsApplyLinks(String url, boolean expected) {
        assertThat(JobMerger.isAts(url)).isEqualTo(expected);
    }
}
