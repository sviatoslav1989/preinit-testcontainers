package by.macmonitor.examples.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import by.macmonitor.preinittestcontainers.postgresql.CreatePostgreSQLContainerCommand;
import by.macmonitor.preinittestcontainers.postgresql.PostgreSQLContainerFactory;
import by.macmonitor.preinittestcontainers.support.TimedContainerStart;

import com.github.dockerjava.api.DockerClient;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

class MavenCentralPostgreSQLPreinitTest {

    @Test
    void preinitializedContainerStarts() throws Exception {
        String imageName = null;
        CreatePostgreSQLContainerCommand command = CreatePostgreSQLContainerCommand.builder()
                .withBaseImageName("postgres:17")
                .withPreInitialized(true)
                .withInitScripts(List.of(
                        "postgresql/init.tables.expected.sql", "postgresql/init.data.expected.sql"))
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .build();
        try (PostgreSQLContainer container =
                PostgreSQLContainerFactory.createPostgreSQLContainer(command)) {
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