package by.macmonitor.examples.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import by.macmonitor.preinittestcontainers.mysql.CreateMySQLContainerCommand;
import by.macmonitor.preinittestcontainers.mysql.MySQLContainerFactory;
import by.macmonitor.preinittestcontainers.support.TimedContainerStart;

import com.github.dockerjava.api.DockerClient;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

class MavenCentralMySQLPreinitTest {

    @Test
    void preinitializedContainerStarts() throws Exception {
        String imageName = null;
        CreateMySQLContainerCommand command = CreateMySQLContainerCommand.builder()
                .withBaseImageName("mysql:8.0.45")
                .withInitScripts(
                        List.of("mysql/init.tables.expected.sql", "mysql/init.data.expected.sql"))
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .build();
        try (MySQLContainer container = MySQLContainerFactory.createMySQLContainer(command)) {
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