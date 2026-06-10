package by.macmonitor.preinittestcontainers;

import by.macmonitor.preinittestcontainers.endimagename.ContainerEndImageNameCalculator;
import by.macmonitor.preinittestcontainers.endimagename.JdbcEndImageNameCalculator;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.function.Function;

/**
 * {@link GenericContainerFactory} for JDBC
 * {@link org.testcontainers.containers.JdbcDatabaseContainer} types: applies database name,
 * credentials, init scripts, and URL parameters from {@link CreateJdbcContainerCommand}, and uses
 * {@link JdbcEndImageNameCalculator} for end-image naming.
 *
 * <p>Database modules ({@code MySQLContainerFactory}, {@code PostgreSQLContainerFactory}, and so
 * on) extend this class and supply a container supplier only.
 */
@Slf4j
@SuperBuilder(toBuilder = true, setterPrefix = "with")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public abstract class JdbcContainerFactory<
                C extends CreateJdbcContainerCommand, T extends JdbcDatabaseContainer<?>>
        extends GenericContainerFactory<C, T> {

    @Builder.Default
    private final ContainerEndImageNameCalculator<C> endImageNameCalculator =
            JdbcEndImageNameCalculator.instance();

    @Override
    protected void applyCommandProperties(T container, C command, boolean imageBuild) {
        super.applyCommandProperties(container, command, imageBuild);
        container
                .withDatabaseName(command.getDbName())
                .withUsername(command.getUsername())
                .withPassword(command.getPassword());
        if (imageBuild) {
            container.withInitScripts(command.getInitScripts());
        }
        command.getUrlParameters().forEach(container::withUrlParam);
        if (command.getStartupTimeoutSeconds() != null) {
            container.withStartupTimeoutSeconds(command.getStartupTimeoutSeconds());
        } else if (command.getStartupTimeout() != null) {
            container.withStartupTimeoutSeconds(
                    Math.toIntExact(command.getStartupTimeout().getSeconds()));
        }
        if (command.getConnectTimeoutSeconds() != null) {
            container.withConnectTimeoutSeconds(command.getConnectTimeoutSeconds());
        }
    }

    @Override
    protected abstract Function<DockerImageName, T> resolveContainerSupplier();

    @Override
    protected ContainerEndImageNameCalculator<C> resolveEndImageNameCalculator() {
        return endImageNameCalculator;
    }
}
