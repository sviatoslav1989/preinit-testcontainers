package by.macmonitor.preinittestcontainers.endimagename;

import static org.assertj.core.api.Assertions.assertThat;

import by.macmonitor.preinittestcontainers.ClasspathResourceMappingCommand;
import by.macmonitor.preinittestcontainers.TmpFsSystemCommand;
import by.macmonitor.preinittestcontainers.redis.CreateRedisContainerCommand;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class RedisEndImageNameCalculatorTest {

    @Test
    void calculate_changesWhenClasspathResourceMappingChanges() {
        CreateRedisContainerCommand baseline = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withTmpFsFilesystems(List.of())
                .build();
        CreateRedisContainerCommand withMapping = baseline.toBuilder()
                .withClasspathResourceMappings(List.of(ClasspathResourceMappingCommand.builder()
                        .withClasspathResourcePath("redis/custom.conf")
                        .withContainerPath("/usr/local/etc/redis/redis.conf")
                        .build()))
                .build();

        assertThat(RedisEndImageNameCalculator.INSTANCE.calculate(withMapping))
                .isNotEqualTo(RedisEndImageNameCalculator.INSTANCE.calculate(baseline));
    }

    @Test
    void calculate_changesWhenEnvironmentVariablesChange() {
        CreateRedisContainerCommand baseline = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withTmpFsFilesystems(List.of())
                .build();
        CreateRedisContainerCommand withEnv =
                baseline.toBuilder().withEnv(Map.of("REDIS_EXTRA", "1")).build();

        assertThat(RedisEndImageNameCalculator.INSTANCE.calculate(withEnv))
                .isNotEqualTo(RedisEndImageNameCalculator.INSTANCE.calculate(baseline));
    }

    @Test
    void calculate_changesWhenPasswordChanges() {
        CreateRedisContainerCommand withoutPassword = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .build();
        CreateRedisContainerCommand withPassword =
                withoutPassword.toBuilder().withPassword("secret").build();

        assertThat(RedisEndImageNameCalculator.INSTANCE.calculate(withPassword))
                .isNotEqualTo(RedisEndImageNameCalculator.INSTANCE.calculate(withoutPassword));
    }

    @Test
    void calculate_includesEntrypointFilesForAllPreInitializedCommands() {
        CreateRedisContainerCommand withPersist = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .build();
        CreateRedisContainerCommand withoutPersist = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withTmpFsFilesystems(List.of(TmpFsSystemCommand.builder()
                        .withMountPath("/data")
                        .withOptions("rw")
                        .withNeedPersist(false)
                        .build()))
                .build();

        assertThat(RedisEndImageNameCalculator.INSTANCE.calculate(withoutPersist))
                .isEqualTo(RedisEndImageNameCalculator.INSTANCE.calculate(withPersist));
    }

    @Test
    void calculate_includesEntrypointFilesRegardlessOfPreInitializedFlag() {
        CreateRedisContainerCommand preInitialized = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .build();
        CreateRedisContainerCommand notPreInitialized = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withPreInitialized(false)
                .build();

        assertThat(RedisEndImageNameCalculator.INSTANCE.calculate(notPreInitialized))
                .isEqualTo(RedisEndImageNameCalculator.INSTANCE.calculate(preInitialized));
    }

    @Test
    void calculate_isDeterministicForFixedInputs() {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withPassword("secret")
                .build();
        String first = RedisEndImageNameCalculator.INSTANCE.calculate(command);
        String second = RedisEndImageNameCalculator.INSTANCE.calculate(command);
        assertThat(second).isEqualTo(first);
        assertThat(first.substring(0, "test-redis:7.4.2.".length())).isEqualTo("test-redis:7.4.2.");
    }
}
