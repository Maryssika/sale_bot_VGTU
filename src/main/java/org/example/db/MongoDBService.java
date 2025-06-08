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

        // Коллекции для разных маркетплейсов
        this.wbProductsCollection = saleBotDB.getCollection("wb_products");
        this.ozonProductsCollection = saleBotDB.getCollection("ozon_products");

        // БД для информации и логов
        MongoDatabase saleBotInfoDB = mongoClient.getDatabase("sale_bot_info");
        this.apiDataCollection = saleBotInfoDB.getCollection("api_data");
        this.errorLogsCollection = saleBotInfoDB.getCollection("error_logs");

        createIndexes();
    }

    private void createIndexes() {
        // Индексы для Wildberries
        wbProductsCollection.createIndex(new Document("query", 1));
        wbProductsCollection.createIndex(new Document("foundProducts.productId", 1));

        // Индексы для Ozon
        ozonProductsCollection.createIndex(new Document("query", 1));
        ozonProductsCollection.createIndex(new Document("foundProducts.productId", 1));

        // Индексы для логов и API данных
        errorLogsCollection.createIndex(new Document("timestamp", -1));
        apiDataCollection.createIndex(new Document("processed", 1));
    }

    // Сохранение продуктов с указанием маркетплейса
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
                    eq("query", query), // Фильтр по query
                    combine( // Обновляющие операторы
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

    private MongoCollection<Document> getCollectionForMarketplace(Marketplace marketplace) {
        return marketplace == Marketplace.WILDBERRIES ?
                wbProductsCollection : ozonProductsCollection;
    }

    // Остальные методы остаются без изменений
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

    // Получение статистики по маркетплейсу
    public Document getSearchStats(Marketplace marketplace, String query) {
        return getCollectionForMarketplace(marketplace)
                .find(eq("query", query))
                .first();
    }
}