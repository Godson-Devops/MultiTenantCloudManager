package com.portal.dao;

import com.portal.model.PodDetails;
import com.portal.model.Status;
import com.portal.util.JdbcConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PodDao {

    private static final String INSERT_SQL =
            "INSERT INTO pod_table (pod_id, user_id, pod_name, namespace, node_name,"
                    + " status, restart_count, pod_ip, pod_port, image)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_COLUMNS =
            "SELECT pod_id, user_id, pod_name, namespace, node_name, status,"
                    + " restart_count, pod_ip, pod_port, image FROM pod_table";

    public void insert(PodDetails pod) {
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setString(1, pod.getPodId());
            ps.setString(2, pod.getUserId());
            ps.setString(3, pod.getPodName());
            ps.setString(4, pod.getNamespace());
            ps.setString(5, pod.getNodeName());
            ps.setString(6, statusCode(pod));
            ps.setInt(7, pod.getRestartCount());
            ps.setString(8, pod.getPodIp());
            ps.setInt(9, pod.getPodPort());
            ps.setString(10, pod.getImage());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw failure("insert pod " + pod.getPodId(), e);
        }
    }

    public PodDetails findByIdAndUser(String podId, String userId) {
        if (podId == null || userId == null) {
            return null;
        }
        String sql = SELECT_COLUMNS + " WHERE pod_id = ? AND user_id = ?";
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, podId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw failure("load pod " + podId, e);
        }
    }

    public List<PodDetails> findByUser(String userId) {
        List<PodDetails> owned = new ArrayList<>();
        if (userId == null) {
            return owned;
        }
        String sql = SELECT_COLUMNS + " WHERE user_id = ?";
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    owned.add(map(rs));
                }
            }
            return owned;
        } catch (SQLException e) {
            throw failure("load pods of user " + userId, e);
        }
    }

    public List<PodDetails> findAll() {
        List<PodDetails> all = new ArrayList<>();
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_COLUMNS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                all.add(map(rs));
            }
            return all;
        } catch (SQLException e) {
            throw failure("load all pods", e);
        }
    }

    public void updateStatus(String podId, String userId, Status status) {
        String sql = "UPDATE pod_table SET status = ? WHERE pod_id = ? AND user_id = ?";
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, statusCode(status));
            ps.setString(2, podId);
            ps.setString(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw failure("update status of pod " + podId, e);
        }
    }

    public void updateStatusUnscoped(String podId, Status status, String podIp,
                                     String nodeName, Integer restartCount) {
        StringBuilder sql = new StringBuilder("UPDATE pod_table SET status = ?");
        if (podIp != null) {
            sql.append(", pod_ip = ?");
        }
        if (nodeName != null) {
            sql.append(", node_name = ?");
        }
        if (restartCount != null) {
            sql.append(", restart_count = ?");
        }
        sql.append(" WHERE pod_id = ?");

        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int column = 1;
            ps.setString(column++, statusCode(status));
            if (podIp != null) {
                ps.setString(column++, podIp);
            }
            if (nodeName != null) {
                ps.setString(column++, nodeName);
            }
            if (restartCount != null) {
                ps.setInt(column++, restartCount);
            }
            ps.setString(column, podId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw failure("sync pod " + podId, e);
        }
    }

    public boolean delete(String podId, String userId) {
        String sql = "DELETE FROM pod_table WHERE pod_id = ? AND user_id = ?";
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, podId);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw failure("delete pod " + podId, e);
        }
    }

    public boolean existsByName(String userId, String podName, String namespace) {
        String sql = "SELECT 1 FROM pod_table"
                + " WHERE user_id = ? AND pod_name = ? AND namespace = ?";
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, podName);
            ps.setString(3, namespace);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw failure("look up pod named " + podName, e);
        }
    }

    private static PodDetails map(ResultSet rs) throws SQLException {
        PodDetails pod = new PodDetails();
        pod.setPodId(rs.getString("pod_id"));
        pod.setUserId(rs.getString("user_id"));
        pod.setPodName(rs.getString("pod_name"));
        pod.setNamespace(rs.getString("namespace"));
        pod.setNodeName(rs.getString("node_name"));
        pod.setStatus(Status.fromCode(rs.getString("status")));
        pod.setRestartCount(rs.getInt("restart_count"));
        pod.setPodIp(rs.getString("pod_ip"));
        pod.setPodPort(rs.getInt("pod_port"));
        pod.setImage(rs.getString("image"));
        return pod;
    }

    private static String statusCode(PodDetails pod) {
        return statusCode(pod.getStatus());
    }

    private static String statusCode(Status status) {
        return status == null ? Status.CREATING.code() : status.code();
    }

    private static RuntimeException failure(String what, SQLException e) {
        return new RuntimeException("Could not " + what + ": " + e.getMessage(), e);
    }
}
