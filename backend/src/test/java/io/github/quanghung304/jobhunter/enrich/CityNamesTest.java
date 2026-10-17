package io.github.quanghung304.jobhunter.enrich;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CityNamesTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" mentions {1} → {2}")
    @CsvSource(delimiter = ';', nullValues = "NULL", textBlock = """
            Turin, Italy;             Turin;       true
            TORINO (TO);              Turin;       true
            Torino;                   torino;      true
            Munich;                   München;     true
            München, Bayern;          Munich;      true
            Muenchen;                 Munich;      true
            Munich, Germany;          Turin;       false
            Torinostrasse 4, Berlin;  Turin;       false
            Hà Nội, Vietnam;          Hanoi;       true
            Saigon;                   Ho Chi Minh City; true
            Paris, France;            Paris;       true
            NULL;                     Turin;       false
            Turin;                    NULL;        false
            ' ';                      Turin;       false
            """)
    void mentions(String location, String city, boolean expected) {
        assertThat(CityNames.mentions(location, city)).isEqualTo(expected);
    }
}
