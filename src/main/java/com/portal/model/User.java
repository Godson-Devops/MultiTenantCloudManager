package com.portal.model;

public class User {

    private String userId;
    private String userPassword;
    private int usedVm;
    private int maxVm;
    private int usedPod;
    private int maxPod;

    public User() {
    }

    public User(String userId, String userPassword, int usedVm, int maxVm, int usedPod, int maxPod) {
        this.userId = userId;
        this.userPassword = userPassword;
        this.usedVm = usedVm;
        this.maxVm = maxVm;
        this.usedPod = usedPod;
        this.maxPod = maxPod;
    }

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

    /** True when the user has already used their entire VM allowance. */
    public boolean isVmQuotaExceeded() {
        return usedVm >= maxVm;
    }

    /** True when the user has already used their entire Pod allowance. */
    public boolean isPodQuotaExceeded() {
        return usedPod >= maxPod;
    }
}
