package org.example.wb;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class WildberriesApiClient {
    private static final String WB_SEARCH_API = "https://search.wb.ru/exactmatch/ru/male/v4/search";
    private final HttpClient httpClient;

    public WildberriesApiClient() {
        this.httpClient = HttpClient.newHttpClient();
    }

    public String searchProduct(String query) throws IOException, InterruptedException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String requestUrl = String.format("%s?query=%s&resultset=catalog&dest=-1257786",
                WB_SEARCH_API, encodedQuery);

        System.out.println("Request URL: " + requestUrl); // Логирование URL

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept-Language", "ru-RU,ru;q=0.9")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            System.out.println("Raw response: " + response.body());
            return parseProducts(response.body());
        } else {
            return "Ошибка при поиске товара. Код: " + response.statusCode() +
                    "\nОтвет сервера: " + response.body();
        }
    }

    private String parseProducts(String jsonResponse) {
        StringBuilder result = new StringBuilder();
        JSONObject jsonObject = new JSONObject(jsonResponse);
        JSONObject data = jsonObject.getJSONObject("data");
        JSONArray products = data.getJSONArray("products");

        if (products.length() == 0) {
            return "Товары не найдены";
        }

        result.append("Найдено товаров: ").append(products.length()).append("\n\n");

        for (int i = 0; i < Math.min(5, products.length()); i++) {
            JSONObject product = products.getJSONObject(i);
            String name = product.getString("name");
            int price = product.getInt("priceU") / 100;
            String brand = product.getString("brand");
            int rating = product.getInt("reviewRating");

            String id = String.valueOf(product.get("id"));
            String link = "https://www.wildberries.ru/catalog/" + id + "/detail.aspx";

            result.append(String.format(
                    "%d. %s\nЦена: %d ₽\nБренд: %s\nРейтинг: %d/5\nСсылка: %s\n\n",
                    i + 1, name, price, brand, rating, link
            ));
        }

        return result.toString();
    }
}