package com.sviattech.preinittestcontainers.endimagename;

import com.sviattech.preinittestcontainers.CreateJdbcContainerCommand;

import java.util.List;

/**
 * {@link ContainerEndImageNameCalculator} for {@link CreateJdbcContainerCommand} that folds
 * database name, credentials, and init script paths into the pre-initialized image hash.
 *
 * <p>Uses {@code dbName} as the image name prefix so different logical databases get distinct
 * cached images. Shared singleton via {@link #instance()}.
 */
public class JdbcEndImageNameCalculator<C extends CreateJdbcContainerCommand>
        extends GenericContainerEndImageNameCalculator<C> {

    private static final JdbcEndImageNameCalculator<CreateJdbcContainerCommand> INSTANCE =
            new JdbcEndImageNameCalculator<>();

    @Override
    protected List<String> fileParameters(C command) {
        List<String> fileParameters = super.fileParameters(command);
        List<String> scripts = command.getInitScripts();
        if (scripts != null) {
            fileParameters.addAll(scripts);
        }
        return fileParameters;
    }

    @Override
    protected String imageNamePrefix(C command) {
        return command.getDbName();
    }

    @Override
    protected List<String> stringParameters(C command) {
        List<String> stringParameters = super.stringParameters(command);
        stringParameters.add(command.getDbName());
        stringParameters.add(command.getUsername());
        stringParameters.add(command.getPassword());
        return stringParameters;
    }

    @SuppressWarnings("unchecked")
    public static <C extends CreateJdbcContainerCommand> JdbcEndImageNameCalculator<C> instance() {
        return (JdbcEndImageNameCalculator<C>) INSTANCE;
    }
}
