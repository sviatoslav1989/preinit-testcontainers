package by.macmonitor.preinittestcontainers.endimagename;

import by.macmonitor.preinittestcontainers.CreateContainerCommand;

public interface ContainerEndImageNameCalculator<T extends CreateContainerCommand> {
    String calculate(T command);
}
