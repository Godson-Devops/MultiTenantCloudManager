package com.portal.dao;

import com.portal.model.User;
import com.portal.util.AppConfig;
import com.portal.util.JdbcConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UserDaoQuotaTest {

    private static final String TEST_DB = "cloudportal_test";
    private static final String PASSWORD = "Passw0rd!";
    private static final int DEFAULT_MAX_VM = 3;

    private static final String[] TABLES = {"users", "vm_table", "pod_table"};

    private static boolean mysqlAvailable;

    private UserDao dao;

    @BeforeAll
    static void requireMysql() {
        assumeTrue(ping(), "MySQL is not reachable, skipping the DAO tests");
        assumeTrue(AppConfig.get("mysql.url", "").contains(TEST_DB),
                "the DAO would target the real portal database, refusing to run");
        recreateSchema();
        mysqlAvailable = true;
    }

    @AfterAll
    static void dropTestTables() {
        if (mysqlAvailable) {
            dropTables();
        }
    }

    @BeforeEach
    void freshDao() {
        dropTables();
        recreateSchema();
        dao = new UserDao();
    }

    @Test
    @DisplayName("a new user gets default quotas and a hashed password")
    void createsUserWithDefaults() {
        assertTrue(dao.createUser("u-create", PASSWORD));

        User user = dao.findById("u-create");
        assertNotNull(user);
        assertEquals(DEFAULT_MAX_VM, user.getMaxVm());
        assertEquals(DEFAULT_MAX_VM, user.getMaxPod());
        assertEquals(0, user.getUsedVm());
        assertEquals(0, user.getUsedPod());
        assertFalse(user.getUserPassword().equals(PASSWORD), "the password must not be stored as sent");
        assertTrue(user.getUserPassword().startsWith("$2"), "expected a bcrypt hash");
    }

    @Test
    @DisplayName("the same user id cannot be registered twice")
    void refusesDuplicateUserId() {
        assertTrue(dao.createUser("u-dup", PASSWORD));
        assertFalse(dao.createUser("u-dup", PASSWORD));
    }

    @Test
    @DisplayName("a wrong password is rejected and the right one accepted")
    void verifiesPassword() {
        dao.createUser("u-pw", PASSWORD);

        assertTrue(dao.verifyPassword("u-pw", PASSWORD));
        assertFalse(dao.verifyPassword("u-pw", "wrong"));
        assertFalse(dao.verifyPassword("u-absent", PASSWORD));
    }

    @Test
    @DisplayName("reserving stops at the limit")
    void reserveStopsAtTheLimit() {
        dao.createUser("u-limit", PASSWORD);

        for (int i = 0; i < DEFAULT_MAX_VM; i++) {
            assertTrue(dao.reserveVmSlot("u-limit"), "slot " + (i + 1) + " should be granted");
        }

        assertFalse(dao.reserveVmSlot("u-limit"), "the limit must be enforced");
        assertEquals(DEFAULT_MAX_VM, dao.findById("u-limit").getUsedVm());
    }

    @Test
    @DisplayName("releasing frees exactly one slot")
    void releaseFreesOneSlot() {
        dao.createUser("u-release", PASSWORD);
        dao.reserveVmSlot("u-release");
        dao.reserveVmSlot("u-release");

        dao.releaseVmSlot("u-release");

        assertEquals(1, dao.findById("u-release").getUsedVm());
        assertTrue(dao.reserveVmSlot("u-release"), "the freed slot is reusable");
        assertTrue(dao.reserveVmSlot("u-release"), "the last slot is reusable");
        assertFalse(dao.reserveVmSlot("u-release"), "the limit applies again");
        assertEquals(DEFAULT_MAX_VM, dao.findById("u-release").getUsedVm());
    }

    @Test
    @DisplayName("releasing never drives the count below zero")
    void releaseStopsAtZero() {
        dao.createUser("u-zero", PASSWORD);

        dao.releaseVmSlot("u-zero");
        dao.releaseVmSlot("u-zero");

        assertEquals(0, dao.findById("u-zero").getUsedVm());
    }

    @Test
    @DisplayName("reserving for an unknown user grants nothing")
    void reserveForUnknownUserGrantsNothing() {
        assertFalse(dao.reserveVmSlot("u-absent"));
        assertNull(dao.findById("u-absent"));
    }

    @Test
    @DisplayName("pod slots are accounted the same way as VM slots")
    void podSlotsBehaveLikeVmSlots() {
        dao.createUser("u-pod", PASSWORD);

        for (int i = 0; i < DEFAULT_MAX_VM; i++) {
            assertTrue(dao.reservePodSlot("u-pod"), "slot " + (i + 1) + " should be granted");
        }
        assertFalse(dao.reservePodSlot("u-pod"));

        dao.releasePodSlot("u-pod");
        assertEquals(DEFAULT_MAX_VM - 1, dao.findById("u-pod").getUsedPod());
    }

    @Test
    @DisplayName("simultaneous requests cannot push a user past the limit")
    void concurrentReservesNeverExceedTheLimit() throws Exception {
        dao.createUser("u-race", PASSWORD);
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        go.await();
                        return dao.reserveVmSlot("u-race");
                    }
                }));
            }
            go.countDown();

            int granted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    granted++;
                }
            }

            assertEquals(DEFAULT_MAX_VM, granted,
                    "exactly the quota may be granted, however many callers race");
            assertEquals(DEFAULT_MAX_VM, dao.findById("u-race").getUsedVm());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("a row written before quotas existed still enforces the default limit")
    void legacyUserWithoutQuotaFieldsGetsDefaults() {
        insertRawUser("u-legacy",
                "$2a$06$0123456789012345678901uKzFh0F0F0F0F0F0F0F0F0F0F");

        for (int i = 0; i < DEFAULT_MAX_VM; i++) {
            assertTrue(dao.reserveVmSlot("u-legacy"), "slot " + (i + 1) + " should be granted");
        }
        assertFalse(dao.reserveVmSlot("u-legacy"), "the default limit must still apply");
    }

    private static void insertRawUser(String userId, String passwordHash) {
        String sql = "INSERT INTO users (user_id, user_password) VALUES (?, ?)";
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, passwordHash);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not insert raw user: " + e.getMessage(), e);
        }
    }

    private static boolean ping() {
        try (Connection conn = JdbcConnection.getConnection()) {
            return conn.isValid(2);
        } catch (SQLException | RuntimeException e) {

            return false;
        }
    }

    private static void recreateSchema() {
        dropTables();
        if (!SchemaManager.ensure()) {
            throw new IllegalStateException("could not create the test schema");
        }
    }

    private static void dropTables() {
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement()) {
            for (String table : TABLES) {
                statement.execute("DROP TABLE IF EXISTS " + table);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not drop test tables: " + e.getMessage(), e);
        }
    }
}
