package com.flycast.emulator.shell;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Validates RetroAchievements proxy base URLs received across the app boundary.
 *
 * Only loopback destinations on this device are supported. This intentionally rejects
 * LAN addresses, public hosts, and hostnames other than localhost so the override cannot
 * redirect RetroAchievements requests to a remote proxy.
 *
 * This does not authenticate the broadcast sender; the exported receiver's caller trust
 * boundary remains separate from destination validation.
 */
public final class RetroAchievementsHostUrlValidator {
    private RetroAchievementsHostUrlValidator() {}

    public static boolean isValid(String hostUrl) {
        if (hostUrl == null || hostUrl.isEmpty()) return false;

        for (int i = 0; i < hostUrl.length(); i++) {
            if (Character.isISOControl(hostUrl.charAt(i)) || Character.isWhitespace(hostUrl.charAt(i))) {
                return false;
            }
        }

        try {
            URI uri = new URI(hostUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!uri.isAbsolute() || uri.isOpaque()
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || host == null || host.isEmpty()
                    || uri.getRawUserInfo() != null
                    || !isLoopbackHost(host)) {
                return false;
            }

            int port = uri.getPort();
            return port == -1 || (port >= 1 && port <= 65535);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean isLoopbackHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized);
    }
}
