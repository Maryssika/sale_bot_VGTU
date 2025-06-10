package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class AdvancedAliExpressParser {
    public static void main(String[] args) {
        try {
            parseGoogleShopping("смартфон");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void parseGoogleShopping(String searchQuery) throws IOException, InterruptedException {
        String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
        String url = "https://www.google.com/search?tbm=shop&q=" + encodedQuery;

        // Добавляем случайные задержки между запросами
        Thread.sleep(1000 + (long)(Math.random() * 2000));

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_2)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", getRandomUserAgent())
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Referer", "https://www.google.com/")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Document doc = Jsoup.parse(response.body());
            List<Product> products = parseProducts(doc);
            for (Product product : products) {
                System.out.println(product);
            }
        } else if (response.statusCode() == 429) {
            System.out.println("Слишком много запросов. Попробуйте позже или используйте прокси.");
        } else {
            System.out.println("Ошибка HTTP: " + response.statusCode());
        }
    }

    private static List<Product> parseProducts(Document doc) {
        List<Product> products = new ArrayList<>();
        Elements items = doc.select("div.sh-dgr__grid-result, div.KZmu8e, div.sh-pr__product-results-grid, div.sh-dgr__content");

        if (items.isEmpty()) {
            System.out.println("Товары не найдены. Проанализируйте сохраненный файл google_shopping.html");
            return products;
        }

        for (Element item : items) {
            String title = item.selectFirst("h3, h4") != null ? item.selectFirst("h3, h4").text() : "Не указано";
            String price = item.selectFirst("span.a8Pemb, span.kHxwFf, span.HRLxBb") != null ? item.selectFirst("span.a8Pemb, span.kHxwFf, span.HRLxBb").text() : "Не указана";
            String link = item.selectFirst("a.shntl, a.Lq5OHe") != null ? item.selectFirst("a.shntl, a.Lq5OHe").attr("href") : "Не найдена";
            String seller = item.selectFirst("span.aULzje") != null ? item.selectFirst("span.aULzje").text() : "Не указан";
            String rating = item.selectFirst("span.z3HNkc") != null ? item.selectFirst("span.z3HNkc").attr("aria-label") : "Не указан";
            String reviews = item.selectFirst("span.hx8QM") != null ? item.selectFirst("span.hx8QM").text() : "Не указано";
            String returnPolicy = item.selectFirst("div.wuglHf") != null ? item.selectFirst("div.wuglHf").text() : "Не указана";

            if (!link.startsWith("http")) {
                link = "https://www.google.com" + link;
            }

            Product product = new Product(title, price, link, seller, rating, reviews, returnPolicy);
            products.add(product);
        }
        return products;
    }

    private static String getRandomUserAgent() {
        String[] agents = {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/120.0",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"
        };
        return agents[(int)(Math.random() * agents.length)];
    }

    static class Product {
        private String title;
        private String price;
        private String link;
        private String seller;
        private String rating;
        private String reviews;
        private String returnPolicy;

        public Product(String title, String price, String link, String seller, String rating, String reviews, String returnPolicy) {
            this.title = title;
            this.price = price;
            this.link = link;
            this.seller = seller;
            this.rating = rating;
            this.reviews = reviews;
            this.returnPolicy = returnPolicy;
        }

        @Override
        public String toString() {
            return "Название: " + title + "\n" +
                    "Цена: " + price + "\n" +
                    "Ссылка: " + link + "\n" +
                    "Продавец: " + seller + "\n" +
                    "Рейтинг: " + rating + "\n" +
                    "Отзывы: " + reviews + "\n" +
                    "Возврат: " + returnPolicy + "\n" +
                    "------------------------------";
        }
    }
}
