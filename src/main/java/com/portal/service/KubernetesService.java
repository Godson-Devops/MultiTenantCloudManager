package com.portal.service;

import com.portal.model.PodDetails;
import com.portal.model.Status;
import com.portal.util.AppConfig;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.LogWatch;

import java.io.OutputStream;

public class KubernetesService {

    private static final String MAIN_CONTAINER = "main";
    private static final String QUOTA_NAME = "user-quota";

    private final KubernetesClient client;

    public KubernetesService() {
        ConfigBuilder config = new ConfigBuilder()
                .withMasterUrl(AppConfig.get("k8s.master_url", "https://kubernetes.default.svc"))
                .withTrustCerts(Boolean.parseBoolean(AppConfig.get("k8s.trust_certs", "true")));
        String token = AppConfig.get("k8s.token", "");
        if (!token.isBlank()) {
            config.withOauthToken(token);
        }
        client = new KubernetesClientBuilder().withConfig(config.build()).build();
    }

    public PodDetails createPod(String podName, String namespace, String image, int port) {
        ensureNamespace(namespace);

        ContainerPort containerPort = new ContainerPortBuilder().withContainerPort(port).build();
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName(podName)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(MAIN_CONTAINER)
                .withImage(image)
                .withPorts(containerPort)

                .withNewResources()
                    .addToRequests("cpu", Quantity.parse(AppConfig.get("pod.cpu_requests", "100m")))
                    .addToRequests("memory", Quantity.parse(AppConfig.get("pod.memory_requests", "128Mi")))
                    .addToLimits("cpu", Quantity.parse(AppConfig.get("pod.cpu_limit", "1")))
                    .addToLimits("memory", Quantity.parse(AppConfig.get("pod.memory_limit", "512Mi")))
                .endResources()
                .endContainer()
                .endSpec()
                .build();

        Pod created = client.pods().inNamespace(namespace).resource(pod).create();
        if (created == null) {
            throw new IllegalStateException("Kubernetes returned no pod for " + podName);
        }

        PodDetails details = toDetails(created);
        details.setImage(image);
        details.setPodPort(port);
        return details;
    }

    private void ensureNamespace(String namespace) {
        if (client.namespaces().withName(namespace).get() != null) {
            return;
        }
        Namespace ns = new NamespaceBuilder()
                .withNewMetadata()
                .withName(namespace)
                .endMetadata()
                .build();
        try {
            client.namespaces().resource(ns).create();
        } catch (KubernetesClientException e) {
            if (e.getCode() != 409) {
                throw e;
            }
        }
        ensureQuota(namespace);
    }

    private void ensureQuota(String namespace) {
        if (client.resourceQuotas().inNamespace(namespace).withName(QUOTA_NAME).get() != null) {
            return;
        }
        ResourceQuota quota = new ResourceQuotaBuilder()
                .withNewMetadata()
                .withName(QUOTA_NAME)
                .endMetadata()
                .withNewSpec()
                .addToHard("pods", Quantity.parse(AppConfig.get("quota.default_max_pod", "3")))
                .addToHard("limits.cpu", Quantity.parse(AppConfig.get("quota.default_max_cpu", "4")))
                .addToHard("limits.memory", Quantity.parse(AppConfig.get("quota.default_max_memory", "8Gi")))
                .endSpec()
                .build();
        try {
            client.resourceQuotas().inNamespace(namespace).resource(quota).create();
        } catch (KubernetesClientException e) {
            if (e.getCode() != 409) {
                throw e;
            }
        }
    }

    public PodDetails get(String namespace, String podName) {
        if (namespace == null || podName == null) {
            return null;
        }
        Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
        return pod == null ? null : toDetails(pod);
    }

    public String delete(String namespace, String podName) {
        PodDetails details = get(namespace, podName);
        String uid = details == null ? null : details.getPodId();
        client.pods().inNamespace(namespace).withName(podName).withGracePeriod(0).delete();
        return uid;
    }

    public PodDetails recreate(String namespace, String podName, String image, int port) {
        delete(namespace, podName);
        awaitDeletion(namespace, podName);
        return createPod(podName, namespace, image, port);
    }

    private void awaitDeletion(String namespace, String podName) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (get(namespace, podName) == null) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public LogWatch watchLog(String namespace, String podName, OutputStream out) {
        return watchLog(namespace, podName, out, null);
    }

    public LogWatch watchLog(String namespace, String podName, OutputStream out, String since) {
        var resource = client.pods().inNamespace(namespace).withName(podName);
        if (since != null && !since.isBlank()) {
            return resource.usingTimestamps().sinceTime(since).watchLog(out);
        }
        return resource.usingTimestamps().watchLog(out);
    }

    private static Status statusOf(Pod pod) {
        if (pod == null) {
            return Status.ERROR;
        }
        return Status.fromPodPhase(pod.getStatus() == null ? null : pod.getStatus().getPhase());
    }

    private static String nodeNameOf(Pod pod) {
        return pod == null || pod.getSpec() == null ? null : pod.getSpec().getNodeName();
    }

    private static PodDetails toDetails(Pod pod) {
        PodDetails details = new PodDetails();
        details.setPodId(uidOf(pod));
        details.setPodName(pod.getMetadata() == null ? null : pod.getMetadata().getName());
        details.setNamespace(pod.getMetadata() == null ? null : pod.getMetadata().getNamespace());
        details.setNodeName(nodeNameOf(pod));
        details.setPodIp(ipOf(pod));
        details.setStatus(statusOf(pod));
        details.setRestartCount(restartCount(pod));
        return details;
    }

    private static String ipOf(Pod pod) {
        return pod == null || pod.getStatus() == null ? null : pod.getStatus().getPodIP();
    }

    private static int restartCount(Pod pod) {
        if (pod == null || pod.getStatus() == null
                || pod.getStatus().getContainerStatuses() == null) {
            return 0;
        }
        int total = 0;
        for (ContainerStatus c : pod.getStatus().getContainerStatuses()) {
            if (c.getRestartCount() != null) {
                total += c.getRestartCount();
            }
        }
        return total;
    }

    private static String uidOf(Pod pod) {
        if (pod == null || pod.getMetadata() == null) {
            return null;
        }
        return pod.getMetadata().getUid() != null
                ? pod.getMetadata().getUid()
                : pod.getMetadata().getName();
    }
}
