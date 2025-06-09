package org.example.db;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;

import java.util.*;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class MongoDBService {
    // Коллекции БД для разных типов данных
    private final MongoCollection<Document> wbProductsCollection;
    private final MongoCollection<Document> ozonProductsCollection;
    private final MongoCollection<Document> apiRequestMetricsCollection;
    private final MongoCollection<Document> productMetricsCollection;
    private final MongoCollection<Document> commandMetricsCollection;
    private final MongoCollection<Document> errorLogsCollection;
    private final MongoCollection<Document> apiDataCollection;

    // Маркетплейсы, которые поддерживает система
    public enum Marketplace {
        WILDBERRIES,
        OZON
    }

    // Конструктор подключается к MongoDB и инициализирует коллекции
    public MongoDBService() {
        MongoClient mongoClient = MongoClients.create("mongodb+srv://kasatkinnikita13:T9lswrFtZMLgl6Wj@cluster0.vx90u17.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0");
        MongoDatabase saleBotDB = mongoClient.getDatabase("sale_bot");
        MongoDatabase saleBotInfoDB = mongoClient.getDatabase("sale_bot_info");

        // Основные коллекции по товарам
        this.wbProductsCollection = saleBotDB.getCollection("wb_products");
        this.ozonProductsCollection = saleBotDB.getCollection("ozon_products");

        // Коллекции для сбора метрик и логов
        this.apiRequestMetricsCollection = saleBotInfoDB.getCollection("api_request_metrics");
        this.productMetricsCollection = saleBotInfoDB.getCollection("product_metrics");
        this.commandMetricsCollection = saleBotInfoDB.getCollection("command_metrics");
        this.errorLogsCollection = saleBotInfoDB.getCollection("error_logs");
        this.apiDataCollection = saleBotInfoDB.getCollection("api_data");

        // Создание индексов
        createIndexes();
    }

    // Создание индексов по ключевым полям
    private void createIndexes() {
        // Индексы для коллекций товаров
        wbProductsCollection.createIndex(new Document("query", 1));
        wbProductsCollection.createIndex(new Document("foundProducts.productId", 1));
        ozonProductsCollection.createIndex(new Document("query", 1));
        ozonProductsCollection.createIndex(new Document("foundProducts.productId", 1));

        // Индексы для запросов к API
        apiRequestMetricsCollection.createIndex(new Document("timestamp", -1));
        apiRequestMetricsCollection.createIndex(new Document("endpoint", 1));
        apiRequestMetricsCollection.createIndex(new Document("catalogType", 1));

        // Индексы для продуктов, команд и ошибок
        productMetricsCollection.createIndex(new Document("productId", 1));
        commandMetricsCollection.createIndex(new Document("commandType", 1));
        errorLogsCollection.createIndex(new Document("errorType", 1));

        // Индексы для хранения необработанных API-ответов
        apiDataCollection.createIndex(new Document("timestamp", -1));
        apiDataCollection.createIndex(new Document("processed", 1));
        apiDataCollection.createIndex(new Document("response.metadata.catalog_type", 1));
    }

    // Логирование события API-запроса
    public void logApiRequest(String eventType, Map<String, Object> metadata) {
        Document doc = new Document(metadata)
                .append("eventType", eventType)
                .append("timestamp", new Date());

        if (metadata.containsKey("catalogType")) {
            doc.append("catalogType", metadata.get("catalogType"));
        }

        apiRequestMetricsCollection.insertOne(doc);
    }

    // Логирование метрик продукта
    public void logProductEvent(String eventType, Map<String, Object> metadata) {
        try {
            Document metricDoc = new Document(metadata)
                    .append("eventType", eventType)
                    .append("timestamp", new Date());

            productMetricsCollection.insertOne(metricDoc);
        } catch (Exception e) {
            // Логируем ошибку при попытке логирования
            logError("product_metric_log_failed", eventType, e, metadata);
        }
    }

    // Логирование команд от пользователей
    public void logCommand(String eventType, Map<String, Object> metadata) {
        try {
            Document metricDoc = new Document(metadata)
                    .append("eventType", eventType)
                    .append("timestamp", new Date());

            commandMetricsCollection.insertOne(metricDoc);
        } catch (Exception e) {
            logError("command_metric_log_failed", eventType, e, metadata);
        }
    }

    // Сохранение сырых API-ответов для последующего анализа
    public void logApiRawData(String endpoint, String request, String response, String traceId) {
        Document doc = new Document()
                .append("timestamp", new Date())
                .append("endpoint", endpoint)
                .append("request", request)
                .append("response", Document.parse(response)) // Преобразуем JSON-строку в BSON
                .append("processed", false)
                .append("traceId", traceId);

        apiDataCollection.insertOne(doc);
    }

    // Метод логирования ошибок
    public void logError(String errorType, String eventType, Exception e, Map<String, Object> originalMetadata) {
        Document errorDoc = new Document()
                .append("timestamp", new Date())
                .append("errorType", errorType)
                .append("eventType", eventType)
                .append("error", e.toString())
                .append("stackTrace", Arrays.toString(e.getStackTrace()))
                .append("originalMetadata", originalMetadata);

        errorLogsCollection.insertOne(errorDoc);
    }

    // Обновление/добавление продукта в коллекции, с логированием
    public void saveOrUpdateProduct(Marketplace marketplace, String query,
                                    String productId, String name, int price,
                                    String brand, int rating, String traceId) {
        try {
            MongoCollection<Document> collection = marketplace == Marketplace.WILDBERRIES ?
                    wbProductsCollection : ozonProductsCollection;

            // Документ с описанием товара
            Document productDoc = new Document()
                    .append("productId", productId)
                    .append("name", name)
                    .append("price", price)
                    .append("brand", brand)
                    .append("rating", rating)
                    .append("marketplace", marketplace.name())
                    .append("lastUpdated", new Date());

            // Обновляем или вставляем документ по ключу query
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

            // Логируем успешное обновление продукта
            logProductEvent("product_updated", createMetadata(traceId, null)
                    .with("marketplace", marketplace.name())
                    .with("productId", productId)
                    .with("price", price)
                    .build());
        } catch (Exception e) {
            // Логируем ошибку при обновлении
            logProductEvent("product_update_failed", createMetadata(traceId, null)
                    .with("error", e.getMessage())
                    .with("productId", productId)
                    .build());
        }
    }

    // Получение статистики поиска по маркетплейсу и запросу
    public Document getSearchStats(Marketplace marketplace, String query) {
        return (marketplace == Marketplace.WILDBERRIES ?
                wbProductsCollection : ozonProductsCollection)
                .find(eq("query", query))
                .first();
    }

    public MetadataBuilder createMetadata(String traceId, Long chatId) {
        return new MetadataBuilder()
                .with("traceId", traceId)
                .with("chatId", chatId);
    }

    public static class MetadataBuilder {
        private final Map<String, Object> metadata = new HashMap<>();

        public MetadataBuilder with(String key, Object value) {
            if (value != null) {
                metadata.put(key, value);
            }
            return this;
        }

        public Map<String, Object> build() {
            return metadata;
        }
    }
}
