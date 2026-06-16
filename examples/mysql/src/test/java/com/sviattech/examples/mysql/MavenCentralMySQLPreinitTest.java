package com.sviattech.examples.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import com.sviattech.preinittestcontainers.mysql.CreateMySQLContainerCommand;
import com.sviattech.preinittestcontainers.mysql.MySQLContainerFactory;
import com.sviattech.preinittestcontainers.support.TimedContainerStart;

import com.github.dockerjava.api.DockerClient;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

class MavenCentralMySQLPreinitTest {

    private static final List<String> CMD_PARAMETERS = List.of(
            "--character-set-server=utf8",
            "--collation-server=utf8_general_ci",
            "--max_connections=150",
            "--innodb-buffer-pool-size=128M",
            "--innodb-doublewrite=0",
            "--innodb-flush-log-at-trx-commit=0",
            "--log_bin_trust_function_creators=1",
            "--sync-binlog=0");

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
                .withCmdParameters(CMD_PARAMETERS)
                .build();
        try (MySQLContainer container = MySQLContainerFactory.createMySQLContainer(command)) {
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