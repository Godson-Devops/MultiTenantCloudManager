package com.portal.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public final class JdbcConnection {

    private static volatile HikariDataSource dataSource;

    private JdbcConnection() {
    }

    public static Connection getConnection() throws SQLException {
        return pool().getConnection();
    }

    private static HikariDataSource pool() {
        HikariDataSource local = dataSource;
        if (local == null) {
            synchronized (JdbcConnection.class) {
                local = dataSource;
                if (local == null) {
                    local = createPool();
                    dataSource = local;
                }
            }
        }
        return local;
    }

    private static HikariDataSource createPool() {
        HikariConfig config = new HikariConfig();

        config.setDriverClassName(AppConfig.get("mysql.driver", "com.mysql.cj.jdbc.Driver"));
        config.setJdbcUrl(AppConfig.get("mysql.url", "jdbc:mysql://localhost:3306/cloudportal"));
        config.setUsername(AppConfig.get("mysql.user", "root"));
        config.setPassword(AppConfig.get("mysql.password", ""));
        return new HikariDataSource(config);
    }
}
