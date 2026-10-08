package com.portal.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum Status {

    CREATING("creating"),
    RUNNING("running"),
    STOPPED("stopped"),
    ERROR("error"),

    DELETED("deleted");

    private static final Map<String, Status> BY_CODE = index();

    private final String code;

    Status(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public static Status fromCode(String code) {
        Status status = code == null ? null : BY_CODE.get(code);
        return status == null ? ERROR : status;
    }

    public static Status fromOpenStack(String novaStatus) {
        if (novaStatus == null) {
            return ERROR;
        }
        switch (novaStatus.toUpperCase(Locale.ROOT)) {
            case "BUILD":
                return CREATING;
            case "ACTIVE":
                return RUNNING;
            case "SHUTOFF":
                return STOPPED;
            default:
                return ERROR;
        }
    }

    public static Status fromPodPhase(String phase) {
        if (phase == null) {
            return CREATING;
        }
        switch (phase) {
            case "Running":
                return RUNNING;
            case "Failed":
                return ERROR;
            case "Succeeded":
                return STOPPED;
            default:
                return CREATING;
        }
    }

    private static Map<String, Status> index() {
        Map<String, Status> byCode = new HashMap<>();
        for (Status status : values()) {
            byCode.put(status.code, status);
        }
        return Map.copyOf(byCode);
    }
}
