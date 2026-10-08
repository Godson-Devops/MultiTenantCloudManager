package com.portal.dao;

import com.portal.model.User;
import com.portal.util.AppConfig;
import com.portal.util.JdbcConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

public class UserDao {

    private static final String SELECT_COLUMNS =
            "SELECT user_id, user_password, used_vm, max_vm, used_pod, max_pod"
                    + " FROM users";

    public User findById(String userId) {
        if (userId == null) {
            return null;
        }
        String sql = SELECT_COLUMNS + " WHERE user_id = ?";
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw failure("load user " + userId, e);
        }
    }

    public boolean exists(String userId) {
        return findById(userId) != null;
    }

    public boolean createUser(String userId, String plainPassword) {
        if (userId == null || userId.isBlank() || plainPassword == null || plainPassword.isEmpty()) {
            return false;
        }
        String sql = "INSERT INTO users (user_id, user_password, max_vm, max_pod)"
                + " VALUES (?, ?, ?, ?)";
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, BCrypt.hashpw(plainPassword, BCrypt.gensalt()));
            ps.setInt(3, AppConfig.getInt("quota.default_max_vm", User.DEFAULT_MAX_VM));
            ps.setInt(4, AppConfig.getInt("quota.default_max_pod", User.DEFAULT_MAX_POD));
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {

            return false;
        } catch (SQLException e) {
            throw failure("create user " + userId, e);
        }
    }

    public boolean verifyPassword(String userId, String plainPassword) {
        if (plainPassword == null) {
            return false;
        }
        User user = findById(userId);
        if (user == null || user.getUserPassword() == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, user.getUserPassword());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean reserveVmSlot(String userId) {
        return reserveSlot("UPDATE users SET used_vm = used_vm + 1"
                + " WHERE user_id = ? AND used_vm < max_vm", userId);
    }

    public boolean reservePodSlot(String userId) {
        return reserveSlot("UPDATE users SET used_pod = used_pod + 1"
                + " WHERE user_id = ? AND used_pod < max_pod", userId);
    }

    public void releaseVmSlot(String userId) {
        releaseSlot("UPDATE users SET used_vm = used_vm - 1"
                + " WHERE user_id = ? AND used_vm > 0", userId);
    }

    public void releasePodSlot(String userId) {
        releaseSlot("UPDATE users SET used_pod = used_pod - 1"
                + " WHERE user_id = ? AND used_pod > 0", userId);
    }

    private static boolean reserveSlot(String sql, String userId) {
        return executeSlotUpdate(sql, userId) > 0;
    }

    private static void releaseSlot(String sql, String userId) {
        executeSlotUpdate(sql, userId);
    }

    private static int executeSlotUpdate(String sql, String userId) {
        if (userId == null) {
            return 0;
        }
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw failure("update quota of user " + userId, e);
        }
    }

    private static User map(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getString("user_id"));
        user.setUserPassword(rs.getString("user_password"));
        user.setUsedVm(rs.getInt("used_vm"));
        user.setMaxVm(rs.getInt("max_vm"));
        user.setUsedPod(rs.getInt("used_pod"));
        user.setMaxPod(rs.getInt("max_pod"));
        return user;
    }

    private static RuntimeException failure(String what, SQLException e) {
        return new RuntimeException("Could not " + what + ": " + e.getMessage(), e);
    }
}
