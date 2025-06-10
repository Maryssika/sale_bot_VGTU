package org.example;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

public class GoogleShoppingParser2 {

    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.driver", "C:\\Users\\zenko\\Downloads\\chromedriver-win32\\chromedriver-win32\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        // options.addArguments("--headless=new"); // Раскомментировать, если нужен headless
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--remote-debugging-port=0");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);
            String url = "https://www.google.com/search?tbm=shop&q=смартфон";
            System.out.println("Начинаем парсинг URL: " + url);

            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div[data-docid]")));

            List<WebElement> items = driver.findElements(By.cssSelector("div[data-docid]"));
            System.out.println("Найдено товаров: " + items.size());

            for (WebElement item : items) {
                try {
                    String title = item.findElement(By.cssSelector("h4")).getText();
                    String price = item.findElement(By.cssSelector("span.a8Pemb")).getText();

                    System.out.println("Название товара: " + title);
                    System.out.println("Цена: " + price);
                    System.out.println();
                } catch (Exception e) {
                    System.out.println("Не удалось извлечь данные для одного из товаров");
                }
            }
        } catch (Exception e) {
            System.err.println("Произошла ошибка:");
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }
}