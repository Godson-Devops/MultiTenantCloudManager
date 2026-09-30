package com.portal.util;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

/**
 * Single shared connection factory for the whole application.
 *
 * The MongoClient is thread-safe and pools connections internally, so one
 * instance is shared by the request threads and the background sync worker
 * rather than opening a client per request.
 */
public final class MongoConnection {

    private static volatile MongoClient client;

    private MongoConnection() {
    }

    public static MongoClient getClient() {
        MongoClient local = client;
        if (local == null) {
            synchronized (MongoConnection.class) {
                local = client;
                if (local == null) {
                    local = MongoClients.create(settings());
                    client = local;
                }
            }
        }
        return local;
    }

    private static MongoClientSettings settings() {
        String uri = AppConfig.get("mongo.uri", "mongodb://localhost:27017");
        MongoClientSettings.Builder builder = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri));

        String user = AppConfig.get("mongo.user", "");
        if (!user.isEmpty()) {
            builder.credential(MongoCredential.createCredential(
                    user,
                    AppConfig.get("mongo.auth.source", "admin"),
                    AppConfig.get("mongo.password", "").toCharArray()));
        }
        return builder.build();
    }
}
