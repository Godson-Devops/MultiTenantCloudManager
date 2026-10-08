package com.portal.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class AppConfig {

    private static final String FILE_NAME = "portal.properties";

    private static final Properties PROPS = new Properties();

    private static final Map<String, String> ENV_NAMES = new ConcurrentHashMap<>();
    private static volatile boolean loaded;

    private AppConfig() {
    }

    public static String get(String key, String defaultValue) {
        load();
        String env = System.getenv(envName(key));
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String value = PROPS.getProperty(key);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    public static int getInt(String key, int defaultValue) {
        return (int) getLong(key, defaultValue);
    }

    public static long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String envName(String key) {
        String cached = ENV_NAMES.get(key);
        if (cached != null) {
            return cached;
        }
        String name = key.toUpperCase(Locale.ROOT).replace('.', '_');
        String raced = ENV_NAMES.putIfAbsent(key, name);
        return raced == null ? name : raced;
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(FILE_NAME)) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (IOException e) {

        }
        loaded = true;
    }
}
