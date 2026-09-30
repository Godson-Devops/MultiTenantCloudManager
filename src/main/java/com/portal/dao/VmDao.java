package com.portal.dao;

import com.mongodb.client.MongoCollection;
import com.portal.model.VmDetails;
import com.portal.util.AppConfig;
import com.portal.util.MongoConnection;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

public class VmDao {

    private final MongoCollection<Document> vms =
            MongoConnection.getClient()
                    .getDatabase(AppConfig.get("mongo.database", "cloudportal"))
                    .getCollection("vm_table");

    public void insert(VmDetails vm) {
        vms.insertOne(toDocument(vm));
    }

    /** Loads a VM by id, scoped to the owner. */
    public VmDetails findByIdAndUser(String vmId, String userId) {
        if (vmId == null || userId == null) {
            return null;
        }
        Document doc = vms.find(and(eq("vm_id", vmId), eq("user_id", userId))).first();
        return doc == null ? null : toVm(doc);
    }

    /** Loads a VM by id regardless of owner. Used only by the sync worker. */
    public VmDetails findById(String vmId) {
        if (vmId == null) {
            return null;
        }
        Document doc = vms.find(eq("vm_id", vmId)).first();
        return doc == null ? null : toVm(doc);
    }

    /** Returns only the calling user's VMs. The user_id filter is not optional. */
    public List<VmDetails> findByUser(String userId) {
        List<VmDetails> out = new ArrayList<>();
        if (userId == null) {
            return out;
        }
        for (Document doc : vms.find(eq("user_id", userId))) {
            out.add(toVm(doc));
        }
        return out;
    }

    /** All VMs, for the background sync worker. */
    public List<VmDetails> findAll() {
        List<VmDetails> out = new ArrayList<>();
        for (Document doc : vms.find()) {
            out.add(toVm(doc));
        }
        return out;
    }

    /** Updates status only when the row is still owned by this user. */
    public void updateStatus(String vmId, String userId, String status) {
        vms.updateOne(and(eq("vm_id", vmId), eq("user_id", userId)),
                new Document("$set", new Document("status", status)));
    }

    /** Sync worker variant: no ownership filter. */
    public void updateStatusUnscoped(String vmId, String status, String vmIp, String floatIp) {
        Document set = new Document("status", status);
        if (vmIp != null) {
            set.append("vm_ip", vmIp);
        }
        if (floatIp != null) {
            set.append("float_ip", floatIp);
        }
        vms.updateOne(eq("vm_id", vmId), new Document("$set", set));
    }

    /** Deletes a row, scoped to the owner. Returns true if a row was removed. */
    public boolean delete(String vmId, String userId) {
        return vms.deleteOne(and(eq("vm_id", vmId), eq("user_id", userId))).getDeletedCount() > 0;
    }

    /** True when a VM with this name already exists for the user. */
    public boolean existsByName(String userId, String vmName) {
        return vms.find(and(eq("user_id", userId), eq("vm_name", vmName))).first() != null;
    }

    public long countByUser(String userId) {
        return vms.countDocuments(eq("user_id", userId));
    }

    private Document toDocument(VmDetails vm) {
        return new Document("vm_id", vm.getVmId())
                .append("user_id", vm.getUserId())
                .append("vm_name", vm.getVmName())
                .append("vm_ip", vm.getVmIp())
                .append("float_ip", vm.getFloatIp())
                .append("ssh_key", vm.getSshKey())
                .append("status", vm.getStatus())
                .append("project_name", vm.getProjectName())
                .append("flavor", vm.getFlavor())
                .append("image", vm.getImage());
    }

    private VmDetails toVm(Document doc) {
        VmDetails vm = new VmDetails();
        vm.setVmId(doc.getString("vm_id"));
        vm.setUserId(doc.getString("user_id"));
        vm.setVmName(doc.getString("vm_name"));
        vm.setVmIp(doc.getString("vm_ip"));
        vm.setFloatIp(doc.getString("float_ip"));
        vm.setSshKey(doc.getString("ssh_key"));
        vm.setStatus(doc.getString("status"));
        vm.setProjectName(doc.getString("project_name"));
        vm.setFlavor(doc.getString("flavor"));
        vm.setImage(doc.getString("image"));
        return vm;
    }
}
