package com.portal.dao;

import com.mongodb.client.MongoCollection;
import com.portal.model.PodDetails;
import com.portal.util.AppConfig;
import com.portal.util.MongoConnection;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

public class PodDao {

    private final MongoCollection<Document> pods =
            MongoConnection.getClient()
                    .getDatabase(AppConfig.get("mongo.database", "cloudportal"))
                    .getCollection("pod_table");

    public void insert(PodDetails pod) {
        pods.insertOne(toDocument(pod));
    }

    /** Loads a pod by id, scoped to the owner. */
    public PodDetails findByIdAndUser(String podId, String userId) {
        if (podId == null || userId == null) {
            return null;
        }
        Document doc = pods.find(and(eq("pod_id", podId), eq("user_id", userId))).first();
        return doc == null ? null : toPod(doc);
    }

    /** Loads a pod by name+namespace, scoped to the owner. */
    public PodDetails findByNameAndUser(String podName, String namespace, String userId) {
        if (podName == null || namespace == null || userId == null) {
            return null;
        }
        Document doc = pods.find(and(
                eq("pod_name", podName),
                eq("namespace", namespace),
                eq("user_id", userId))).first();
        return doc == null ? null : toPod(doc);
    }

    /** Loads a pod by id regardless of owner. Sync worker only. */
    public PodDetails findById(String podId) {
        if (podId == null) {
            return null;
        }
        Document doc = pods.find(eq("pod_id", podId)).first();
        return doc == null ? null : toPod(doc);
    }

    /** Returns only the calling user's pods. */
    public List<PodDetails> findByUser(String userId) {
        List<PodDetails> out = new ArrayList<>();
        if (userId == null) {
            return out;
        }
        for (Document doc : pods.find(eq("user_id", userId))) {
            out.add(toPod(doc));
        }
        return out;
    }

    /** All pods, for the background sync worker. */
    public List<PodDetails> findAll() {
        List<PodDetails> out = new ArrayList<>();
        for (Document doc : pods.find()) {
            out.add(toPod(doc));
        }
        return out;
    }

    public void updateStatus(String podId, String userId, String status) {
        pods.updateOne(and(eq("pod_id", podId), eq("user_id", userId)),
                new Document("$set", new Document("status", status)));
    }

    /** Sync worker variant: no ownership filter. */
    public void updateStatusUnscoped(String podId, String status, String podIp,
                                     String nodeName, Integer restartCount) {
        Document set = new Document("status", status);
        if (podIp != null) {
            set.append("pod_ip", podIp);
        }
        if (nodeName != null) {
            set.append("node_name", nodeName);
        }
        if (restartCount != null) {
            set.append("restart_count", restartCount);
        }
        pods.updateOne(eq("pod_id", podId), new Document("$set", set));
    }

    /** Deletes a row scoped to the owner. Returns true if a row was removed. */
    public boolean delete(String podId, String userId) {
        return pods.deleteOne(and(eq("pod_id", podId), eq("user_id", userId))).getDeletedCount() > 0;
    }

    public boolean existsByName(String userId, String podName, String namespace) {
        return pods.find(and(
                eq("user_id", userId),
                eq("pod_name", podName),
                eq("namespace", namespace))).first() != null;
    }

    public long countByUser(String userId) {
        return pods.countDocuments(eq("user_id", userId));
    }

    private Document toDocument(PodDetails pod) {
        return new Document("pod_id", pod.getPodId())
                .append("user_id", pod.getUserId())
                .append("pod_name", pod.getPodName())
                .append("namespace", pod.getNamespace())
                .append("node_name", pod.getNodeName())
                .append("status", pod.getStatus())
                .append("restart_count", pod.getRestartCount())
                .append("pod_ip", pod.getPodIp())
                .append("pod_port", pod.getPodPort())
                .append("image", pod.getImage());
    }

    private PodDetails toPod(Document doc) {
        PodDetails pod = new PodDetails();
        pod.setPodId(doc.getString("pod_id"));
        pod.setUserId(doc.getString("user_id"));
        pod.setPodName(doc.getString("pod_name"));
        pod.setNamespace(doc.getString("namespace"));
        pod.setNodeName(doc.getString("node_name"));
        pod.setStatus(doc.getString("status"));
        pod.setRestartCount(doc.getInteger("restart_count", 0));
        pod.setPodIp(doc.getString("pod_ip"));
        pod.setPodPort(doc.getInteger("pod_port", 0));
        pod.setImage(doc.getString("image"));
        return pod;
    }
}
