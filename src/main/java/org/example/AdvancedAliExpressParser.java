package org.example;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class AdvancedAliExpressParser {

    // Список User-Agents для ротации
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1"
    };

    // Список рефереров для ротации
    private static final String[] REFERERS = {
            "https://www.google.com/",
            "https://www.yandex.ru/",
            "https://www.bing.com/",
            "https://aliexpress.ru/"
    };

    public static void main(String[] args) {
        String url = "https://www.aliexpress.com/wholesale?SearchText=earrings+women";
        Random random = new Random();

        try {
            // Первый запрос с минимальными заголовками
            Connection.Response initialResponse = Jsoup.connect(url)
                    .userAgent(USER_AGENTS[random.nextInt(USER_AGENTS.length)])
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Connection", "keep-alive")
                    .timeout(30000)
                    .method(Connection.Method.GET)
                    .execute();

            // Имитация человеческого поведения - случайная задержка
            TimeUnit.SECONDS.sleep(3 + random.nextInt(5));

            // Второй запрос с полученными cookies и дополнительными заголовками
            Document doc = Jsoup.connect(url)
                    .cookies(initialResponse.cookies())
                    .userAgent(USER_AGENTS[random.nextInt(USER_AGENTS.length)])
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Referer", REFERERS[random.nextInt(REFERERS.length)])
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("Cache-Control", "max-age=0")
                    .timeout(30000)
                    .get();

            System.out.println("Успешно! Заголовок страницы: " + doc.title());
            System.out.println("Полученные cookies: " + initialResponse.cookies());

        } catch (IOException | InterruptedException e) {
            System.err.println("Ошибка при запросе: " + e.getMessage());

            // Расширенная диагностика
            if (e.getMessage().contains("Too many redirects")) {
                System.err.println("\n=== Рекомендации ===");
                System.err.println("1. Используйте качественные резидентские прокси");
                System.err.println("2. Увеличьте задержки между запросами до 10-30 секунд");
                System.err.println("3. Добавьте обработку JavaScript через Selenium");
                System.err.println("4. Попробуйте эмулировать разные устройства");
                System.err.println("5. Рассмотрите использование API вместо парсинга");
            }
        }
    }
}
