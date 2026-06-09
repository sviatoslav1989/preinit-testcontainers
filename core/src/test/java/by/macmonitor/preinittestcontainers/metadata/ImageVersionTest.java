package by.macmonitor.preinittestcontainers.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageVersionTest {

    @Test
    void comparesNumericSegments() {
        assertThat(ImageVersion.parse("8.0.45").compareTo(ImageVersion.parse("8.0")))
                .isGreaterThan(0);
        assertThat(ImageVersion.parse("17").compareTo(ImageVersion.parse("16"))).isGreaterThan(0);
        assertThat(ImageVersion.parse("26.3.4.11").compareTo(ImageVersion.parse("26.3.4")))
                .isGreaterThan(0);
    }

    @Test
    void latestIsGreaterThanConcreteVersions() {
        assertThat(ImageVersion.parse("latest").compareTo(ImageVersion.parse("99.0")))
                .isGreaterThan(0);
    }
}
