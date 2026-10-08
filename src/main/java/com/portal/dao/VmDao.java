package com.portal.dao;

import com.portal.model.Status;
import com.portal.model.VmDetails;
import com.portal.util.JdbcConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class VmDao {

    private static final String INSERT_SQL =
            "INSERT INTO vm_table (vm_id, user_id, vm_name, vm_ip, float_ip, ssh_key,"
                    + " status, project_name, flavor, image)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_COLUMNS =
            "SELECT vm_id, user_id, vm_name, vm_ip, float_ip, ssh_key, status,"
                    + " project_name, flavor, image FROM vm_table";

    public void insert(VmDetails vm) {
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setString(1, vm.getVmId());
            ps.setString(2, vm.getUserId());
            ps.setString(3, vm.getVmName());
            ps.setString(4, vm.getVmIp());
            ps.setString(5, vm.getFloatIp());
            ps.setString(6, vm.getSshKey());
            ps.setString(7, statusCode(vm));
            ps.setString(8, vm.getProjectName());
            ps.setString(9, vm.getFlavor());
            ps.setString(10, vm.getImage());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw failure("insert VM " + vm.getVmId(), e);
        }
    }

    public VmDetails findByIdAndUser(String vmId, String userId) {
        if (vmId == null || userId == null) {
            return null;
        }
        String sql = SELECT_COLUMNS + " WHERE vm_id = ? AND user_id = ?";
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vmId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw failure("load VM " + vmId, e);
        }
    }

    public List<VmDetails> findByUser(String userId) {
        List<VmDetails> owned = new ArrayList<>();
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
            throw failure("load VMs of user " + userId, e);
        }
    }

    public List<VmDetails> findAll() {
        List<VmDetails> all = new ArrayList<>();
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_COLUMNS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                all.add(map(rs));
            }
            return all;
        } catch (SQLException e) {
            throw failure("load all VMs", e);
        }
    }

    public void updateStatus(String vmId, String userId, Status status) {
        String sql = "UPDATE vm_table SET status = ? WHERE vm_id = ? AND user_id = ?";
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, statusCode(status));
            ps.setString(2, vmId);
            ps.setString(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw failure("update status of VM " + vmId, e);
        }
    }

    public void updateStatusUnscoped(String vmId, Status status, String vmIp, String floatIp) {
        StringBuilder sql = new StringBuilder("UPDATE vm_table SET status = ?");
        if (vmIp != null) {
            sql.append(", vm_ip = ?");
        }
        if (floatIp != null) {
            sql.append(", float_ip = ?");
        }
        sql.append(" WHERE vm_id = ?");

        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int column = 1;
            ps.setString(column++, statusCode(status));
            if (vmIp != null) {
                ps.setString(column++, vmIp);
            }
            if (floatIp != null) {
                ps.setString(column++, floatIp);
            }
            ps.setString(column, vmId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw failure("sync VM " + vmId, e);
        }
    }

    public boolean delete(String vmId, String userId) {
        String sql = "DELETE FROM vm_table WHERE vm_id = ? AND user_id = ?";
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vmId);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw failure("delete VM " + vmId, e);
        }
    }

    public boolean existsByName(String userId, String vmName) {
        String sql = "SELECT 1 FROM vm_table WHERE user_id = ? AND vm_name = ?";
        try (Connection conn = JdbcConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, vmName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
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
