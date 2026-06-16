package com.sviattech.preinittestcontainers.metadata;

import com.sviattech.preinittestcontainers.ContainerMetadata;
import com.sviattech.preinittestcontainers.support.ColonSeparatedArgvUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads version-ranged {@link ContainerMetadata} from a Java {@code .properties} resource.
 *
 * <p>{@code record.N.entrypoint}, {@code record.N.cmd}, and {@code record.N.volumes} use
 * colon-separated tokens per {@link ColonSeparatedArgvUtils} (backslash-escaped {@code :} and
 * {@code \} inside tokens). Absent or blank {@code cmd} means no image CMD. Blank volume tokens are
 * dropped.
 */
public final class MetadataFileLoader {

    private static final Pattern RECORD_INDEX = Pattern.compile("^record\\.(\\d+)\\.");

    private MetadataFileLoader() {}

    public static MetadataFile load(String metadataResourcePath, ClassLoader classLoader) {
        return tryLoad(metadataResourcePath, classLoader).orElseThrow(() -> {
            return new IllegalStateException(
                    "Metadata resource not found on classpath: " + metadataResourcePath);
        });
    }

    public static Optional<MetadataFile> tryLoad(
            String metadataResourcePath, ClassLoader classLoader) {
        Properties properties = new Properties();
        try (InputStream in = classLoader.getResourceAsStream(metadataResourcePath)) {
            if (in == null) {
                return Optional.empty();
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to load metadata resource: " + metadataResourcePath, e);
        }

        Set<Integer> indices = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            Matcher matcher = RECORD_INDEX.matcher(key);
            if (matcher.find()) {
                indices.add(Integer.parseInt(matcher.group(1)));
            }
        }
        if (indices.isEmpty()) {
            throw new IllegalStateException(
                    "Metadata resource has no records: " + metadataResourcePath);
        }

        List<VersionedContainerMetadata> records = new ArrayList<>(indices.size());
        for (Integer index : indices) {
            records.add(parseRecord(properties, index));
        }
        records.sort(Comparator.comparing(VersionedContainerMetadata::getStartVersion));
        validateNoOverlaps(records, metadataResourcePath);
        return Optional.of(new MetadataFile(records));
    }

    static String[] parseArgv(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) {
            return null;
        }
        List<String> tokens = ColonSeparatedArgvUtils.decode(encoded.trim());
        return tokens.isEmpty() ? null : tokens.toArray(new String[0]);
    }

    static String[] parseVolumes(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) {
            return null;
        }
        List<String> volumes = new ArrayList<>();
        for (String token : ColonSeparatedArgvUtils.decode(encoded.trim())) {
            if (!token.isEmpty()) {
                volumes.add(token);
            }
        }
        return volumes.isEmpty() ? null : volumes.toArray(new String[0]);
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static VersionedContainerMetadata parseRecord(Properties properties, int index) {
        String prefix = "record." + index + ".";
        ImageVersion startVersion =
                ImageVersion.parse(required(properties, prefix + "startVersion"));
        ImageVersion endVersion = ImageVersion.parse(required(properties, prefix + "endVersion"));
        if (startVersion.compareTo(endVersion) > 0) {
            throw new IllegalStateException("Record " + index + " has startVersion > endVersion: "
                    + startVersion + " > " + endVersion);
        }

        ContainerMetadata metadata = ContainerMetadata.builder()
                .withEntrypointPath(blankToNull(properties.getProperty(prefix + "entrypointPath")))
                .withEntrypoint(parseArgv(properties.getProperty(prefix + "entrypoint")))
                .withCmd(parseArgv(blankToNull(properties.getProperty(prefix + "cmd"))))
                .withVolumes(parseVolumes(properties.getProperty(prefix + "volumes")))
                .build();
        return new VersionedContainerMetadata(startVersion, endVersion, metadata);
    }

    private static boolean rangesOverlap(
            VersionedContainerMetadata left, VersionedContainerMetadata right) {
        return (left.getStartVersion().compareTo(right.getEndVersion()) <= 0
                && right.getStartVersion().compareTo(left.getEndVersion()) <= 0);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing required metadata property: " + key);
        }
        return value.trim();
    }

    private static void validateNoOverlaps(
            List<VersionedContainerMetadata> records, String resourcePath) {
        for (int i = 0; i < records.size(); i++) {
            VersionedContainerMetadata left = records.get(i);
            for (int j = i + 1; j < records.size(); j++) {
                VersionedContainerMetadata right = records.get(j);
                if (rangesOverlap(left, right)) {
                    throw new IllegalStateException(
                            "Overlapping version ranges in " + resourcePath + ": ["
                                    + left.getStartVersion() + ", " + left.getEndVersion()
                                    + "] and [" + right.getStartVersion()
                                    + ", " + right.getEndVersion() + "]");
                }
            }
        }
    }
}
