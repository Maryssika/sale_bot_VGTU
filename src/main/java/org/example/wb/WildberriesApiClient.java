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
    // Логгер для отладки
    private static final Logger logger = Logger.getLogger(WildberriesApiClient.class.getName());
    private static final String WB_SEARCH_API = "https://search.wb.ru/exactmatch/ru/male/v4/search";

    // Таймаут запроса и максимальное число повторов
    private static final int TIMEOUT_SECONDS = 10;
    private static final int MAX_RETRIES = 3;

    private final HttpClient httpClient;
    private final MongoDBService mongoDBService;

    // Инициализация HTTP-клиента и подключения к MongoDB
    public WildberriesApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
        this.mongoDBService = new MongoDBService();
    }

    // Запрос к WB и обработка результатов
    public String searchProduct(String query, long chatId, String traceId) {
        long startTime = System.currentTimeMillis();
        String endpoint = "/exactmatch/ru/male/v4/search";

        if (query == null || query.trim().isEmpty()) {
            mongoDBService.logCommand("empty_query", mongoDBService.createMetadata(traceId, chatId).build());
            return "Поисковый запрос не может быть пустым";
        }

        // Кодируем запрос и формируем URL
        String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        String requestUrl = WB_SEARCH_API + "?query=" + encodedQuery + "&resultset=catalog&dest=-1257786";

        // Повторяем попытки до MAX_RETRIES
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Логируем начало запроса
                mongoDBService.logApiRequest("api_request_start", mongoDBService.createMetadata(traceId, chatId)
                        .with("endpoint", endpoint)
                        .with("query", query)
                        .with("attempt", attempt)
                        .build());

                // Сборка и отправка запроса
                HttpRequest request = buildRequest(requestUrl);
                HttpResponse<String> response = sendRequest(request);

                mongoDBService.logApiRawData(endpoint, request.uri().toString(), response.body(), traceId);

                if (response.statusCode() == 200) {
                    String result = processSuccessfulResponse(response.body(), query, chatId, traceId);

                    // Логируем успешный результат
                    mongoDBService.logApiRequest("api_request_success", mongoDBService.createMetadata(traceId, chatId)
                            .with("endpoint", endpoint)
                            .with("query", query)
                            .with("executionTimeMs", System.currentTimeMillis() - startTime)
                            .with("attempts", attempt)
                            .build());

                    return result;
                } else {
                    // Логируем ошибку, если не 200 OK
                    mongoDBService.logApiRequest("api_request_error", mongoDBService.createMetadata(traceId, chatId)
                            .with("statusCode", response.statusCode())
                            .with("error", response.body())
                            .with("attempt", attempt)
                            .build());
                    return handleErrorResponse(response);
                }
            } catch (IOException | InterruptedException e) {
                // Логируем исключение
                mongoDBService.logApiRequest("api_request_exception", mongoDBService.createMetadata(traceId, chatId)
                        .with("error", e.getMessage())
                        .with("attempt", attempt)
                        .with("stackTrace", Arrays.toString(e.getStackTrace()))
                        .build());

                // Если попытки закончились — сообщаем об ошибке
                if (attempt == MAX_RETRIES) {
                    return "Не удалось выполнить запрос после нескольких попыток";
                }

                try {
                    Thread.sleep(1000 * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "Операция была прервана";
                }
            }
        }

        return "Произошла ошибка при поиске товаров";
    }

    // Построение HTTP-запроса
    private HttpRequest buildRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
    }

    // Отправка HTTP-запроса
    private HttpResponse<String> sendRequest(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // Обработка успешного JSON-ответа от API
    private String processSuccessfulResponse(String jsonResponse, String query, long chatId, String traceId) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject data = jsonObject.optJSONObject("data");

            // Если нет блока data, значит что-то пошло не так
            if (data == null) {
                mongoDBService.logApiRequest("api_data_missing", mongoDBService.createMetadata(traceId, chatId)
                        .with("query", query)
                        .with("responseSample", jsonResponse.substring(0, 100))
                        .build());
                return "Некорректный ответ от сервера";
            }

            // Логируем метаданные каталога (если есть)
            if (data.has("metadata")) {
                JSONObject metadata = data.getJSONObject("metadata");
                mongoDBService.logApiRequest("api_metadata", mongoDBService.createMetadata(traceId, chatId)
                        .with("catalogType", metadata.optString("catalog_type"))
                        .with("catalogValue", metadata.optString("catalog_value"))
                        .build());
            }

            // Извлекаем список продуктов
            JSONArray products = data.optJSONArray("products");
            if (products == null || products.isEmpty()) {
                return "Товары по запросу '" + query + "' не найдены";
            }

            // Парсим список продуктов и возвращаем отформатированную строку
            List<Product> productList = parseProducts(products, query, chatId, traceId);
            return formatResults(productList, query);

        } catch (Exception e) {
            // Логируем ошибки при парсинге
            mongoDBService.logApiRequest("response_processing_error", mongoDBService.createMetadata(traceId, chatId)
                    .with("error", e.getMessage())
                    .with("query", query)
                    .with("stackTrace", Arrays.toString(e.getStackTrace()))
                    .build());
            return "Ошибка при обработке результатов";
        }
    }

    // Парсинг до 5 продуктов из ответа
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

                // Сохраняем продукт в MongoDB
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
                // Логируем ошибку при разборе конкретного товара
                mongoDBService.logApiRequest("product_parsing_error", mongoDBService.createMetadata(traceId, chatId)
                        .with("error", e.getMessage())
                        .with("productIndex", i)
                        .with("stackTrace", Arrays.toString(e.getStackTrace()))
                        .build());
            }
        }
        return productList;
    }

    // Формирование строки с результатами для ответа пользователю
    private String formatResults(List<Product> products, String query) {
        StringBuilder result = new StringBuilder();
        result.append("🔍 Результаты поиска '").append(query).append("':\n\n");

        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            result.append(String.format(
                    "%d. %s\n💰 Цена: %,d ₽\n🏷 Бренд: %s\n⭐ Рейтинг: %d/5\n🔗 https://www.wildberries.ru/catalog/%s/detail.aspx\n\n",
                    i + 1, p.getName(), p.getPrice(), p.getBrand(), p.getRating(), p.getId()
            ));
        }

        // Добавляем статистику поиска
        Document stats = mongoDBService.getSearchStats(MongoDBService.Marketplace.WILDBERRIES, query);
        if (stats != null) {
            result.append(String.format(
                    "\n📊 Этот запрос искали %d раз(а)",
                    stats.getInteger("searchCount", 1)
            ));
        }

        return result.toString();
    }

    // Формирование сообщения об ошибке при плохом статус-коде
    private String handleErrorResponse(HttpResponse<String> response) {
        return String.format(
                "Ошибка при поиске товара. Код: %d\nОтвет сервера: %s",
                response.statusCode(),
                response.body()
        );
    }

    // Внутренний класс для представления товара
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