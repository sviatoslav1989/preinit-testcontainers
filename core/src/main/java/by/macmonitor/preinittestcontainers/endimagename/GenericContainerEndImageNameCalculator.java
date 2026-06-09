package by.macmonitor.preinittestcontainers.endimagename;

import by.macmonitor.preinittestcontainers.ClasspathResourceMappingCommand;
import by.macmonitor.preinittestcontainers.CreateGenericContainerCommand;
import by.macmonitor.preinittestcontainers.PreInitStartCallback;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Slf4j
public class GenericContainerEndImageNameCalculator<C extends CreateGenericContainerCommand>
        implements ContainerEndImageNameCalculator<C> {

    public static final GenericContainerEndImageNameCalculator<CreateGenericContainerCommand>
            INSTANCE = new GenericContainerEndImageNameCalculator<>();

    private static final String ENTRYPOINT_CLASS_PATH = "docker/testcontainer-entrypoint.sh";

    private static final int FILE_DIGEST_BUFFER_SIZE = 8 * 1024;

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    @Override
    public String calculate(C command) {
        List<String> stringParameters = new ArrayList<>(stringParameters(command));
        List<String> fileParameters = fileParameters(command);
        String hash = computeImageParametersHash(stringParameters, fileParameters);
        String imageNamePrefix = imageNamePrefix(command);
        String name = imageNamePrefix
                + '-'
                + command.getBaseImageName()
                + '.'
                + (hash.length() <= 8 ? hash : hash.substring(0, 8));
        log.info("Testcontainer pre-initialized image name: {} (prefix={})", name, imageNamePrefix);
        return name;
    }

    protected List<String> fileParameters(C command) {
        List<String> fileParameters = new ArrayList<>();
        fileParameters.add(ENTRYPOINT_CLASS_PATH);
        for (ClasspathResourceMappingCommand mapping : command.getClasspathResourceMappings()) {
            fileParameters.add(mapping.getClasspathResourcePath());
        }
        return fileParameters;
    }

    protected String imageNamePrefix(C command) {
        return "test";
    }

    protected List<String> stringParameters(C command) {
        List<String> parameters = new ArrayList<>(command.getCmdParameters());
        for (Map.Entry<String, String> entry :
                new TreeMap<>(command.getEnvironmentVariables()).entrySet()) {
            parameters.add(entry.getKey() + "=" + entry.getValue());
        }
        Boolean privilegedMode = command.getPrivilegedMode();
        if (privilegedMode != null) {
            parameters.add("privileged=" + privilegedMode);
        }
        PreInitStartCallback callback = command.getAfterPreInitStartCallback();
        if (callback != null) {
            parameters.add(Objects.requireNonNull(
                    callback.uniqueKey(), "callback.uniqueKey() must not be null"));
        }
        return parameters;
    }

    /**
     * Full MD5 hex digest of all inputs that affect pre-initialized image contents.
     *
     * <p>{@code stringParameters} are every non-file input that should affect the hash (e.g.
     * database name, credentials, server command flags, environment knobs). For each element
     * <strong>in list order</strong>, UTF-8 bytes are fed into the digest with no extra delimiters,
     * matching sequential {@link StringBuilder#append(String)} calls. {@code null} or empty list
     * contributes nothing to that part; {@code null} elements use the literal {@code "null"} bytes
     * (same as {@link StringBuilder#append(String)}).
     *
     * <p>{@code fileParameters} are classpath resource paths. For each non-blank path <strong>in
     * list order</strong>, the raw resource bytes are streamed into the same digest (no decoding,
     * no line-ending normalization). {@code null} or empty list contributes nothing. Callers should
     * list files in a stable order (e.g. init scripts then entrypoint) so the hash matches the
     * intended layering.
     *
     * @return 32-character lowercase MD5 hex string (not truncated)
     */
    @SneakyThrows
    private static String computeImageParametersHash(
            List<String> stringParameters, List<String> fileParameters) {
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        if (stringParameters != null && !stringParameters.isEmpty()) {
            for (String parameter : stringParameters) {
                md5.update(
                        (parameter == null ? "null" : parameter).getBytes(StandardCharsets.UTF_8));
            }
        }

        if (fileParameters != null && !fileParameters.isEmpty()) {
            for (String resourcePath : fileParameters) {
                md5.update(resourcePath.getBytes(StandardCharsets.UTF_8));
                if (!resourcePath.trim().isEmpty()) {
                    updateDigestFromClasspathResource(md5, resourcePath);
                }
            }
        }

        return formatHex(md5.digest());
    }

    private static String formatHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            chars[i * 2] = HEX_DIGITS[v >>> 4];
            chars[i * 2 + 1] = HEX_DIGITS[v & 0x0F];
        }
        return new String(chars);
    }

    private static URL getResourceUrl(String resourcePath) {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
        if (resource == null) {
            resource = GenericContainerEndImageNameCalculator.class
                    .getClassLoader()
                    .getResource(resourcePath);
            if (resource == null) {
                log.warn("Could not load resource: {}", resourcePath);
                throw new RuntimeException(
                        "Could not load resource: " + resourcePath + ". Resource not found.");
            }
        }
        return resource;
    }

    private static void updateDigestFromClasspathResource(
            java.security.MessageDigest md5, String resourcePath) throws IOException {
        URL url = getResourceUrl(resourcePath);
        try (InputStream inputStream = url.openStream()) {
            byte[] buffer = new byte[FILE_DIGEST_BUFFER_SIZE];
            int n;
            while ((n = inputStream.read(buffer)) != -1) {
                md5.update(buffer, 0, n);
            }
        }
    }
}
