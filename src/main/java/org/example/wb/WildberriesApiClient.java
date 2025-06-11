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

    public String searchProduct(String query, long chatId, String traceId) {
        logger.info("Начинаем поиск товара по запросу: '" + query + "'");

        if (query == null || query.trim().isEmpty()) {
            mongoDBService.logCommand("empty_query", mongoDBService.createMetadata(traceId, chatId).build());
            return "Поисковый запрос не может быть пустым";
        }

        // Check cache first
        List<Document> cached = mongoDBService.getCachedProducts(MongoDBService.Marketplace.WILDBERRIES, query);
        if (cached != null && !cached.isEmpty()) {
            logger.info("Найден кэш по запросу '" + query + "'; количество товаров: " + cached.size());
            List<Product> products = new ArrayList<>();
            for (Document d : cached) {
                products.add(new Product(
                        d.getString("productId"),
                        d.getString("name"),
                        getIntSafe(d, "price"),
                        d.getString("brand"),
                        getIntSafe(d, "rating")
                ));
            }
            logger.info("Возвращаем результаты из кэша");
            return formatResults(products, query);
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

                if (response.statusCode() == 200) {
                    logger.info("Успешный ответ от API (код 200)");
                    String result = processSuccessfulResponse(response.body(), query, chatId, traceId);

                    mongoDBService.logCommand("api_request_success", mongoDBService.createMetadata(traceId, chatId)
                            .with("query", query)
                            .with("executionTimeMs", System.currentTimeMillis() - startTime)
                            .with("attempts", attempt)
                            .build());

                    return result;
                } else {
                    logger.warning("Ошибка ответа от API. Код: " + response.statusCode());
                    mongoDBService.logCommand("api_request_error", mongoDBService.createMetadata(traceId, chatId)
                            .with("statusCode", response.statusCode())
                            .with("error", response.body())
                            .with("attempt", attempt)
                            .build());
                    return handleErrorResponse(response, traceId);
                }
            } catch (IOException | InterruptedException e) {
                logger.log(Level.WARNING, "Ошибка при выполнении запроса (попытка " + attempt + ")", e);
                mongoDBService.logCommand("api_request_exception", mongoDBService.createMetadata(traceId, chatId)
                        .with("error", e.getMessage())
                        .with("attempt", attempt)
                        .with("stackTrace", Arrays.toString(e.getStackTrace()))
                        .build());

                if (attempt == MAX_RETRIES) {
                    logger.severe("Все попытки выполнены, возвращаем сообщение об ошибке");
                    return "Не удалось выполнить запрос после нескольких попыток";
                }
            }
        }
        return "Произошла неизвестная ошибка при выполнении запроса";
    }

    private HttpRequest buildRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
    }

    private HttpResponse<String> sendRequest(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String processSuccessfulResponse(String jsonResponse, String query, long chatId, String traceId) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject data = jsonObject.optJSONObject("data");

            if (data == null) {
                mongoDBService.logCommand("api_data_missing", mongoDBService.createMetadata(traceId, chatId)
                        .with("query", query)
                        .with("responseSample", jsonResponse.substring(0, 100))
                        .build());
                logger.warning("Некорректный ответ от сервера Wildberries: отсутствует поле 'data'");
                return "Некорректный ответ от сервера Wildberries";
            }

            JSONArray products = data.optJSONArray("products");
            if (products == null || products.isEmpty()) {
                logger.info("По запросу '" + query + "' товары не найдены");
                return "Товары по запросу '" + query + "' не найдены";
            }

            List<Product> productList = parseProducts(products, query, chatId, traceId);
            logger.info("Обработано " + productList.size() + " товаров, возвращаем результаты");
            return formatResults(productList, query);

        } catch (Exception e) {
            mongoDBService.logCommand("response_processing_error", mongoDBService.createMetadata(traceId, chatId)
                    .with("error", e.getMessage())
                    .with("query", query)
                    .with("stackTrace", Arrays.toString(e.getStackTrace()))
                    .build());
            return "Ошибка при обработке результатов";
        }
    }

    private List<Product> parseProducts(JSONArray products, String query, long chatId, String traceId) {
        List<Product> productList = new ArrayList<>();
        for (int i = 0; i < Math.min(5, products.length()); i++) {
            try {
                JSONObject product = products.getJSONObject(i);
                Product p = new Product(
                        product.optString("id", ""),
                        product.optString("name", "Название не указано"),
                        product.optInt("priceU", 0) / 100,
                        product.optString("brand", "Бренд не указан"),
                        product.optInt("reviewRating", 0)
                );
                productList.add(p);

                mongoDBService.saveOrUpdateProduct(
                        MongoDBService.Marketplace.WILDBERRIES,
                        query,
                        p.getId(),
                        p.getName(),
                        p.getPrice(),
                        p.getBrand(),
                        p.getRating(),
                        traceId
                );
            } catch (Exception e) {
                logger.log(Level.WARNING, "Ошибка обработки товара " + i, e);
            }
        }
        return productList;
    }

    private String formatResults(List<Product> products, String query) {
        StringBuilder result = new StringBuilder("🔍 Результаты поиска '" + query + "':\n\n");
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            result.append(String.format(
                    "%d. %s\n💰 Цена: %,d ₽\n🏷 Бренд: %s\n⭐ Рейтинг: %d/5\n🔗 https://www.wildberries.ru/catalog/%s/detail.aspx\n\n",
                    i + 1, p.getName(), p.getPrice(), p.getBrand(), p.getRating(), p.getId()
            ));
        }
        return result.toString();
    }

    private String handleErrorResponse(HttpResponse<String> response, String traceId) {
        String errorMsg = String.format(
                "Ошибка при поиске товара. Код: %d\nОтвет сервера: %s",
                response.statusCode(),
                response.body()
        );

        logger.warning(errorMsg);
        mongoDBService.logError("api_error", "Ошибка API", new Exception(errorMsg),
                Map.of("statusCode", response.statusCode()));
        return errorMsg;
    }

    private int getIntSafe(Document doc, String key) {
        try {
            return doc.getInteger(key, 0);
        } catch (ClassCastException e) {
            return 0;
        }
    }

    public String getCacheInfo(String query) {
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

    private static class Product {
        private final String id;
        private final String name;
        private final int price;
        private final String brand;
        private final int rating;

        public Product(String id, String name, int price, String brand, int rating) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.brand = brand;
            this.rating = rating;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public int getPrice() { return price; }
        public String getBrand() { return brand; }
        public int getRating() { return rating; }
    }
}