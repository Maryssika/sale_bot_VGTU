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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
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

    public Document searchProduct(String query) {
        Document resultDocument = new Document();
        if (query == null || query.trim().isEmpty()) {
            resultDocument.append("error", "Поисковый запрос не может быть пустым");
            return resultDocument;
        }

        String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        String requestUrl = String.format("%s?query=%s&resultset=catalog&dest=-1257786",
                WB_SEARCH_API, encodedQuery);

        logger.log(Level.INFO, "Выполняем запрос к WB API: {0}", requestUrl);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest request = buildRequest(requestUrl);
                HttpResponse<String> response = sendRequest(request);

                mongoDBService.saveApiRawData(
                        "/exactmatch/ru/male/v4/search",
                        "query=" + encodedQuery + "&resultset=catalog&dest=-1257786",
                        response.body()
                );

                if (response.statusCode() == 200) {
                    return processSuccessfulResponse(response.body(), query);
                } else {
                    resultDocument.append("error", handleErrorResponse(response));
                    return resultDocument;
                }
            } catch (IOException | InterruptedException e) {
                logger.log(Level.WARNING, "Ошибка при попытке {0}: {1}",
                        new Object[]{attempt, e.getMessage()});
                if (attempt == MAX_RETRIES) {
                    resultDocument.append("error", "Произошла ошибка при поиске товаров. Попробуйте позже.");
                    return resultDocument;
                }
                try {
                    Thread.sleep(1000 * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        resultDocument.append("error", "Не удалось выполнить запрос после нескольких попыток");
        return resultDocument;
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

    private Document processSuccessfulResponse(String jsonResponse, String query) {
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
                            productDoc.getInteger("rating")
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

    private String handleErrorResponse(HttpResponse<String> response) {
        String errorMsg = String.format(
                "Ошибка при поиске товара. Код: %d\nОтвет сервера: %s",
                response.statusCode(),
                response.body()
        );
        logger.warning(errorMsg);
        mongoDBService.logError("api_error", errorMsg,
                Map.of("statusCode", response.statusCode()));
        return errorMsg;
    }
}