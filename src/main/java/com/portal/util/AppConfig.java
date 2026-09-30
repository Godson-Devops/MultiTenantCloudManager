package com.portal.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads configuration from a classpath "portal.properties", falling back to
 * environment variables and then to a built-in default.
 *
 * Secrets (OpenStack / Kubernetes / Mongo passwords) are read from the
 * environment first so they never need to be committed to source control.
 */
public final class AppConfig {

    private static final Properties PROPS = new Properties();
    private static boolean loaded = false;

    private AppConfig() {
    }

    public static String get(String key, String defaultValue) {
        load();
        String envKey = key.toUpperCase().replace('.', '_');
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String value = PROPS.getProperty(key);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("portal.properties")) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (IOException e) {
            // Non-fatal: defaults still apply.
        }
        loaded = true;
    }
}
