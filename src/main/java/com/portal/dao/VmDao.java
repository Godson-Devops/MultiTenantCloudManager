package com.portal.dao;

import com.portal.model.Status;
import com.portal.model.VmDetails;
import com.portal.util.JdbcConnection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class VmDao {

    private static final String SELECT_COLUMNS =
            "SELECT vm_id, user_id, vm_name, vm_ip, float_ip, ssh_key, status,"
                    + " project_name, flavor, image FROM vm_table";

    public void insert(VmDetails vm) {
        String vmIp = vm.getVmIp() == null ? "NULL" : "'" + vm.getVmIp() + "'";
        String floatIp = vm.getFloatIp() == null ? "NULL" : "'" + vm.getFloatIp() + "'";
        String sshKey = vm.getSshKey() == null ? "NULL" : "'" + vm.getSshKey() + "'";
        String projectName = vm.getProjectName() == null ? "NULL" : "'" + vm.getProjectName() + "'";
        String flavor = vm.getFlavor() == null ? "NULL" : "'" + vm.getFlavor() + "'";
        String image = vm.getImage() == null ? "NULL" : "'" + vm.getImage() + "'";

        String sql = "INSERT INTO vm_table (vm_id, user_id, vm_name, vm_ip, float_ip,"
                + " ssh_key, status, project_name, flavor, image) VALUES ('"
                + vm.getVmId() + "', '"
                + vm.getUserId() + "', '"
                + vm.getVmName() + "', "
                + vmIp + ", "
                + floatIp + ", "
                + sshKey + ", '"
                + statusCode(vm) + "', "
                + projectName + ", "
                + flavor + ", "
                + image + ")";
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw failure("insert VM " + vm.getVmId(), e);
        }
    }

    public VmDetails findByIdAndUser(String vmId, String userId) {
        if (vmId == null || userId == null) {
            return null;
        }
        String sql = SELECT_COLUMNS + " WHERE vm_id = '" + vmId
                + "' AND user_id = '" + userId + "'";
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) {
            throw failure("load VM " + vmId, e);
        }
    }

    public List<VmDetails> findByUser(String userId) {
        List<VmDetails> owned = new ArrayList<>();
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
            throw failure("load VMs of user " + userId, e);
        }
    }

    public List<VmDetails> findAll() {
        List<VmDetails> all = new ArrayList<>();
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(SELECT_COLUMNS)) {
            while (rs.next()) {
                all.add(map(rs));
            }
            return all;
        } catch (SQLException e) {
            throw failure("load all VMs", e);
        }
    }

    public void updateStatus(String vmId, String userId, Status status) {
        String sql = "UPDATE vm_table SET status = '" + statusCode(status)
                + "' WHERE vm_id = '" + vmId + "' AND user_id = '" + userId + "'";
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw failure("update status of VM " + vmId, e);
        }
    }

    public void updateStatusUnscoped(String vmId, Status status, String vmIp, String floatIp) {
        StringBuilder sql = new StringBuilder("UPDATE vm_table SET status = '" + statusCode(status) + "'");
        if (vmIp != null) {
            sql.append(", vm_ip = '" + vmIp + "'");
        }
        if (floatIp != null) {
            sql.append(", float_ip = '" + floatIp + "'");
        }
        sql.append(" WHERE vm_id = '" + vmId + "'");

        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement()) {
            statement.executeUpdate(sql.toString());
        } catch (SQLException e) {
            throw failure("sync VM " + vmId, e);
        }
    }

    public boolean delete(String vmId, String userId) {
        String sql = "DELETE FROM vm_table WHERE vm_id = '" + vmId
                + "' AND user_id = '" + userId + "'";
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement()) {
            return statement.executeUpdate(sql) > 0;
        } catch (SQLException e) {
            throw failure("delete VM " + vmId, e);
        }
    }

    public boolean existsByName(String userId, String vmName) {
        String sql = "SELECT 1 FROM vm_table WHERE user_id = '" + userId
                + "' AND vm_name = '" + vmName + "'";
        try (Connection conn = JdbcConnection.getConnection();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next();
        } catch (SQLException e) {
            throw failure("look up VM named " + vmName, e);
        }
    }

    private static VmDetails map(ResultSet rs) throws SQLException {
        VmDetails vm = new VmDetails();
        vm.setVmId(rs.getString("vm_id"));
        vm.setUserId(rs.getString("user_id"));
        vm.setVmName(rs.getString("vm_name"));
        vm.setVmIp(rs.getString("vm_ip"));
        vm.setFloatIp(rs.getString("float_ip"));
        vm.setSshKey(rs.getString("ssh_key"));
        vm.setStatus(Status.fromCode(rs.getString("status")));
        vm.setProjectName(rs.getString("project_name"));
        vm.setFlavor(rs.getString("flavor"));
        vm.setImage(rs.getString("image"));
        return vm;
    }

    private static String statusCode(VmDetails vm) {
        return statusCode(vm.getStatus());
    }

    private static String statusCode(Status status) {
        return status == null ? Status.CREATING.code() : status.code();
    }

    private static RuntimeException failure(String what, SQLException e) {
        return new RuntimeException("Could not " + what + ": " + e.getMessage(), e);
    }
}
