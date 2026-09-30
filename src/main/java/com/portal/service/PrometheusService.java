package com.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.util.AppConfig;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Queries Prometheus over its HTTP API and returns chart-ready series.
 *
 * Every method is fail-soft: if Prometheus is unreachable or returns garbage
 * the caller gets an empty list rather than an exception, so the JSPs can
 * render "No data" instead of breaking the chart.
 */
public class PrometheusService {

    private static final Logger LOG = LoggerFactory.getLogger(PrometheusService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final CloseableHttpClient client;

    public PrometheusService() {
        this.baseUrl = AppConfig.get("prometheus.url", "http://localhost:9090");
        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(20)
                        .setMaxConnPerRoute(20)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofSeconds(5))
                        .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                        .build())
                .build();
    }

    /**
     * PromQL for VM CPU utilisation percentage, from node_exporter.
     * Returns an empty list when Prometheus is down.
     */
    public List<double[]> queryVmCpu(String vmIp) {
        String promQl = "100 - (avg(rate(node_cpu_seconds_total{mode=\"idle\",instance=\""
                + vmIp + ":9100\"}[5m])) * 100)";
        return instantQuery(promQl);
    }

    /** PromQL for VM used memory in bytes, from node_exporter. */
    public List<double[]> queryVmMemory(String vmIp) {
        String promQl = "node_memory_MemTotal_bytes{instance=\"" + vmIp + ":9100\"}"
                + " - node_memory_MemAvailable_bytes{instance=\"" + vmIp + ":9100\"}";
        return instantQuery(promQl);
    }

    /** PromQL for VM disk usage in bytes on the root filesystem. */
    public List<double[]> queryVmDisk(String vmIp) {
        String promQl = "(node_filesystem_avail_bytes{instance=\"" + vmIp + ":9100\",mountpoint=\"/\"})"
                + " * -1";
        return instantQuery(promQl);
    }

    /** PromQL for VM network receive/transmit bytes per second. */
    public List<double[]> queryVmNetwork(String vmIp) {
        String promQl = "rate(node_network_receive_bytes_total{instance=\"" + vmIp + ":9100\"}[5m])"
                + " + rate(node_network_transmit_bytes_total{instance=\"" + vmIp + ":9100\"}[5m])";
        return instantQuery(promQl);
    }

    /** 1 if the node_exporter target is up, 0 otherwise. */
    public List<double[]> queryVmUp(String vmIp) {
        return instantQuery("up{instance=\"" + vmIp + ":9100\"}");
    }

    /** PromQL for pod CPU usage (cores), from cAdvisor. */
    public List<double[]> queryPodCpu(String podName, String namespace) {
        String promQl = "rate(container_cpu_usage_seconds_total{pod=\"" + podName
                + "\",namespace=\"" + namespace + "\"}[5m])";
        return instantQuery(promQl);
    }

    /** PromQL for pod memory usage in bytes, from cAdvisor. */
    public List<double[]> queryPodMemory(String podName, String namespace) {
        String promQl = "container_memory_usage_bytes{pod=\"" + podName
                + "\",namespace=\"" + namespace + "\"}";
        return instantQuery(promQl);
    }

    /** PromQL for pod network traffic in bytes per second, from cAdvisor. */
    public List<double[]> queryPodNetwork(String podName, String namespace) {
        String promQl = "rate(container_network_transmit_bytes_total{pod=\"" + podName
                + "\",namespace=\"" + namespace + "\"}[5m])"
                + " + rate(container_network_receive_bytes_total{pod=\"" + podName
                + "\",namespace=\"" + namespace + "\"}[5m])";
        return instantQuery(promQl);
    }

    /**
     * Runs an instant query and flattens the response into [timestamp, value]
     * pairs. Returns an empty list on any failure.
     */
    public List<double[]> instantQuery(String promQl) {
        try {
            String encoded = URLEncoder.encode(promQl, StandardCharsets.UTF_8);
            HttpGet get = new HttpGet(baseUrl + "/api/v1/query?query=" + encoded);
            get.setHeader("Accept", "application/json");

            return client.execute(get, response -> {
                if (response.getCode() != 200) {
                    LOG.warn("Prometheus returned HTTP {}", response.getCode());
                    return List.of();
                }
                String body = new String(
                        response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
                return parse(body);
            });
        } catch (IOException | RuntimeException e) {
            // Deliberately swallowed: the JSP renders "No data" for empty series.
            LOG.debug("Prometheus query failed: {}", e.toString());
            return List.of();
        }
    }

    private List<double[]> parse(String body) throws IOException {
        JsonNode root = MAPPER.readTree(body);
        JsonNode data = root.path("data");
        JsonNode result = data.path("result");
        if (!result.isArray()) {
            return List.of();
        }
        List<double[]> series = new ArrayList<>();
        for (JsonNode item : result) {
            JsonNode value = item.path("value");
            if (value.isArray() && value.size() >= 2) {
                series.add(new double[]{
                        value.get(0).asDouble(),
                        value.get(1).asDouble()
                });
            }
        }
        return series;
    }
}
