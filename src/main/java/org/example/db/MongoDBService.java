package org.example.db;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.*;

public class MongoDBService {
    private final MongoCollection<Document> productsCollection;
    private final MongoCollection<Document> apiDataCollection;
    private final MongoCollection<Document> errorLogsCollection;

    public MongoDBService() {
        MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017");

        // Основная БД для продуктов
        MongoDatabase saleBotDB = mongoClient.getDatabase("sale_bot");
        this.productsCollection = saleBotDB.getCollection("products");

        // БД для информации и логов
        MongoDatabase saleBotInfoDB = mongoClient.getDatabase("sale_bot_info");
        this.apiDataCollection = saleBotInfoDB.getCollection("api_data");
        this.errorLogsCollection = saleBotInfoDB.getCollection("error_logs");

        // Создаем индексы при инициализации
        createIndexes();
    }

    private void createIndexes() {
        // Индекс для быстрого поиска продуктов по запросу
        productsCollection.createIndex(new Document("query", 1));

        // Индекс для быстрого поиска по времени в логах
        errorLogsCollection.createIndex(new Document("timestamp", -1));

        // Индекс для необработанных API данных
        apiDataCollection.createIndex(new Document("processed", 1));
        apiDataCollection.createIndex(new Document("timestamp", -1));
    }

    // Сохранение продуктов в sale_bot.products
    public void saveOrUpdateProduct(String query, String productId, String name,
                                    int price, String brand, int rating) {
        try {
            Document productDoc = new Document()
                    .append("productId", productId)
                    .append("name", name)
                    .append("price", price)
                    .append("brand", brand)
                    .append("rating", rating)
                    .append("lastUpdated", new Date());

            productsCollection.updateOne(
                    eq("query", query),
                    combine(
                            setOnInsert("query", query),
                            addToSet("foundProducts", productDoc),
                            inc("searchCount", 1),
                            set("lastUpdated", new Date())
                    ),
                    new UpdateOptions().upsert(true)
            );
        } catch (Exception e) {
            logError("saveProductError", e.getMessage(),
                    Map.of("query", query, "productId", productId));
        }
    }

    // Сохранение сырых данных от API в sale_bot_info.api_data
    public void saveApiRawData(String endpoint, String request, String response) {
        Document doc = new Document()
                .append("timestamp", new Date())
                .append("endpoint", endpoint)
                .append("request", request)
                .append("response", response)
                .append("processed", false)
                .append("processingAttempts", 0);

        apiDataCollection.insertOne(doc);
    }

    // Логирование ошибок в sale_bot_info.error_logs
    public void logError(String errorType, String message, Object context) {
        Document errorDoc = new Document()
                .append("timestamp", new Date())
                .append("errorType", errorType)
                .append("message", message)
                .append("context", context)
                .append("resolved", false);

        errorLogsCollection.insertOne(errorDoc);
    }

    // Получение статистики из sale_bot.products
    public Document getSearchStats(String query) {
        return productsCollection.find(eq("query", query)).first();
    }

    // Методы для работы с API данными
    public List<Document> getUnprocessedApiData(int limit) {
        return apiDataCollection.find(eq("processed", false))
                .limit(limit)
                .into(new ArrayList<>());
    }

    public void markAsProcessed(String documentId) {
        apiDataCollection.updateOne(
                eq("_id", new org.bson.types.ObjectId(documentId)),
                combine(
                        set("processed", true),
                        set("processedAt", new Date())
                )
        );
    }

    // Методы для работы с ошибками
    public List<Document> getUnresolvedErrors(int limit) {
        return errorLogsCollection.find(eq("resolved", false))
                .sort(new Document("timestamp", -1))
                .limit(limit)
                .into(new ArrayList<>());
    }

    public void markErrorAsResolved(String errorId) {
        errorLogsCollection.updateOne(
                eq("_id", new org.bson.types.ObjectId(errorId)),
                combine(
                        set("resolved", true),
                        set("resolvedAt", new Date())
                )
        );
    }

    // Дополнительные методы
    public long getTotalSearches() {
        return productsCollection.countDocuments();
    }

    public List<Document> getRecentErrors(int limit) {
        return errorLogsCollection.find()
                .sort(new Document("timestamp", -1))
                .limit(limit)
                .into(new ArrayList<>());
    }
}