package com.sviattech.examples.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import com.sviattech.preinittestcontainers.mariadb.CreateMariaDBContainerCommand;
import com.sviattech.preinittestcontainers.mariadb.MariaDBContainerFactory;
import com.sviattech.preinittestcontainers.support.TimedContainerStart;

import com.github.dockerjava.api.DockerClient;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mariadb.MariaDBContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

class MavenCentralMariaDBPreinitTest {

    private static final List<String> CMD_PARAMETERS = List.of(
            "--character-set-server=utf8",
            "--collation-server=utf8_general_ci",
            "--max_connections=150",
            "--innodb-buffer-pool-size=128M",
            "--innodb-doublewrite=0",
            "--innodb-flush-log-at-trx-commit=0",
            "--innodb-flush-neighbors=0",
            "--innodb-fast-shutdown=1",
            "--innodb-log-file-size=128M",
            "--skip-name-resolve");

    @Test
    void preinitializedContainerStarts() throws Exception {
        String imageName = null;
        CreateMariaDBContainerCommand command = CreateMariaDBContainerCommand.builder()
                .withBaseImageName("mariadb:11.4.5")
                .withInitScripts(
                        List.of("mariadb/init.tables.expected.sql", "mariadb/init.data.expected.sql"))
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .withCmdParameters(CMD_PARAMETERS)
                .build();
        try (MariaDBContainer container = MariaDBContainerFactory.createMariaDBContainer(command)) {
            TimedContainerStart.start(container);
            imageName = container.getDockerImageName();
            try (Connection connection = DriverManager.getConnection(
                            container.getJdbcUrl(), "user", "password");
                    Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery("SELECT id FROM test")) {
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
