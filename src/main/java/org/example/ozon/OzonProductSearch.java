package org.example.ozon;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

public class OzonProductSearch {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private static final String BASE_URL = "https://www.ozon.ru";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Введите название товара для поиска на Ozon:");
        String productName = scanner.nextLine();

        try {
            System.out.println("\nРезультаты поиска:\n");
            searchProductsOnOzon(productName);
        } catch (Exception e) {
            System.out.println("Произошла ошибка при поиске товара: " + e.getMessage());
        }
    }

    public static void searchProductsOnOzon(String productName) throws IOException, InterruptedException {
        // Кодируем название товара для URL
        String encodedProductName = URLEncoder.encode(productName, "UTF-8");
        String searchUrl = BASE_URL + "/search/?text=" + encodedProductName + "&from_global=true";

        // Создаем HTTP-клиент с заголовками
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(searchUrl))
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .build();

        // Отправляем запрос и получаем ответ
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Парсим HTML с помощью Jsoup
        Document doc = Jsoup.parse(response.body());

        // Ищем все карточки товаров
        Elements products = doc.select("div[data-widget='searchResultsV2'] a[href*='/product/']");

        if (products.isEmpty()) {
            System.out.println("Товары не найдены");
            return;
        }

        System.out.println("Найдено товаров: " + products.size() + "\n");

        // Ограничиваем вывод 5 первыми товарами
        for (int i = 0; i < Math.min(5, products.size()); i++) {
            Element product = products.get(i);

            // Получаем ссылку на товар
            String productLink = BASE_URL + product.attr("href");

            // Получаем название товара
            String title = product.selectFirst("span.tsBody500").text();

            // Получаем цену (основной селектор)
            String price = "Цена не указана";
            Element priceElement = product.selectFirst("span.c3118-a0"); // Основной селектор цены

            if (priceElement == null) {
                // Альтернативный селектор цены
                priceElement = product.selectFirst("span[data-widget='webPrice']");
            }

            if (priceElement != null) {
                price = priceElement.text().replaceAll("[^0-9₽]", "").trim();
            }

            System.out.println((i+1) + ". " + title);
            System.out.println("Цена: " + price);
            System.out.println("Ссылка: " + productLink + "\n");
        }
    }
}