package com.portal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lazily constructs and shares the three external-provider clients.
 *
 * Each client authenticates on construction (OpenStack, Kubernetes) or opens a
 * connection pool (Prometheus), so they are built on first use and reused
 * afterwards rather than per request. A construction failure is not cached, so
 * a provider that is down at boot can recover once it comes back.
 */
public final class ServiceRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceRegistry.class);

    private static volatile OpenStackService openStack;
    private static volatile KubernetesService kubernetes;
    private static volatile PrometheusService prometheus;

    private ServiceRegistry() {
    }

    public static OpenStackService openStack() {
        OpenStackService local = openStack;
        if (local == null) {
            synchronized (ServiceRegistry.class) {
                local = openStack;
                if (local == null) {
                    try {
                        local = new OpenStackService();
                        openStack = local;
                    } catch (RuntimeException e) {
                        LOG.warn("OpenStack client unavailable: {}", e.toString());
                        throw e;
                    }
                }
            }
        }
        return local;
    }

    public static KubernetesService kubernetes() {
        KubernetesService local = kubernetes;
        if (local == null) {
            synchronized (ServiceRegistry.class) {
                local = kubernetes;
                if (local == null) {
                    try {
                        local = new KubernetesService();
                        kubernetes = local;
                    } catch (RuntimeException e) {
                        LOG.warn("Kubernetes client unavailable: {}", e.toString());
                        throw e;
                    }
                }
            }
        }
        return local;
    }

    public static PrometheusService prometheus() {
        PrometheusService local = prometheus;
        if (local == null) {
            synchronized (ServiceRegistry.class) {
                local = prometheus;
                if (local == null) {
                    local = new PrometheusService();
                    prometheus = local;
                }
            }
        }
        return local;
    }
}
