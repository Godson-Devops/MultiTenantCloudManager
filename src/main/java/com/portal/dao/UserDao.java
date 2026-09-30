package com.portal.dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReturnDocument;
import com.portal.model.User;
import com.portal.util.AppConfig;
import com.portal.util.MongoConnection;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;

import static com.mongodb.client.model.Filters.eq;

public class UserDao {

    private final MongoCollection<Document> users =
            MongoConnection.getClient()
                    .getDatabase(AppConfig.get("mongo.database", "cloudportal"))
                    .getCollection("users");

    public static final int DEFAULT_MAX_VM = 3;
    public static final int DEFAULT_MAX_POD = 3;

    public User findById(String userId) {
        if (userId == null) {
            return null;
        }
        Document doc = users.find(eq("user_id", userId)).first();
        return doc == null ? null : toUser(doc);
    }

    public boolean exists(String userId) {
        if (userId == null) {
            return false;
        }
        return users.find(eq("user_id", userId)).first() != null;
    }

    /**
     * Inserts a new user with a bcrypt-hashed password and default quotas.
     *
     * @return true if inserted, false if the user_id was already taken
     */
    public boolean createUser(String userId, String plainPassword) {
        if (userId == null || userId.isBlank() || plainPassword == null || plainPassword.isEmpty()) {
            return false;
        }
        if (exists(userId)) {
            return false;
        }
        Document doc = new Document("user_id", userId)
                .append("user_password", BCrypt.hashpw(plainPassword, BCrypt.gensalt()))
                .append("used_vm", 0)
                .append("max_vm", AppConfig.getInt("quota.default_max_vm", DEFAULT_MAX_VM))
                .append("used_pod", 0)
                .append("max_pod", AppConfig.getInt("quota.default_max_pod", DEFAULT_MAX_POD));
        try {
            users.insertOne(doc);
            return true;
        } catch (com.mongodb.MongoWriteException e) {
            // Duplicate key: another registration for the same id won the race.
            return false;
        }
    }

    /** Verifies a plain-text password against the stored bcrypt hash. */
    public boolean verifyPassword(String userId, String plainPassword) {
        if (userId == null || plainPassword == null) {
            return false;
        }
        Document doc = users.find(eq("user_id", userId)).first();
        if (doc == null) {
            return false;
        }
        String hash = doc.getString("user_password");
        if (hash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Atomically increments used_vm only if the user is still under quota.
     *
     * The quota test and the increment happen in a single findOneAndUpdate so
     * two concurrent create requests cannot both observe "under quota" and
     * both increment past the limit.
     *
     * @return true if the slot was reserved, false if the quota is exhausted
     */
    public boolean reserveVmSlot(String userId) {
        Document updated = users.findOneAndUpdate(
                Filters.and(
                        eq("user_id", userId),
                        Filters.expr(new Document("$lt", List.of("$used_vm", "$max_vm")))),
                new Document("$inc", new Document("used_vm", 1)),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        return updated != null;
    }

    /** Atomically increments used_pod only if under quota. */
    public boolean reservePodSlot(String userId) {
        Document updated = users.findOneAndUpdate(
                Filters.and(
                        eq("user_id", userId),
                        Filters.expr(new Document("$lt", List.of("$used_pod", "$max_pod")))),
                new Document("$inc", new Document("used_pod", 1)),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        return updated != null;
    }

    /**
     * Releases a reserved slot. Uses an aggregation pipeline so the counter is
     * decremented but clamped at 0 -- a double release (e.g. delete racing a
     * failed create) can never drive the counter negative and hand the user
     * free quota.
     */
    public void releaseVmSlot(String userId) {
        users.updateOne(eq("user_id", userId), List.of(new Document("$set",
                new Document("used_vm",
                        new Document("$max", List.of(0, new Document("$subtract",
                                List.of("$used_vm", 1))))))));
    }

    public void releasePodSlot(String userId) {
        users.updateOne(eq("user_id", userId), List.of(new Document("$set",
                new Document("used_pod",
                        new Document("$max", List.of(0, new Document("$subtract",
                                List.of("$used_pod", 1))))))));
    }

    private User toUser(Document doc) {
        User u = new User();
        u.setUserId(doc.getString("user_id"));
        u.setUserPassword(doc.getString("user_password"));
        u.setUsedVm(doc.getInteger("used_vm", 0));
        u.setMaxVm(doc.getInteger("max_vm", DEFAULT_MAX_VM));
        u.setUsedPod(doc.getInteger("used_pod", 0));
        u.setMaxPod(doc.getInteger("max_pod", DEFAULT_MAX_POD));
        return u;
    }
}
