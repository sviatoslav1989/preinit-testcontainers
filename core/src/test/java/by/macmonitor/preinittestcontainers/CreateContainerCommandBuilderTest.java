package by.macmonitor.preinittestcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import by.macmonitor.preinittestcontainers.redis.CreateRedisContainerCommand;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

class CreateContainerCommandBuilderTest {

    @Test
    void waitingFor_setsWaitStrategy() {
        WaitStrategy waitStrategy = Wait.forListeningPort();
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .waitingFor(waitStrategy)
                .build();

        assertThat(command.getWaitStrategy()).isSameAs(waitStrategy);
    }

    @Test
    void withCommand_setsCmdParameters() {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withCommand("--foo", "--bar")
                .build();

        assertThat(command.getCmdParameters()).containsExactly("--foo", "--bar");
    }

    @Test
    void withEnv_mapOverloadReplacesEnvironmentVariables() {
        Map<String, String> initial = new LinkedHashMap<>();
        initial.put("A", "1");
        initial.put("B", "2");
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withEnv(initial)
                .withEnv(Collections.singletonMap("C", "3"))
                .build();

        assertThat(command.getEnvironmentVariables()).containsExactly(entry("C", "3"));
    }

    @Test
    void withEnv_mergesSingleKeyIntoExistingMap() {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withEnv(Collections.singletonMap("A", "1"))
                .withEnv("B", "2")
                .build();

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("A", "1");
        expected.put("B", "2");
        assertThat(command.getEnvironmentVariables()).containsExactlyEntriesOf(expected);
    }

    @Test
    void withExtraHost_accumulatesHostnameIpPairs() {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withExtraHost("host-a", "172.17.0.1")
                .withExtraHost("host-b", "172.17.0.2")
                .build();

        assertThat(command.getExtraHosts())
                .containsExactly("host-a:172.17.0.1", "host-b:172.17.0.2");
    }

    private static Map.Entry<String, String> entry(String key, String value) {
        return new LinkedHashMap.SimpleEntry<>(key, value);
    }
}
