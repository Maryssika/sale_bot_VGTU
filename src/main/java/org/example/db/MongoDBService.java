package org.example.db;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Updates.*;

public class MongoDBService {
    // Database collections for different data types
    private final MongoCollection<Document> wbProductsCollection;
    private final MongoCollection<Document> ozonProductsCollection;
    private final MongoCollection<Document> apiRequestMetricsCollection;
    private final MongoCollection<Document> productMetricsCollection;
    private final MongoCollection<Document> commandMetricsCollection;
    private final MongoCollection<Document> errorLogsCollection;
    private final MongoCollection<Document> apiDataCollection;
    private final MongoCollection<Document> userLogsCollection;
    private final MongoCollection<Document> productMetricsCollection;

    // Supported marketplaces
    public enum Marketplace {
        WILDBERRIES,
        OZON
    }

    // Constructor connects to MongoDB and initializes collections
    public MongoDBService() {
        MongoClient mongoClient = MongoClients.create("mongodb+srv://kasatkinnikita13:T9lswrFtZMLgl6Wj@cluster0.vx90u17.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0");
        MongoDatabase saleBotDB = mongoClient.getDatabase("sale_bot");
        MongoDatabase saleBotInfoDB = mongoClient.getDatabase("sale_bot_info");

        // Main product collections
        this.wbProductsCollection = saleBotDB.getCollection("wb_products");
        this.ozonProductsCollection = saleBotDB.getCollection("ozon_products");
        this.productMetricsCollection = saleBotDB.getCollection("product_metrics");

        // Collections for metrics, logs and API
        this.apiRequestMetricsCollection = saleBotInfoDB.getCollection("api_request_metrics");
        this.productMetricsCollection = saleBotInfoDB.getCollection("product_metrics");
        this.commandMetricsCollection = saleBotInfoDB.getCollection("command_metrics");
        MongoDatabase saleBotInfoDB = mongoClient.getDatabase("sale_bot_info");
        this.apiDataCollection = saleBotInfoDB.getCollection("api_data");
        this.errorLogsCollection = saleBotInfoDB.getCollection("error_logs");
        this.apiDataCollection = saleBotInfoDB.getCollection("api_data");
        this.userLogsCollection = saleBotDB.getCollection("user_logs");

        // Create indexes
        createIndexes();
    }

    private void createIndexes() {
        wbProductsCollection.createIndex(new Document("query", 1));
        wbProductsCollection.createIndex(new Document("foundProducts.productId", 1));
        wbProductsCollection.createIndex(new Document("lastUpdated", -1));

        ozonProductsCollection.createIndex(new Document("query", 1));
        ozonProductsCollection.createIndex(new Document("foundProducts.productId", 1));
        ozonProductsCollection.createIndex(new Document("lastUpdated", -1));

        // Indexes for API metrics
        apiRequestMetricsCollection.createIndex(new Document("timestamp", -1));
        apiRequestMetricsCollection.createIndex(new Document("endpoint", 1));
        apiRequestMetricsCollection.createIndex(new Document("catalogType", 1));

        // Indexes for products and commands
        productMetricsCollection.createIndex(new Document("productId", 1));
        commandMetricsCollection.createIndex(new Document("commandType", 1));

        // Indexes for errors
        errorLogsCollection.createIndex(new Document("errorType", 1));
        productMetricsCollection.createIndex(new Document("productId", 1));
        productMetricsCollection.createIndex(new Document("marketplace", 1));
        productMetricsCollection.createIndex(new Document("timestamp", -1));

        errorLogsCollection.createIndex(new Document("timestamp", -1));

        // Indexes for API data
        apiDataCollection.createIndex(new Document("timestamp", -1));
        apiDataCollection.createIndex(new Document("processed", 1));
        apiDataCollection.createIndex(new Document("response.metadata.catalog_type", 1));
    }

    public void logUserAction(long userId, String username, String firstName,
                              String lastName, String action, String query, String message) {
        Document logDoc = new Document()
                .append("userId", userId)
                .append("username", username)
                .append("firstName", firstName)
                .append("lastName", lastName)
                .append("action", action)
                .append("query", query)
                .append("message", message)
                .append("timestamp", new Date());

        userLogsCollection.insertOne(logDoc);
    }

    // Log API request event
    public void logApiRequest(String eventType, Map<String, Object> metadata) {
        Document doc = new Document(metadata)
                .append("eventType", eventType)
                .append("timestamp", new Date());

        if (metadata.containsKey("catalogType")) {
            doc.append("catalogType", metadata.get("catalogType"));
        }

        apiRequestMetricsCollection.insertOne(doc);
    }

    // Log product metrics
    public void logProductEvent(String eventType, Map<String, Object> metadata) {
        try {
            Document metricDoc = new Document(metadata)
                    .append("eventType", eventType)
                    .append("timestamp", new Date());

            productMetricsCollection.insertOne(metricDoc);
        } catch (Exception e) {
            // Log error if metric logging fails
            logError("product_metric_log_failed", eventType, e, metadata);
        }
    }

    // Log user commands
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

    // Save raw API responses for analysis
    public void logApiRawData(String endpoint, String request, String response, String traceId) {
        Document doc = new Document()
                .append("timestamp", new Date())
                .append("endpoint", endpoint)
                .append("request", request)
                .append("response", Document.parse(response)) // Convert JSON string to BSON
                .append("processed", false)
                .append("traceId", traceId);

        apiDataCollection.insertOne(doc);
    }

    // Error logging method
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

    public void logSearchQuery(String query, String traceId) {
        try {
            Document searchDoc = new Document()
                    .append("query", query)
                    .append("traceId", traceId)
                    .append("timestamp", new Date());

            commandMetricsCollection.insertOne(searchDoc);
        } catch (Exception e) {
            logError("search_query_log_failed", "search_query", e,
                    Map.of("query", query, "traceId", traceId));
        }
    }

    // Update/add product to collections with logging
    public void saveOrUpdateProduct(Marketplace marketplace, String query,
                                    String productId, String name, int price,
                                    String brand, int rating, String traceId) {
        try {
            MongoCollection<Document> collection = marketplace == Marketplace.WILDBERRIES ?
                    wbProductsCollection : ozonProductsCollection;

            // Product document
            Document productDoc = new Document()
                    .append("productId", productId)
                    .append("name", name)
                    .append("price", price)
                    .append("brand", brand)
                    .append("rating", rating)
                    .append("marketplace", marketplace.name())
                    .append("lastUpdated", new Date());

            // Update or insert document by query key
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

            // Log successful product update
            logProductEvent("product_updated", createMetadata(traceId, null)
                    .with("marketplace", marketplace.name())
                    .with("productId", productId)
                    .with("price", price)
                    .build());
        } catch (Exception e) {
            // Log update error
            logProductEvent("product_update_failed", createMetadata(traceId, null)
                    .with("error", e.getMessage())
                    .with("productId", productId)
                    .build());
        }
    }

    // Methods for working with product cache
    public List<Document> getCachedProducts(Marketplace marketplace, String query) {
        Document doc = getCollectionForMarketplace(marketplace)
                .find(eq("query", query))
                .projection(new Document("foundProducts", 1).append("lastUpdated", 1))
                .first();

        if (doc != null) {
            Date lastUpdated = doc.getDate("lastUpdated");
            if (lastUpdated != null && (new Date().getTime() - lastUpdated.getTime()) < 2 * 60 * 60 * 1000) {
                return doc.getList("foundProducts", Document.class);
            }
        }
        return null;
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

    public void clearOldCache(Marketplace marketplace, int hours) {
        MongoCollection<Document> collection = getCollectionForMarketplace(marketplace);
        long cutoff = System.currentTimeMillis() - (hours * 60 * 60 * 1000);
        collection.deleteMany(lt("lastUpdated", new Date(cutoff)));
    }

    // Methods for working with search and statistics
    public String getCacheInfo(Marketplace marketplace, String query) {
        Document doc = getCollectionForMarketplace(marketplace)
    public Document getSearchStats(Marketplace marketplace, String query) {
        return getCollectionForMarketplace(marketplace)
                .find(eq("query", query))
                .first();

        if (doc == null) return "Кэш не найден для запроса '" + query + "'";

        Date lastUpdated = doc.getDate("lastUpdated");
        int count = doc.getList("foundProducts", Document.class, new ArrayList<>()).size();
        int searches = doc.getInteger("searchCount", 0);

        // Format date
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

    // Helper methods
    private MongoCollection<Document> getCollectionForMarketplace(Marketplace marketplace) {
        return marketplace == Marketplace.WILDBERRIES ? wbProductsCollection : ozonProductsCollection;
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