package com.portal.dao;

import com.portal.model.PodDetails;
import com.portal.model.Status;
import com.portal.util.JdbcConnection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class PodDao {

    private static final String SELECT_COLUMNS =
            "SELECT pod_id, user_id, pod_name, namespace, node_name, status,"
                    + " restart_count, pod_ip, pod_port, image FROM pod_table";

    public void insert(PodDetails pod) {
        String nodeName = pod.getNodeName() == null ? "NULL" : "'" + pod.getNodeName() + "'";
        String podIp = pod.getPodIp() == null ? "NULL" : "'" + pod.getPodIp() + "'";
        String image = pod.getImage() == null ? "NULL" : "'" + pod.getImage() + "'";

        String sql = "INSERT INTO pod_table (pod_id, user_id, pod_name, namespace,"
                + " node_name, status, restart_count, pod_ip, pod_port, image) VALUES ('"
                + pod.getPodId() + "', '"
                + pod.getUserId() + "', '"
                + pod.getPodName() + "', '"
                + pod.getNamespace() + "', "
                + nodeName + ", '"
                + statusCode(pod) + "', "
                + pod.getRestartCount() + ", "
                + podIp + ", "
                + pod.getPodPort() + ", "
                + image + ")";
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw failure("insert pod " + pod.getPodId(), e);
        }
    }

    public PodDetails findById(String podId) {
        if (podId == null) {
            return null;
        }
        String sql = SELECT_COLUMNS + " WHERE pod_id = '" + podId + "'";
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) {
            throw failure("load pod " + podId, e);
        }
    }

    public PodDetails findByIdAndUser(String podId, String userId) {
        if (podId == null || userId == null) {
            return null;
        }
        String sql = SELECT_COLUMNS + " WHERE pod_id = '" + podId
                + "' AND user_id = '" + userId + "'";
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) {
            throw failure("load pod " + podId, e);
        }
    }

    public List<PodDetails> findByUser(String userId) {
        List<PodDetails> owned = new ArrayList<>();
        if (userId == null) {
            return owned;
        }
        String sql = SELECT_COLUMNS + " WHERE user_id = '" + userId + "'";
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                owned.add(map(rs));
            }
            return owned;
        } catch (SQLException e) {
            throw failure("load pods of user " + userId, e);
        }
    }

    public List<PodDetails> findAll() {
        List<PodDetails> all = new ArrayList<>();
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(SELECT_COLUMNS)) {
            while (rs.next()) {
                all.add(map(rs));
            }
            return all;
        } catch (SQLException e) {
            throw failure("load all pods", e);
        }
    }

    public void updateStatus(String podId, String userId, Status status) {
        String sql = "UPDATE pod_table SET status = '" + statusCode(status)
                + "' WHERE pod_id = '" + podId + "' AND user_id = '" + userId + "'";
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw failure("update status of pod " + podId, e);
        }
    }

    public void updateStatusUnscoped(String podId, Status status, String podIp,
                                     String nodeName, Integer restartCount) {
        StringBuilder sql = new StringBuilder("UPDATE pod_table SET status = '" + statusCode(status) + "'");
        if (podIp != null) {
            sql.append(", pod_ip = '" + podIp + "'");
        }
        if (nodeName != null) {
            sql.append(", node_name = '" + nodeName + "'");
        }
        if (restartCount != null) {
            sql.append(", restart_count = " + restartCount);
        }
        sql.append(" WHERE pod_id = '" + podId + "'");

        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement()) {
            statement.executeUpdate(sql.toString());
        } catch (SQLException e) {
            throw failure("sync pod " + podId, e);
        }
    }

    public void incrementRestartCount(String podId, String userId) {
        String sql = "UPDATE pod_table SET restart_count = restart_count + 1"
                + " WHERE pod_id = '" + podId + "' AND user_id = '" + userId + "'";
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw failure("increment restart count of pod " + podId, e);
        }
    }

    public boolean delete(String podId, String userId) {
        String sql = "DELETE FROM pod_table WHERE pod_id = '" + podId
                + "' AND user_id = '" + userId + "'";
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement()) {
            return statement.executeUpdate(sql) > 0;
        } catch (SQLException e) {
            throw failure("delete pod " + podId, e);
        }
    }

    public boolean existsByName(String userId, String podName, String namespace) {
        String sql = "SELECT 1 FROM pod_table WHERE user_id = '" + userId
                + "' AND pod_name = '" + podName + "' AND namespace = '" + namespace + "'";
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next();
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
