package com.portal.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class PodDetails {

    private String podId;
    private String userId;
    private String podName;
    private String namespace;
    private String nodeName;
    private Status status = Status.CREATING;
    private int restartCount;
    private String podIp;
    private int podPort;
    private String image;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("podId", podId);
        map.put("podName", podName);
        map.put("namespace", namespace);
        map.put("nodeName", nodeName);
        map.put("status", status);
        map.put("restartCount", restartCount);
        map.put("podIp", podIp);
        map.put("podPort", podPort);
        map.put("image", image);
        return map;
    }

    public String getPodId() {
        return podId;
    }

    public void setPodId(String podId) {
        this.podId = podId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPodName() {
        return podName;
    }

    public void setPodName(String podName) {
        this.podName = podName;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getRestartCount() {
        return restartCount;
    }

    public void setRestartCount(int restartCount) {
        this.restartCount = restartCount;
    }

    public String getPodIp() {
        return podIp;
    }

    public void setPodIp(String podIp) {
        this.podIp = podIp;
    }

    public int getPodPort() {
        return podPort;
    }

    public void setPodPort(int podPort) {
        this.podPort = podPort;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
