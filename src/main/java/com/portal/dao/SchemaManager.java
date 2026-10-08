package com.portal.dao;

import com.portal.util.JdbcConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class SchemaManager {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaManager.class);

    private static final String[] DDL = {
            """
            CREATE TABLE IF NOT EXISTS users (
                user_id       VARCHAR(64)  NOT NULL,
                user_password VARCHAR(100) NOT NULL,
                used_vm       INT NOT NULL DEFAULT 0,
                max_vm        INT NOT NULL DEFAULT 3,
                used_pod      INT NOT NULL DEFAULT 0,
                max_pod       INT NOT NULL DEFAULT 3,
                PRIMARY KEY (user_id)
            )""",
            """
            CREATE TABLE IF NOT EXISTS vm_table (
                vm_id        VARCHAR(64) NOT NULL,
                user_id      VARCHAR(64) NOT NULL,
                vm_name      VARCHAR(255) NOT NULL,
                vm_ip        VARCHAR(64) NULL,
                float_ip     VARCHAR(64) NULL,
                ssh_key      TEXT NULL,
                status       VARCHAR(16) NOT NULL DEFAULT 'creating',
                project_name VARCHAR(255) NULL,
                flavor       VARCHAR(64) NULL,
                image        VARCHAR(255) NULL,
                PRIMARY KEY (vm_id),
                INDEX idx_vm_user (user_id),
                INDEX idx_vm_user_name (user_id, vm_name)
            )""",
            """
            CREATE TABLE IF NOT EXISTS pod_table (
                pod_id        VARCHAR(64) NOT NULL,
                user_id       VARCHAR(64) NOT NULL,
                pod_name      VARCHAR(253) NOT NULL,
                namespace     VARCHAR(63) NOT NULL,
                node_name     VARCHAR(255) NULL,
                status        VARCHAR(16) NOT NULL DEFAULT 'creating',
                restart_count INT NOT NULL DEFAULT 0,
                pod_ip        VARCHAR(64) NULL,
                pod_port      INT NOT NULL DEFAULT 0,
                image         VARCHAR(255) NULL,
                PRIMARY KEY (pod_id),
                INDEX idx_pod_user (user_id),
                INDEX idx_pod_user_name_ns (user_id, pod_name, namespace)
            )"""
    };

    private SchemaManager() {
    }

    public static boolean ensure() {
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement()) {
            for (String ddl : DDL) {
                statement.execute(ddl);
            }
            return true;
        } catch (SQLException | RuntimeException e) {

            LOG.warn("Could not create MySQL schema, will retry on next pass: {}",
                    e.toString());
            return false;
        }
    }
}
