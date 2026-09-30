package com.portal.dao;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.portal.util.AppConfig;
import com.portal.util.MongoConnection;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the indexes the DAOs rely on.
 *
 * The unique user_id index is not just a performance concern.
 * UserDao.createUser guards against duplicate registrations with a
 * check-then-insert, which is only safe if the index underneath actually
 * rejects the second insert. Without it, two concurrent registrations for the
 * same id both succeed and leave two rows, after which login is ambiguous
 * because find() returns whichever document happens to come first.
 */
public final class MongoIndexes {

    private static final Logger LOG = LoggerFactory.getLogger(MongoIndexes.class);

    private MongoIndexes() {
    }

    /**
     * Creates any missing index. Idempotent, so it is safe on every start.
     * Never throws: a database that is briefly unreachable at boot must not
     * abort deployment, and the next start will retry.
     *
     * @return true if every index is in place
     */
    public static boolean ensure() {
        try {
            MongoDatabase db = MongoConnection.getClient()
                    .getDatabase(AppConfig.get("mongo.database", "cloudportal"));

            unique(db, "users", new Document("user_id", 1));

            unique(db, "vm_table", new Document("vm_id", 1));
            index(db, "vm_table", new Document("user_id", 1));
            // Backs existsByName(userId, vmName) without a collection scan.
            index(db, "vm_table", new Document("user_id", 1).append("vm_name", 1));

            unique(db, "pod_table", new Document("pod_id", 1));
            index(db, "pod_table", new Document("user_id", 1));
            index(db, "pod_table",
                    new Document("user_id", 1).append("pod_name", 1).append("namespace", 1));

            return true;
        } catch (RuntimeException e) {
            LOG.warn("Could not create MongoDB indexes, will retry on next start: {}",
                    e.toString());
            return false;
        }
    }

    private static void unique(MongoDatabase db, String collection, Document keys) {
        db.getCollection(collection).createIndex(keys, new IndexOptions().unique(true));
    }

    private static void index(MongoDatabase db, String collection, Document keys) {
        db.getCollection(collection).createIndex(keys);
    }
}
