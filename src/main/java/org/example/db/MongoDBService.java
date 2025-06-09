package org.example.db;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class MongoDBService {
    private final MongoCollection<Document> wbProductsCollection;
    private final MongoCollection<Document> ozonProductsCollection;
    private final MongoCollection<Document> apiDataCollection;
    private final MongoCollection<Document> errorLogsCollection;

    public enum Marketplace {
        WILDBERRIES,
        OZON
    }

    public MongoDBService() {
        MongoClient mongoClient = MongoClients.create("mongodb+srv://kasatkinnikita13:T9lswrFtZMLgl6Wj@cluster0.vx90u17.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0");
        MongoDatabase saleBotDB = mongoClient.getDatabase("sale_bot");

        this.wbProductsCollection = saleBotDB.getCollection("wb_products");
        this.ozonProductsCollection = saleBotDB.getCollection("ozon_products");

        MongoDatabase saleBotInfoDB = mongoClient.getDatabase("sale_bot_info");
        this.apiDataCollection = saleBotInfoDB.getCollection("api_data");
        this.errorLogsCollection = saleBotInfoDB.getCollection("error_logs");

        createIndexes();
    }

    private void createIndexes() {
        wbProductsCollection.createIndex(new Document("query", 1));
        wbProductsCollection.createIndex(new Document("foundProducts.productId", 1));
        ozonProductsCollection.createIndex(new Document("query", 1));
        ozonProductsCollection.createIndex(new Document("foundProducts.productId", 1));
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

    public List<Document> getCachedProducts(Marketplace marketplace, String query) {
        Document doc = getCollectionForMarketplace(marketplace)
                .find(eq("query", query))
                .projection(new Document("foundProducts", 1).append("lastUpdated", 1))
                .first();

        if (doc != null) {
            Date lastUpdated = doc.getDate("lastUpdated");
            if (lastUpdated != null && (new Date().getTime() - lastUpdated.getTime()) < 2 * 60 * 60 * 1000) {
                return (List<Document>) doc.get("foundProducts");
            }
        }
        return null;
    }

    public void clearOldCache(Marketplace marketplace, int hours) {
        MongoCollection<Document> collection = getCollectionForMarketplace(marketplace);
        long cutoff = System.currentTimeMillis() - (hours * 60 * 60 * 1000);
        collection.deleteMany(lt("lastUpdated", new Date(cutoff)));
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

    public String getCacheInfo(Marketplace marketplace, String query) {
        Document doc = getCollectionForMarketplace(marketplace)
                .find(eq("query", query))
                .first();

        if (doc == null) return "Кэш не найден для запроса '" + query + "'";

        Date lastUpdated = doc.getDate("lastUpdated");
        int count = doc.getList("foundProducts", Document.class, new ArrayList<>()).size();
        int searches = doc.getInteger("searchCount", 0);

        // Форматирование даты
        String formattedDate = "неизвестно";
        if (lastUpdated != null) {
            Instant instant = lastUpdated.toInstant();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                    .withLocale(new Locale("ru"))
                    .withZone(ZoneId.systemDefault());
            formattedDate = formatter.format(instant);
        }

        return String.format(
                "ℹ️ Кэш для запроса: '%s'\n📦 Найдено товаров: %d\n🕒 Последнее обновление: %s\n🔄 Искомо раз: %d",
                query, count, formattedDate, searches
        );
    }
}
