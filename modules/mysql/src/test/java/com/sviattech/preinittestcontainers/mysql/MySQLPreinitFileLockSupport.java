package com.sviattech.preinittestcontainers.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import com.sviattech.preinittestcontainers.endimagename.JdbcEndImageNameCalculator;

import org.testcontainers.containers.MySQLContainer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

final class MySQLPreinitFileLockSupport {

    static final Charset WORKER_TEXT_CHARSET = StandardCharsets.UTF_8;

    static final String LOG_TRY_TO_BUILD = "Try to build local image:";

    static final String LOG_ALREADY_EXISTS = "already exists (built by another process)";

    static final String LOG_SUCCESSFUL_BUILD = "Successful build of local image:";

    private static final List<String> CMD_PARAMETERS = List.of(
            "--character-set-server=utf8",
            "--collation-server=utf8_general_ci",
            "--max_connections=150",
            "--innodb-buffer-pool-size=128M",
            "--innodb-doublewrite=0",
            "--innodb-flush-log-at-trx-commit=0",
            "--log_bin_trust_function_creators=1",
            "--sync-binlog=0");

    private static final List<String> LOCK_TEST_INIT_SCRIPTS =
            List.of("mysql/init.tables.expected.sql", "mysql/init.data.expected.sql");

    private static final Pattern RESULT_SUCCESS_PATTERN =
            Pattern.compile("\"success\"\\s*:\\s*(true|false)");

    private static final Pattern RESULT_END_IMAGE_PATTERN =
            Pattern.compile("\"endImageName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private static final Pattern RESULT_ERROR_PATTERN =
            Pattern.compile("\"errorMessage\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private MySQLPreinitFileLockSupport() {}

    static void assertTablesAndDataCreated(MySQLContainer container) throws Exception {
        DataSource dataSource = DataSourceFactory.createDataSource(container);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'testdb' AND table_name = 'test'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("init script should create table test")
                        .isEqualTo(1);
            }
            try (ResultSet rs = statement.executeQuery("SELECT id FROM test ORDER BY id")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("init script should insert row id=1")
                        .isEqualTo(1);
                assertThat(rs.next())
                        .as("init scripts should define exactly one row in test")
                        .isFalse();
            }
        }
    }

    static CreateMySQLContainerCommand.CreateMySQLContainerCommandBuilder<?, ?>
            basePreinitializedCommand() {
        return CreateMySQLContainerCommand.builder()
                .withBaseImageName("mysql:8.0.45")
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .withCmdParameters(CMD_PARAMETERS);
    }

    static List<String> cmdParameters() {
        return CMD_PARAMETERS;
    }

    static long countLogLinesContaining(List<Path> logFiles, String substring) throws IOException {
        long count = 0;
        for (Path logFile : logFiles) {
            for (String line : Files.readAllLines(logFile, WORKER_TEXT_CHARSET)) {
                if (line.contains(substring)) {
                    count++;
                }
            }
        }
        return count;
    }

    static long countSuccessfulBuildLogs(List<Path> logFiles, String endImageName)
            throws IOException {
        return countLogLinesContaining(logFiles, LOG_SUCCESSFUL_BUILD + " " + endImageName);
    }

    static long countTryToBuildLogs(List<Path> logFiles, String endImageName) throws IOException {
        return countLogLinesContaining(logFiles, LOG_TRY_TO_BUILD + " " + endImageName);
    }

    static String expectedEndImageName() {
        return JdbcEndImageNameCalculator.instance().calculate(lockTestCommand());
    }

    static Path goSignal(Path workDir) {
        return workDir.resolve("go");
    }

    static CreateMySQLContainerCommand lockTestCommand() {
        return basePreinitializedCommand()
                .withInitScripts(LOCK_TEST_INIT_SCRIPTS)
                .build();
    }

    static WorkerResult readResult(Path resultFile) throws IOException {
        return fromJson(Files.readString(resultFile, WORKER_TEXT_CHARSET));
    }

    static Path readyMarker(Path workDir, int workerIndex) {
        return workDir.resolve("ready-" + workerIndex);
    }

    static Path resultFile(Path workDir, int workerIndex) {
        return workDir.resolve("result-" + workerIndex + ".json");
    }

    static Path workerLogFile(Path workDir, int workerIndex) {
        return workDir.resolve("worker-" + workerIndex + ".log");
    }

    static void writeResult(Path resultFile, WorkerResult result) throws IOException {
        Files.writeString(resultFile, toJson(result), WORKER_TEXT_CHARSET);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static WorkerResult fromJson(String json) {
        boolean success = parseBooleanField(json, RESULT_SUCCESS_PATTERN);
        String endImageName = parseStringField(json, RESULT_END_IMAGE_PATTERN);
        String errorMessage = parseStringField(json, RESULT_ERROR_PATTERN);
        return new WorkerResult(success, endImageName, errorMessage);
    }

    private static boolean parseBooleanField(String json, Pattern pattern) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "Missing field in result JSON: " + pattern.pattern());
        }
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static String parseStringField(String json, Pattern pattern) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return unescapeJson(matcher.group(1));
    }

    private static String toJson(WorkerResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\"success\":").append(result.success());
        if (result.endImageName() != null) {
            json.append(",\"endImageName\":\"")
                    .append(escapeJson(result.endImageName()))
                    .append('"');
        }
        if (result.errorMessage() != null) {
            json.append(",\"errorMessage\":\"")
                    .append(escapeJson(result.errorMessage()))
                    .append('"');
        }
        json.append('}');
        return json.toString();
    }

    private static String unescapeJson(String value) {
        StringBuilder unescaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                unescaped.append(next);
                i++;
            } else {
                unescaped.append(ch);
            }
        }
        return unescaped.toString();
    }

    record WorkerResult(boolean success, String endImageName, String errorMessage) {

        static WorkerResult success(String endImageName) {
            return new WorkerResult(true, endImageName, null);
        }

        static WorkerResult failure(String errorMessage) {
            return new WorkerResult(false, null, errorMessage);
        }
    }
}
