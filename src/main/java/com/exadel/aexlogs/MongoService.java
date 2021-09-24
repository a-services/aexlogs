package com.exadel.aexlogs;

import java.io.File;
import java.util.List;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import org.bson.codecs.configuration.CodecRegistry;

import org.bson.codecs.pojo.PojoCodecProvider;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
    Send information about requests to `OUTPUT_DB`.
    For plain logs the output collection will be `DEFAULT_COLL`,
    for `exc`, this is the name of HTML files folder.
 */
public class MongoService {

    final private String OUTPUT_DB = "aexlogs";
    final private String DEFAULT_COLL = "aexlogs";

    MongoClient client;
    MongoDatabase db;
    String collName;

    public MongoService(String mongoUrl, String inputFolder) {
        this.client = MongoClients.create(mongoUrl);
        if (inputFolder == null) {
            this.collName = DEFAULT_COLL;
        } else {
            this.collName = new File(inputFolder).getName();
        }
        CodecRegistry pojoCodecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        db = client.getDatabase(OUTPUT_DB);
        db = db.withCodecRegistry(pojoCodecRegistry);
    }

    public void saveRequests(List<RequestLine> aexRequests) {
        MongoCollection<SimpleRequestLine> coll = db.getCollection(collName, SimpleRequestLine.class);
        for (RequestLine it : aexRequests) {
            coll.insertOne(new SimpleRequestLine(it));
        }
    }
}
