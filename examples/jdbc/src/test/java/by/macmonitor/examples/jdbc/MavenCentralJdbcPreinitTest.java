package by.macmonitor.examples.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import by.macmonitor.preinittestcontainers.CreateJdbcContainerCommand;
import by.macmonitor.preinittestcontainers.endimagename.JdbcEndImageNameCalculator;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import org.junit.jupiter.api.Test;

import java.util.List;

class MavenCentralJdbcPreinitTest {

    @Test
    void preinitImageNameIsStableWithInitScripts() {
        TestJdbcContainerCommand command = TestJdbcContainerCommand.builder()
                .withBaseImageName("postgres:17")
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .withInitScripts(
                        List.of("postgresql/init.tables.expected.sql", "postgresql/init.data.expected.sql"))
                .build();

        JdbcEndImageNameCalculator<TestJdbcContainerCommand> calculator =
                JdbcEndImageNameCalculator.instance();
        String first = calculator.calculate(command);
        String second = calculator.calculate(command);

        assertThat(first).isNotBlank();
        assertThat(second).isEqualTo(first);
    }

    @Getter
    @SuperBuilder(toBuilder = true, setterPrefix = "with")
    static class TestJdbcContainerCommand extends CreateJdbcContainerCommand {}
}