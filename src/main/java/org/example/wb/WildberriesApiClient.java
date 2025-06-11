
package org.example.wb;

import org.bson.Document;
import org.example.db.MongoDBService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WildberriesApiClient {
    private static final Logger logger = Logger.getLogger(WildberriesApiClient.class.getName());
    private static final String WB_SEARCH_API = "https://search.wb.ru/exactmatch/ru/male/v4/search";
    private static final int TIMEOUT_SECONDS = 10;
    private static final int MAX_RETRIES = 3;

    private final HttpClient httpClient;
    private final MongoDBService mongoDBService;

    public WildberriesApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
        this.mongoDBService = new MongoDBService();
    }

    public Document searchProduct(String query, long chatId, String traceId) {
        logger.info("Начинаем поиск товара по запросу: '" + query + "'");

        Document resultDocument = new Document(); // Добавляем Document для возврата результата
        if (query == null || query.trim().isEmpty()) {
            mongoDBService.logCommand("empty_query", mongoDBService.createMetadata(traceId, chatId).build());
            resultDocument.append("error", "Поисковый запрос не может быть пустым");
            return resultDocument;  // Возвращаем Document с ошибкой
        }

        // Check cache first
        List<Document> cached = mongoDBService.getCachedProducts(MongoDBService.Marketplace.WILDBERRIES, query);
        if (cached != null && !cached.isEmpty()) {
            logger.info("Найден кэш по запросу '" + query + "'; количество товаров: " + cached.size());
            resultDocument.append("foundProducts",cached); // Возвращаем кэшированные продукты
            return resultDocument;
        }

        logger.info("Кэш по запросу '" + query + "' не найден, обращаемся к API Wildberries");

        long startTime = System.currentTimeMillis();
        String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        String requestUrl = String.format("%s?query=%s&resultset=catalog&dest=-1257786", WB_SEARCH_API, encodedQuery);

        // Retry logic
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                mongoDBService.logCommand("api_request_start", mongoDBService.createMetadata(traceId, chatId)
                        .with("query", query)
                        .with("attempt", attempt)
                        .build());

                logger.info("Попытка №" + attempt + " выполнить запрос к API: " + requestUrl);
                HttpRequest request = buildRequest(requestUrl);
                HttpResponse<String> response = sendRequest(request);

                mongoDBService.logApiRawData(
                        "/exactmatch/ru/male/v4/search",
                        "query=" + encodedQuery + "&resultset=catalog&dest=-1257786",
                        response.body(),traceId
                );

                if (response.statusCode() == 200) {
                    logger.info("Успешный ответ от API (код 200)");
                    Document result = processSuccessfulResponse(response.body(), query, chatId, traceId);

                    mongoDBService.logCommand("api_request_success", mongoDBService.createMetadata(traceId, chatId)
                            .with("query", query)
                            .with("executionTimeMs", System.currentTimeMillis() - startTime)
                            .with("attempts", attempt)
                            .build());

                    return result;
                } else {
                    logger.warning("Ошибка ответа от API. Код: " + response.statusCode());
                    mongoDBService.logError("api_request_error", "http_error", new Exception("HTTP " + response.statusCode()),
                            mongoDBService.createMetadata(traceId, chatId)
                                    .with("query", query)
                                    .with("statusCode", response.statusCode())
                                    .build());

                    if (attempt == MAX_RETRIES) {
                        resultDocument.append("error","Не удалось получить данные после нескольких попыток.");
                        return resultDocument;

                    }
                }
            } catch (IOException | InterruptedException e) {
                logger.log(Level.WARNING, "Ошибка при попытке №" + attempt + ": " + e.getMessage(), e);
                mongoDBService.logError("api_request_exception", "http_exception", e,
                        mongoDBService.createMetadata(traceId, chatId)
                                .with("query", query)
                                .with("attempt", attempt)
                                .build());
                if (attempt == MAX_RETRIES) {
                    resultDocument.append("error","Не удалось выполнить запрос из-за исключения.");
                    return resultDocument;

                }


                try {
                    Thread.sleep(1000 * attempt); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        resultDocument.append("error","Не удалось получить данные после всех попыток.");
        return resultDocument;

    }

    public String getCacheInfo(String query) {
        Logger logger = Logger.getLogger(WildberriesApiClient.class.getName());
        logger.info("Запрос информации о кэше для '" + query + "'");

        String info = mongoDBService.getCacheInfo(MongoDBService.Marketplace.WILDBERRIES, query);
        if (info == null || info.isEmpty()) {
            logger.info("Кэш для '" + query + "' не найден");
            return "Кэш для '" + query + "' не найден";
        } else {
            logger.info("Информация о кэше для '" + query + "' найдена");
            return "Информация о кэше для '" + query + "':\n" + info;
        }
    }

    private HttpRequest buildRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept-Language", "ru-RU,ru;q=0.9")
                .GET()
                .build();
    }

    private HttpResponse<String> sendRequest(HttpRequest request)
            throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Document processSuccessfulResponse(String jsonResponse, String query, long chatId, String traceId) {
        Document resultDocument = new Document();
        List<Document> productsList = new ArrayList<>();

        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject data = jsonObject.optJSONObject("data");

            if (data == null) {
                logger.warning("Некорректный формат ответа: отсутствует data");
                resultDocument.append("error", "Некорректный ответ от сервера Wildberries");
                return resultDocument;
            }

            JSONArray products = data.optJSONArray("products");
            if (products == null || products.isEmpty()) {
                resultDocument.append("error", "Товары по запросу '" + query + "' не найдены");
                return resultDocument;
            }

            for (int i = 0; i < Math.min(5, products.length()); i++) {
                try {
                    JSONObject product = products.getJSONObject(i);
                    Document productDoc = new Document()
                            .append("productId", product.optString("id", ""))
                            .append("name", product.optString("name", "Название не указано"))
                            .append("price", product.optInt("priceU", 0) / 100)
                            .append("brand", product.optString("brand", "Бренд не указан"))
                            .append("rating", product.optInt("reviewRating", 0));

                    productsList.add(productDoc);

                    // Сохраняем метрику цены
                    Document priceMetric = new Document()
                            .append("productId", productDoc.getString("productId"))
                            .append("marketplace", MongoDBService.Marketplace.WILDBERRIES.name())
                            .append("price", productDoc.getInteger("price"))
                            .append("eventType", "price_update")
                            .append("timestamp", new Date());

                    mongoDBService.getProductMetricsCollection().insertOne(priceMetric);

                    mongoDBService.saveOrUpdateProduct(
                            MongoDBService.Marketplace.WILDBERRIES,
                            query,
                            productDoc.getString("productId"),
                            productDoc.getString("name"),
                            productDoc.getInteger("price"),
                            productDoc.getString("brand"),
                            productDoc.getInteger("rating"),traceId
                    );
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Ошибка обработки товара", e);
                }
            }

            resultDocument.append("foundProducts", productsList);
            resultDocument.append("query", query);
            resultDocument.append("lastUpdated", new Date());
            resultDocument.append("marketplace", MongoDBService.Marketplace.WILDBERRIES.name());

            return resultDocument;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ошибка обработки ответа", e);
            resultDocument.append("error", "Ошибка при обработке результатов поиска");
            return resultDocument;
        }
    }



}
