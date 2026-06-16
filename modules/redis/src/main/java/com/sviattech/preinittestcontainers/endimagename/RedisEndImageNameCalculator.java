package com.sviattech.preinittestcontainers.endimagename;

import com.sviattech.preinittestcontainers.redis.CreateRedisContainerCommand;

import java.util.List;

/**
 * {@link ContainerEndImageNameCalculator} for {@link CreateRedisContainerCommand} that includes
 * Redis password and command-line parameters in the end-image hash.
 */
public class RedisEndImageNameCalculator
        extends GenericContainerEndImageNameCalculator<CreateRedisContainerCommand> {

    public static final RedisEndImageNameCalculator INSTANCE = new RedisEndImageNameCalculator();

    @Override
    protected List<String> stringParameters(CreateRedisContainerCommand command) {
        List<String> stringParameters = super.stringParameters(command);
        if (command.getPassword() != null) {
            stringParameters.add(command.getPassword());
        }
        stringParameters.addAll(command.getCmdParameters());
        return stringParameters;
    }
}
