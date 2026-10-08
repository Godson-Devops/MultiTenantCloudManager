package com.portal.model;

public class User {

    public static final int DEFAULT_MAX_VM = 3;
    public static final int DEFAULT_MAX_POD = 3;

    private String userId;

    private String userPassword;

    private int usedVm;

    private int maxVm = DEFAULT_MAX_VM;

    private int usedPod;

    private int maxPod = DEFAULT_MAX_POD;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserPassword() {
        return userPassword;
    }

    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }

    public int getUsedVm() {
        return usedVm;
    }

    public void setUsedVm(int usedVm) {
        this.usedVm = usedVm;
    }

    public int getMaxVm() {
        return maxVm;
    }

    public void setMaxVm(int maxVm) {
        this.maxVm = maxVm;
    }

    public int getUsedPod() {
        return usedPod;
    }

    public void setUsedPod(int usedPod) {
        this.usedPod = usedPod;
    }

    public int getMaxPod() {
        return maxPod;
    }

    public void setMaxPod(int maxPod) {
        this.maxPod = maxPod;
    }
}
