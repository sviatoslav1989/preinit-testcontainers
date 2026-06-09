package by.macmonitor.preinittestcontainers.postgresql;

import by.macmonitor.preinittestcontainers.JdbcContainerFactory;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.function.Function;

@SuperBuilder(toBuilder = true, setterPrefix = "with")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class PostgreSQLContainerFactory
        extends JdbcContainerFactory<CreatePostgreSQLContainerCommand, PostgreSQLContainer> {

    private static final PostgreSQLContainerFactory INSTANCE = new PostgreSQLContainerFactory();

    @Builder.Default
    private final Function<DockerImageName, PostgreSQLContainer> containerSupplier =
            PostgreSQLContainer::new;

    @Override
    protected Function<DockerImageName, PostgreSQLContainer> resolveContainerSupplier() {
        return containerSupplier;
    }

    public static PostgreSQLContainer createPostgreSQLContainer(
            CreatePostgreSQLContainerCommand command) {
        return INSTANCE.create(command);
    }
}
