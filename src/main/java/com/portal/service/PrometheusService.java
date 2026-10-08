package com.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.util.AppConfig;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PrometheusService {

    private static final Logger LOG = LoggerFactory.getLogger(PrometheusService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RATE_WINDOW = "[5m]";
    private static final int MAX_CONNECTIONS = 20;
    private static final int QUERY_TIMEOUT_SECONDS = 5;
    private static final int HTTP_OK = 200;

    private static final long RANGE_WINDOW_SECONDS = 15 * 60;
    private static final long RANGE_STEP_SECONDS = 30;

    private final String baseUrl;
    private final int nodeExporterPort;
    private final CloseableHttpClient client;

    public PrometheusService() {
        this.baseUrl = AppConfig.get("prometheus.url", "http://192.168.1.169:9090");
        this.nodeExporterPort = AppConfig.getInt("prometheus.node_exporter_port", 9100);
        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(MAX_CONNECTIONS)
                        .setMaxConnPerRoute(MAX_CONNECTIONS)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofSeconds(QUERY_TIMEOUT_SECONDS))
                        .setConnectionRequestTimeout(Timeout.ofSeconds(QUERY_TIMEOUT_SECONDS))
                        .build())
                .build();
    }

    public String nodeExporterInstance(String vmIp) {
        return vmIp + ":" + nodeExporterPort;
    }

    public List<double[]> queryVmCpu(String vmIp) {
        return rangeQuery("100 - (avg(rate(node_cpu_seconds_total{mode=\"idle\",instance=\""
                + nodeExporterInstance(vmIp) + "\"}" + RATE_WINDOW + ")) * 100)");
    }

    public List<double[]> queryVmMemory(String vmIp) {
        String instance = nodeExporterInstance(vmIp);
        return rangeQuery("node_memory_MemTotal_bytes{instance=\"" + instance + "\"}"
                + " - node_memory_MemAvailable_bytes{instance=\"" + instance + "\"}");
    }

    public List<double[]> queryVmDisk(String vmIp) {
        return rangeQuery("(node_filesystem_avail_bytes{instance=\""
                + nodeExporterInstance(vmIp) + "\",mountpoint=\"/\"}) * -1");
    }

    public List<double[]> queryVmNetwork(String vmIp) {
        String instance = nodeExporterInstance(vmIp);
        return rangeQuery("sum(rate(node_network_receive_bytes_total{instance=\"" + instance + "\"}"
                + RATE_WINDOW + ") + rate(node_network_transmit_bytes_total{instance=\""
                + instance + "\"}" + RATE_WINDOW + "))");
    }

    public List<double[]> queryVmUp(String vmIp) {
        return instantQuery("up{instance=\"" + nodeExporterInstance(vmIp) + "\"}");
    }

    public List<double[]> queryPodCpu(String podName, String namespace) {
        return rangeQuery("sum(rate(container_cpu_usage_seconds_total"
                + selector(podName, namespace) + RATE_WINDOW + "))");
    }

    public List<double[]> queryPodMemory(String podName, String namespace) {
        return rangeQuery("sum(container_memory_usage_bytes" + selector(podName, namespace) + ")");
    }

    public List<double[]> queryPodNetwork(String podName, String namespace) {
        return rangeQuery("sum(rate(container_network_transmit_bytes_total"
                + selector(podName, namespace) + RATE_WINDOW
                + ") + rate(container_network_receive_bytes_total"
                + selector(podName, namespace) + RATE_WINDOW + "))");
    }

    public List<double[]> rangeQuery(String promQl) {
        try {
            long end = System.currentTimeMillis() / 1000;
            long start = end - RANGE_WINDOW_SECONDS;
            String encoded = URLEncoder.encode(promQl, StandardCharsets.UTF_8);
            HttpGet get = new HttpGet(baseUrl + "/api/v1/query_range?query=" + encoded
                    + "&start=" + start + "&end=" + end + "&step=" + RANGE_STEP_SECONDS);
            get.setHeader("Accept", "application/json");

            return client.execute(get, new HttpClientResponseHandler<List<double[]>>() {
                @Override
                public List<double[]> handleResponse(ClassicHttpResponse response) throws IOException {
                    if (response.getCode() != HTTP_OK) {
                        LOG.warn("Prometheus returned HTTP {}", response.getCode());
                        return List.of();
                    }
                    return parseMatrix(response.getEntity().getContent());
                }
            });
        } catch (IOException | RuntimeException e) {

            LOG.debug("Prometheus range query failed: {}", e.toString());
            return List.of();
        }
    }

    public List<double[]> instantQuery(String promQl) {
        try {
            String encoded = URLEncoder.encode(promQl, StandardCharsets.UTF_8);
            HttpGet get = new HttpGet(baseUrl + "/api/v1/query?query=" + encoded);
            get.setHeader("Accept", "application/json");

            return client.execute(get, new HttpClientResponseHandler<List<double[]>>() {
                @Override
                public List<double[]> handleResponse(ClassicHttpResponse response) throws IOException {
                    if (response.getCode() != HTTP_OK) {
                        LOG.warn("Prometheus returned HTTP {}", response.getCode());
                        return List.of();
                    }
                    return parse(response.getEntity().getContent());
                }
            });
        } catch (IOException | RuntimeException e) {

            LOG.debug("Prometheus query failed: {}", e.toString());
            return List.of();
        }
    }

    private List<double[]> parse(InputStream json) throws IOException {
        JsonNode result = MAPPER.readTree(json).path("data").path("result");
        if (!result.isArray()) {
            return List.of();
        }
        List<double[]> series = new ArrayList<>();
        for (JsonNode item : result) {
            JsonNode value = item.path("value");
            if (value.isArray() && value.size() >= 2) {
                series.add(new double[]{value.get(0).asDouble(), value.get(1).asDouble()});
            }
        }
        return series;
    }

    private static String selector(String podName, String namespace) {
        return "{pod=\"" + podName + "\",namespace=\"" + namespace + "\"}";
    }

    private List<double[]> parseMatrix(InputStream json) throws IOException {
        JsonNode result = MAPPER.readTree(json).path("data").path("result");
        if (!result.isArray()) {
            return List.of();
        }
        Map<Long, Double> merged = new TreeMap<>();
        for (JsonNode item : result) {
            JsonNode values = item.path("values");
            if (!values.isArray()) {
                continue;
            }
            for (JsonNode sample : values) {
                if (sample.isArray() && sample.size() >= 2) {
                    long ts = (long) (sample.get(0).asDouble() * 1000);
                    merged.merge(ts, sample.get(1).asDouble(), Double::sum);
                }
            }
        }
        List<double[]> series = new ArrayList<>(merged.size());
        for (Map.Entry<Long, Double> entry : merged.entrySet()) {
            series.add(new double[]{entry.getKey() / 1000.0, entry.getValue()});
        }
        return series;
    }
}
