package org.example.db;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Updates.*;

public class MongoDBService {
    private final MongoCollection<Document> wbProductsCollection;
    private final MongoCollection<Document> ozonProductsCollection;
    private final MongoCollection<Document> apiDataCollection;
    private final MongoCollection<Document> errorLogsCollection;
    private final MongoCollection<Document> productMetricsCollection;

    public enum Marketplace {
        WILDBERRIES,
        OZON
    }

    public MongoDBService() {
        MongoClient mongoClient = MongoClients.create("mongodb+srv://kasatkinnikita13:T9lswrFtZMLgl6Wj@cluster0.vx90u17.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0");
        MongoDatabase saleBotDB = mongoClient.getDatabase("sale_bot");

        this.wbProductsCollection = saleBotDB.getCollection("wb_products");
        this.ozonProductsCollection = saleBotDB.getCollection("ozon_products");
        this.productMetricsCollection = saleBotDB.getCollection("product_metrics");

        MongoDatabase saleBotInfoDB = mongoClient.getDatabase("sale_bot_info");
        this.apiDataCollection = saleBotInfoDB.getCollection("api_data");
        this.errorLogsCollection = saleBotInfoDB.getCollection("error_logs");

        createIndexes();
    }

    private void createIndexes() {
        wbProductsCollection.createIndex(new Document("query", 1));
        wbProductsCollection.createIndex(new Document("foundProducts.productId", 1));
        wbProductsCollection.createIndex(new Document("lastUpdated", -1));

        ozonProductsCollection.createIndex(new Document("query", 1));
        ozonProductsCollection.createIndex(new Document("foundProducts.productId", 1));
        ozonProductsCollection.createIndex(new Document("lastUpdated", -1));

        productMetricsCollection.createIndex(new Document("productId", 1));
        productMetricsCollection.createIndex(new Document("marketplace", 1));
        productMetricsCollection.createIndex(new Document("timestamp", -1));

        errorLogsCollection.createIndex(new Document("timestamp", -1));
        apiDataCollection.createIndex(new Document("processed", 1));
    }

    public void saveOrUpdateProduct(Marketplace marketplace, String query,
                                    String productId, String name, int price,
                                    String brand, int rating) {
        try {
            MongoCollection<Document> collection = getCollectionForMarketplace(marketplace);

            Document productDoc = new Document()
                    .append("productId", productId)
                    .append("name", name)
                    .append("price", price)
                    .append("brand", brand)
                    .append("rating", rating)
                    .append("marketplace", marketplace.name())
                    .append("lastUpdated", new Date());

            collection.updateOne(
                    eq("query", query),
                    combine(
                            setOnInsert("query", query),
                            addToSet("foundProducts", productDoc),
                            inc("searchCount", 1),
                            set("lastUpdated", new Date()),
                            set("marketplace", marketplace.name())
                    ),
                    new UpdateOptions().upsert(true)
            );
        } catch (Exception e) {
            logError("saveProductError", e.getMessage(),
                    Map.of("marketplace", marketplace.name(),
                            "query", query,
                            "productId", productId));
        }
    }

    public List<Document> getProductPriceHistory(String productId, Marketplace marketplace, int days) {
        MongoCollection<Document> collection = getCollectionForMarketplace(marketplace);

        Date startDate = new Date(System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000);

        Bson filter = and(
                eq("foundProducts.productId", productId),
                gte("lastUpdated", startDate)
        );

        return collection.find(filter)
                .sort(descending("lastUpdated"))
                .into(new ArrayList<>());
    }

    public List<Document> getProductMetrics(String productId, Marketplace marketplace, int days) {
        Date startDate = new Date(System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000);

        Bson filter = and(
                eq("productId", productId),
                eq("marketplace", marketplace.name()),
                gte("timestamp", startDate)
        );

        return productMetricsCollection.find(filter)
                .sort(descending("timestamp"))
                .into(new ArrayList<>());
    }

    private MongoCollection<Document> getCollectionForMarketplace(Marketplace marketplace) {
        return marketplace == Marketplace.WILDBERRIES ? wbProductsCollection : ozonProductsCollection;
    }

    public void saveApiRawData(String endpoint, String request, String response) {
        Document doc = new Document()
                .append("timestamp", new Date())
                .append("endpoint", endpoint)
                .append("request", request)
                .append("response", response)
                .append("processed", false);

        apiDataCollection.insertOne(doc);
    }

    public void logError(String errorType, String message, Object context) {
        Document errorDoc = new Document()
                .append("timestamp", new Date())
                .append("errorType", errorType)
                .append("message", message)
                .append("context", context);

        errorLogsCollection.insertOne(errorDoc);
    }

    public Document getSearchStats(Marketplace marketplace, String query) {
        return getCollectionForMarketplace(marketplace)
                .find(eq("query", query))
                .first();
    }

    public MongoCollection<Document> getProductMetricsCollection() {
        return productMetricsCollection;
    }

    // В класс MongoDBService добавьте эти методы:

    public Document getProductInfo(String productId, Marketplace marketplace) {
        MongoCollection<Document> collection = getCollectionForMarketplace(marketplace);
        Bson filter = eq("foundProducts.productId", productId);
        return collection.find(filter).sort(descending("lastUpdated")).first();
    }

    public List<Document> getDetailedPriceHistory(String productId, Marketplace marketplace, Date startDate, Date endDate) {
        List<Document> result = new ArrayList<>();

        // Получаем данные из основной коллекции
        MongoCollection<Document> collection = getCollectionForMarketplace(marketplace);
        Bson filter = and(
                eq("foundProducts.productId", productId),
                gte("lastUpdated", startDate),
                lte("lastUpdated", endDate)
        );

        collection.find(filter).sort(descending("lastUpdated")).forEach(doc -> {
            Date lastUpdated = doc.getDate("lastUpdated");
            List<Document> products = doc.getList("foundProducts", Document.class);
            for (Document product : products) {
                if (productId.equals(product.getString("productId"))) {
                    Document priceRecord = new Document()
                            .append("timestamp", lastUpdated)
                            .append("price", product.getInteger("price"));
                    result.add(priceRecord);
                }
            }
        });

        // Получаем данные из метрик
        Bson metricsFilter = and(
                eq("productId", productId),
                eq("marketplace", marketplace.name()),
                gte("timestamp", startDate),
                lte("timestamp", endDate)
        );

        productMetricsCollection.find(metricsFilter)
                .sort(descending("timestamp"))
                .forEach(metric -> {
                    Document priceRecord = new Document()
                            .append("timestamp", metric.getDate("timestamp"))
                            .append("price", metric.getInteger("price"));
                    result.add(priceRecord);
                });

        return result;
    }
}