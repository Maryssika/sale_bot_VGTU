package org.example;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

public class GoogleShoppingParser {
    public static void main(String[] args) {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36");
        options.addArguments("--window-size=810,540");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get("https://www.google.com/search?tbm=shop&q=смартфон");

            // Debugging output
            System.out.println("Page title: " + driver.getTitle());

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

            // Updated selectors based on the provided HTML
            String[] containerSelectors = {
                    "div[jsname='dQK82e']",
                    "div[class='MtXiu mZ9c3d wYFOId M919M W5CKGc wTrwWd']"
            };

            WebElement firstItem = null;

            for (String selector : containerSelectors) {
                try {
                    System.out.println("Trying selector: " + selector);
                    firstItem = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
                    System.out.println("Found element with selector: " + selector);
                    break;
                } catch (TimeoutException e) {
                    System.out.println("Element not found with selector: " + selector);
                }
            }

            if (firstItem == null) {
                throw new RuntimeException("Не удалось найти контейнер товаров");
            }

            // Use the correct selectors to find all product items
            List<WebElement> items = driver.findElements(By.cssSelector("div[jsname='dQK82e']"));
            System.out.println("Найдено товаров: " + items.size());

            for (WebElement item : items) {
                try {
                    // Extract title and price using the correct selectors
                    String title = item.findElement(By.cssSelector("div.gkQHve.SsM98d.RmEs5b")).getText();
                    String price = item.findElement(By.cssSelector("span.lmQWe")).getText();
                    System.out.println("Название: " + title);
                    System.out.println("Цена: " + price);
                    System.out.println("------");
                } catch (Exception e) {
                    System.out.println("Ошибка парсинга товара: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Критическая ошибка: ");
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
