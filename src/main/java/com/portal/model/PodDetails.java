package com.portal.model;

public class PodDetails {

    private String podId;
    private String userId;
    private String podName;
    private String namespace;
    private String nodeName;
    private String status;
    private int restartCount;
    private String podIp;
    private int podPort;
    private String image;

    public PodDetails() {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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
