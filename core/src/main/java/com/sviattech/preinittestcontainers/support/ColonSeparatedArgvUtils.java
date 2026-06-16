package com.sviattech.preinittestcontainers.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Encodes and decodes colon-separated argv tokens for container env vars consumed by
 * {@code testcontainer-entrypoint.sh} ({@code TCE_UPSTREAM_ENTRYPOINT},
 * {@code TCE_LIVE_DATA_PATHS}, {@code TCE_SNAPSHOT_TEMP_PATHS}).
 *
 * <p>Rules: {@code :} separates tokens; literal {@code :} and {@code \} inside a token are escaped
 * as {@code \:} and {@code \\}; empty fields are preserved ({@code a::c} → three tokens). A
 * trailing {@code \} with no following character is invalid.
 *
 * <p>Values without {@code :} or {@code \} in any token round-trip unchanged (existing production
 * entrypoint and tmpfs paths). Encoding a single empty token yields an empty string; bash {@code [
 * -n ... ]} treats that as unset — not used by current defaults.
 */
public final class ColonSeparatedArgvUtils {

    private ColonSeparatedArgvUtils() {}

    public static List<String> decode(String encoded) {
        if (encoded == null) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        while (i < encoded.length()) {
            char c = encoded.charAt(i);
            if (c == '\\') {
                if (i + 1 >= encoded.length()) {
                    throw new IllegalArgumentException(
                            "Trailing backslash in colon-separated argv");
                }
                current.append(encoded.charAt(i + 1));
                i += 2;
            } else if (c == ':') {
                tokens.add(current.toString());
                current.setLength(0);
                i++;
            } else {
                current.append(c);
                i++;
            }
        }
        tokens.add(current.toString());
        return Collections.unmodifiableList(new ArrayList<>(tokens));
    }

    public static String encode(List<String> argv) {
        if (argv == null || argv.isEmpty()) {
            return "";
        }
        return argv.stream()
                .map(ColonSeparatedArgvUtils::escapeToken)
                .collect(Collectors.joining(":"));
    }

    public static String encode(String... argv) {
        if (argv == null || argv.length == 0) {
            return "";
        }
        return encode(Arrays.asList(argv));
    }

    private static String escapeToken(String token) {
        StringBuilder sb = new StringBuilder(token.length() * 2);
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '\\' || c == ':') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
