package com.portal.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class VmDetails {

    private String vmId;
    private String userId;
    private String vmName;
    private String vmIp;
    private String floatIp;
    private String sshKey;
    private Status status = Status.CREATING;
    private String projectName;
    private String flavor;
    private String image;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("vmId", vmId);
        map.put("vmName", vmName);
        map.put("status", status);
        map.put("projectName", projectName);
        map.put("flavor", flavor);
        map.put("image", image);
        map.put("vmIp", vmIp);
        map.put("floatIp", floatIp);
        map.put("sshKey", sshKey);
        return map;
    }

    public String getVmId() {
        return vmId;
    }

    public void setVmId(String vmId) {
        this.vmId = vmId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getVmName() {
        return vmName;
    }

    public void setVmName(String vmName) {
        this.vmName = vmName;
    }

    public String getVmIp() {
        return vmIp;
    }

    public void setVmIp(String vmIp) {
        this.vmIp = vmIp;
    }

    public String getFloatIp() {
        return floatIp;
    }

    public void setFloatIp(String floatIp) {
        this.floatIp = floatIp;
    }

    public String getSshKey() {
        return sshKey;
    }

    public void setSshKey(String sshKey) {
        this.sshKey = sshKey;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getFlavor() {
        return flavor;
    }

    public void setFlavor(String flavor) {
        this.flavor = flavor;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
