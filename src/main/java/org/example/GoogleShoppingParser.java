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

    public String parseGoogleShopping(String query) {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36");
        options.addArguments("--window-size=810,540");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});

        WebDriver driver = new ChromeDriver(options);
        StringBuilder result = new StringBuilder();

        try {
            driver.get("https://www.google.com/search?tbm=shop&q=" + query);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

            // Wait for the presence of any item container
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div[jsname='dQK82e'], div[class*='Ez5pwe']")));

            List<WebElement> items = driver.findElements(By.cssSelector("div[jsname='dQK82e'], div[class*='Ez5pwe']"));
            result.append("Найдено товаров: ").append(items.size()).append("\n");

            // Limit the loop to the first 10 items
            int itemCount = Math.min(items.size(), 10);
            for (int i = 0; i < itemCount; i++) {
                WebElement item = items.get(i);
                try {
                    // Extract title
                    String title = item.findElement(By.cssSelector("div[class*='gkQHve']")).getText();

                    // Extract price
                    String price = item.findElement(By.cssSelector("span[class*='lmQWe']")).getText();

                    result.append("Название: ").append(title).append("\n");
                    result.append("Цена: ").append(price).append("\n");
                    result.append("------\n");
                } catch (NoSuchElementException e) {
                    result.append("Не удалось найти элементы для товара ").append(i + 1).append(": ").append(e.getMessage()).append("\n");
                }
            }
        } catch (Exception e) {
            result.append("Критическая ошибка: ").append(e.getMessage()).append("\n");
        } finally {
            driver.quit();
        }

        return result.toString();
    }
}
