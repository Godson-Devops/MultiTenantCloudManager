package com.portal.service;

import com.portal.model.PodDetails;
import com.portal.util.AppConfig;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;

/** Thin wrapper over the Fabric8 Kubernetes client. */
public class KubernetesService {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesService.class);

    private final KubernetesClient client;

    public KubernetesService() {
        String master = AppConfig.get("k8s.master_url", "https://kubernetes.default.svc");
        String token = AppConfig.get("k8s.token", "");

        ConfigBuilder config = new ConfigBuilder()
                .withMasterUrl(master)
                .withTrustCerts(Boolean.parseBoolean(AppConfig.get("k8s.trust_certs", "true")));
        if (token != null && !token.isBlank()) {
            config.withOauthToken(token);
        }
        this.client = new KubernetesClientBuilder().withConfig(config.build()).build();
    }

    /**
     * Creates a pod in the given namespace. The caller is responsible for the
     * quota check; Kubernetes enforces its own namespace ResourceQuota.
     */
    public PodDetails createPod(String podName, String namespace, String image, int port) {
        ContainerPort containerPort = new ContainerPortBuilder().withContainerPort(port).build();
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName(podName)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName("main")
                .withImage(image)
                .withPorts(containerPort)
                .endContainer()
                .endSpec()
                .build();

        Pod created = client.pods().inNamespace(namespace).resource(pod).create();
        if (created == null) {
            throw new IllegalStateException("Kubernetes returned no pod for " + podName);
        }

        PodDetails details = new PodDetails();
        details.setPodId(podUid(created));
        details.setPodName(created.getMetadata().getName());
        details.setNamespace(created.getMetadata().getNamespace());
        details.setImage(image);
        details.setPodPort(port);
        details.setNodeName(created.getSpec() != null ? created.getSpec().getNodeName() : null);
        details.setPodIp(podIp(created));
        details.setRestartCount(restartCount(created));
        details.setStatus(created.getStatus() != null && created.getStatus().getPhase() != null
                ? created.getStatus().getPhase() : "Pending");
        return details;
    }

    /** Current phase, or null if the pod is gone. */
    public String getPodPhase(String namespace, String podName) {
        Pod pod = get(namespace, podName);
        if (pod == null) {
            return null;
        }
        return pod.getStatus() == null ? null : pod.getStatus().getPhase();
    }

    public Pod get(String namespace, String podName) {
        if (namespace == null || podName == null) {
            return null;
        }
        return client.pods().inNamespace(namespace).withName(podName).get();
    }

    /**
     * "Stop" for a bare Pod. Kubernetes has no stop on a Pod (there is no
     * scale to zero), so this deletes it -- see section N of the spec.
     * Returns the UID of the deleted pod so the row can be reconciled.
     */
    public String delete(String namespace, String podName) {
        // Read the pod first: withName(...) narrows to a single resource, so
        // there is no list() to call. Grabbing the UID beforehand lets the
        // caller match the row it owns rather than guessing by name.
        Pod existing = get(namespace, podName);
        String uid = (existing != null && existing.getMetadata() != null)
                ? existing.getMetadata().getUid()
                : null;
        client.pods().inNamespace(namespace).withName(podName).delete();
        return uid;
    }

    /** Watches pod logs into the given stream. Caller owns closing the stream. */
    public LogWatch watchLog(String namespace, String podName, OutputStream out) {
        return client.pods().inNamespace(namespace).withName(podName).watchLog(out);
    }

    public String podIp(Pod pod) {
        if (pod == null || pod.getStatus() == null) {
            return null;
        }
        return pod.getStatus().getPodIP();
    }

    public int restartCount(Pod pod) {
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

    private String podUid(Pod pod) {
        if (pod.getMetadata() != null && pod.getMetadata().getUid() != null) {
            return pod.getMetadata().getUid();
        }
        return pod.getMetadata() == null ? null : pod.getMetadata().getName();
    }
}
