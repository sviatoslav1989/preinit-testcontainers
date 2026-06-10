package by.macmonitor.examples.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;

import by.macmonitor.preinittestcontainers.clickhouse.ClickHouseContainerFactory;
import by.macmonitor.preinittestcontainers.clickhouse.CreateClickHouseContainerCommand;
import by.macmonitor.preinittestcontainers.support.TimedContainerStart;

import com.github.dockerjava.api.DockerClient;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.clickhouse.ClickHouseContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

class MavenCentralClickHousePreinitTest {

    @Test
    void preinitializedContainerStarts() throws Exception {
        String imageName = null;
        CreateClickHouseContainerCommand command = CreateClickHouseContainerCommand.builder()
                .withBaseImageName("clickhouse/clickhouse-server:26.3.4.11")
                .withInitScripts(List.of(
                        "clickhouse/init.tables.expected.sql", "clickhouse/init.data.expected.sql"))
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .build();
        try (ClickHouseContainer container =
                ClickHouseContainerFactory.createClickHouseContainer(command)) {
            TimedContainerStart.start(container);
            imageName = container.getDockerImageName();
            try (Connection connection = DriverManager.getConnection(
                            container.getJdbcUrl(), "user", "password");
                    Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery("SELECT 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        } finally {
            if (imageName != null) {
                DockerClient dockerClient = DockerClientFactory.lazyClient();
                dockerClient.removeImageCmd(imageName).exec();
            }
        }
    }
}