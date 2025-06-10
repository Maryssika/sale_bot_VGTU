package org.example;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import io.github.bonigarcia.wdm.WebDriverManager;
import java.time.Duration;
import java.util.List;

public class GoogleShoppingParser {
    public static void main(String[] args) {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get("https://www.google.com/search?tbm=shop&q=смартфон");

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.sh-dgr__grid-result")));

            List<WebElement> items = driver.findElements(By.cssSelector("div.sh-dgr__grid-result"));
            System.out.println("Найдено товаров: " + items.size());

            for (WebElement item : items) {
                try {
                    String title = item.findElement(By.cssSelector("h3.tAxDx")).getText();
                    String price = item.findElement(By.cssSelector("span.kHxwFf")).getText();
                    System.out.println("Название: " + title);
                    System.out.println("Цена: " + price);
                } catch (Exception e) {
                    System.out.println("Ошибка парсинга товара");
                }
            }
        } finally {
            driver.quit();
        }
    }
}