package by.macmonitor.preinittestcontainers.redis;

import by.macmonitor.preinittestcontainers.GenericContainerFactory;
import by.macmonitor.preinittestcontainers.endimagename.ContainerEndImageNameCalculator;
import by.macmonitor.preinittestcontainers.endimagename.RedisEndImageNameCalculator;

import com.redis.testcontainers.RedisContainer;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import org.testcontainers.utility.DockerImageName;

import java.util.function.Function;

/**
 * {@link GenericContainerFactory} for Redis via {@link RedisContainer}, applying password env and
 * {@link RedisEndImageNameCalculator} for end-image naming.
 */
@Slf4j
@SuperBuilder(toBuilder = true, setterPrefix = "with")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class RedisContainerFactory
        extends GenericContainerFactory<CreateRedisContainerCommand, RedisContainer> {

    private static final RedisContainerFactory INSTANCE = new RedisContainerFactory();

    private static final String REDIS_PASSWORD_ENV = "REDIS_PASSWORD";

    @Builder.Default
    private final Function<DockerImageName, RedisContainer> containerSupplier = RedisContainer::new;

    @Builder.Default
    private final ContainerEndImageNameCalculator<CreateRedisContainerCommand>
            endImageNameCalculator = RedisEndImageNameCalculator.INSTANCE;

    @Override
    protected void applyCommandProperties(
            RedisContainer container, CreateRedisContainerCommand command, boolean imageBuild) {
        super.applyCommandProperties(container, command, imageBuild);
        if (command.getPassword() != null) {
            container.withEnv(REDIS_PASSWORD_ENV, command.getPassword());
        }
    }

    @Override
    protected Function<DockerImageName, RedisContainer> resolveContainerSupplier() {
        return containerSupplier;
    }

    @Override
    protected ContainerEndImageNameCalculator<CreateRedisContainerCommand>
            resolveEndImageNameCalculator() {
        return endImageNameCalculator;
    }

    public static RedisContainer createRedisContainer(CreateRedisContainerCommand command) {
        return INSTANCE.create(command);
    }
}
