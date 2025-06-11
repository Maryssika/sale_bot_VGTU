package org.example;

import org.bson.Document;
import org.example.charts.PriceHistoryService;
import org.example.db.MongoDBService;
import org.example.db.MongoDBService;
import org.example.forecast.ForecastService;
import org.example.forecast.ForecastFormatter;
import org.example.forecast.ForecastResult;
import org.example.wb.WildberriesApiClient;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Arrays;
import java.util.UUID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TelegramBot extends TelegramLongPollingBot {
    private final WildberriesApiClient wbApiClient;
    private final MongoDBService mongoDBService;
    private final ForecastService forecastService;
    private final GoogleShoppingParser googleShoppingParser;

    private final MongoDBService mongoDBService;
    private final PriceHistoryService priceHistoryService;

    public TelegramBot() {
        this.wbApiClient = new WildberriesApiClient();
        this.mongoDBService = new MongoDBService();
        this.forecastService = new ForecastService(mongoDBService);
        this.googleShoppingParser = new GoogleShoppingParser();

        this.mongoDBService = new MongoDBService();
        this.priceHistoryService = new PriceHistoryService(mongoDBService);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long startTime = System.currentTimeMillis();
            String traceId = UUID.randomUUID().toString();
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();
            User user = update.getMessage().getFrom();
            handleMessage(update);
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }

    private void handleMessage(Update update) {
        String messageText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();

            // Определяем тип действия
            String action = "command";
            String query = "";
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));

            if (messageText.startsWith("/search ")) {
                action = "search";
                query = messageText.substring(8).trim();
            } else if (messageText.startsWith("/cacheinfo ")) {
                action = "cache_info";
                query = messageText.substring(11).trim();
            } else if (messageText.startsWith("/forecast ")) {
                action = "forecast";
                query = messageText.substring(10).trim();
            }

            // Логируем действие пользователя
            mongoDBService.logUserAction(
                    user.getId(),
                    user.getUserName() != null ? user.getUserName() : "unknown",
                    user.getFirstName() != null ? user.getFirstName() : "unknown",
                    user.getLastName() != null ? user.getLastName() : "unknown",
                    action,
                    query,
                    messageText
            );

            SendMessage response = new SendMessage();
            response.setChatId(String.valueOf(chatId));

            try {
                if (messageText.startsWith("/wb ")) {
                    handleSearchCommand(messageText, chatId, traceId, response);
                } else if (messageText.startsWith("/cacheinfo ")) {
                    handleCacheInfoCommand(messageText, chatId, traceId, response);
                } else if (messageText.startsWith("/forecast ")) {
                    handleForecastCommand(messageText, chatId, traceId, response);
                } else if (messageText.startsWith("/google ")) {
                    handleGoogleShoppingCommand(messageText, chatId, traceId, response);
                } else if (messageText.equals("/start")) {
                    response.setText("Добро пожаловать! Используйте команды:\n" +
                            "/wb [запрос] - поиск товаров\n" +
                            "/cacheinfo [запрос] - информация о кэше\n" +
                            "/forecast [запрос] - прогноз цен на 7 дней\n" +
                            "/google [запрос] - поиск товаров в Google Shopping");
                } else {
                    response.setText("Неизвестная команда. Доступные команды:\n" +
                            "/wb [запрос] - поиск товаров\n" +
                            "/cacheinfo [запрос] - информация о кэше\n" +
                            "/forecast [запрос] - прогноз цен на 7 дней\n" +
                            "/google [запрос] - поиск товаров в Google Shopping");
                }
        try {
            if (messageText.startsWith("/search ")) {
                handleSearchCommand(update, message);
            } else {
                message.setText("Используйте команду /search [запрос] для поиска товаров на Wildberries");
            }
            execute(message);
        } catch (Exception e) {
            handleError(message, e);
        }
    }

    private void handleSearchCommand(Update update, SendMessage message) {
        String query = update.getMessage().getText().substring(8).trim();
        if (!query.isEmpty()) {
            Document searchResult = wbApiClient.searchProduct(query);

            if (searchResult.containsKey("error")) {
                message.setText(searchResult.getString("error"));
            } else if (searchResult.containsKey("foundProducts")) {
                List<Document> products = searchResult.getList("foundProducts", Document.class);
                if (!products.isEmpty()) {
                    message.setText("Выберите товар из результатов поиска:");
                    message.setReplyMarkup(createProductSelectionKeyboard(products));
                } else {
                    message.setText("По вашему запросу товары не найдены");
                }
            } else {
                message.setText("Произошла ошибка при поиске товаров");
            }
        } else {
            message.setText("Введите поисковый запрос после команды /search");
        }
    }

    private InlineKeyboardMarkup createProductSelectionKeyboard(List<Document> products) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Document product : products) {
            String productName = product.getString("name");
            String productId = product.getString("productId");
            Integer price = product.getInteger("price");

            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(String.format("%s - %d ₽", productName, price));
            button.setCallbackData("select_product_" + productId);
            rows.add(Collections.singletonList(button));
        }

                execute(response);

            } catch (Exception e) {
                e.printStackTrace();
                response.setText("Произошла ошибка при обработке запроса. Попробуйте позже.");
                try {
                    execute(response);
                } catch (TelegramApiException ex) {
                    ex.printStackTrace();
                }
            }
        keyboardMarkup.setKeyboard(rows);
        return keyboardMarkup;
    }

    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));

        try {
            if (callbackData.startsWith("select_product_")) {
                handleProductSelection(update, message);
            } else if (callbackData.startsWith("price_detail_")) {
                handlePriceDetails(update, message);
            } else if (callbackData.startsWith("back_to_search_")) {
                handleBackToSearch(update, message);
            }
            execute(message);
        } catch (Exception e) {
            handleError(message, e);
        }
    }

    private void handleProductSelection(Update update, SendMessage message) {
        String productId = update.getCallbackQuery().getData().substring("select_product_".length());
        Document productInfo = mongoDBService.getProductInfo(productId, MongoDBService.Marketplace.WILDBERRIES);

        if (productInfo != null) {
            String productName = productInfo.getList("foundProducts", Document.class)
                    .get(0).getString("name");
            message.setText("Выбран товар: " + productName + "\nВыберите период для просмотра цен:");
            message.setReplyMarkup(createPeriodSelectionKeyboard(productId, productInfo));
        } else {
            message.setText("Не удалось получить информацию о товаре");
        }
    }

    private InlineKeyboardMarkup createPeriodSelectionKeyboard(String productId, Document productInfo) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Row with day and week buttons
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineButton("Цены за день", "price_detail_day_" + productId));
        row1.add(createInlineButton("Цены за неделю", "price_detail_week_" + productId));

        // Row with month button
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createInlineButton("Цены за месяц", "price_detail_month_" + productId));

        // Row with back button
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createInlineButton("Назад к выбору товара", "back_to_search_" + productInfo.getString("query")));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        keyboardMarkup.setKeyboard(rows);
        return keyboardMarkup;
    }

    private void handlePriceDetails(Update update, SendMessage message) {
        String[] parts = update.getCallbackQuery().getData().split("_");
        String period = parts[2];
        String productId = parts[3];

        String priceHistory = priceHistoryService.getFormattedPriceHistory(
                productId, MongoDBService.Marketplace.WILDBERRIES, period);
        message.setText(priceHistory);

        Document productInfo = mongoDBService.getProductInfo(productId, MongoDBService.Marketplace.WILDBERRIES);
        message.setReplyMarkup(createBackButtonKeyboard(productInfo));
    }

    private void handleBackToSearch(Update update, SendMessage message) {
        String query = update.getCallbackQuery().getData().substring("back_to_search_".length());
        Document searchResult = wbApiClient.searchProduct(query);

        if (searchResult.containsKey("error")) {
            message.setText(searchResult.getString("error"));
        } else if (searchResult.containsKey("foundProducts")) {
            List<Document> products = searchResult.getList("foundProducts", Document.class);
            if (!products.isEmpty()) {
                message.setText("Выберите товар из результатов поиска:");
                message.setReplyMarkup(createProductSelectionKeyboard(products));
            } else {
                message.setText("По вашему запросу товары не найдены");
            }
        } else {
            message.setText("Произошла ошибка при поиске товаров");
        }
    }

    private InlineKeyboardMarkup createBackButtonKeyboard(Document productInfo) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createInlineButton("Назад к выбору товара", "back_to_search_" + productInfo.getString("query")));
        rows.add(row);
        keyboardMarkup.setKeyboard(rows);
        return keyboardMarkup;
    }

    private InlineKeyboardButton createInlineButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private void handleError(SendMessage message, Exception e) {
        e.printStackTrace();
        message.setText("Произошла ошибка при обработке запроса");
        try {
            execute(message);
        } catch (TelegramApiException ex) {
            ex.printStackTrace();
        }
    }

    private void handleGoogleShoppingCommand(String messageText, long chatId, String traceId, SendMessage response) {
        String query = messageText.substring(8).trim();
        if (query.isEmpty()) {
            response.setText("Введите поисковый запрос после команды /google");
            mongoDBService.logCommand("empty_google_query",
                    mongoDBService.createMetadata(traceId, chatId).build());
            return;
        }

        mongoDBService.logCommand("google_shopping_command",
                mongoDBService.createMetadata(traceId, chatId)
                        .with("query", query)
                        .build());

        String searchResult = googleShoppingParser.parseGoogleShopping(query);
        response.setText(searchResult);
    }


    private void handleForecastCommand(String messageText, long chatId, String traceId, SendMessage response) {
        String query = messageText.substring(10).trim();
        if (query.isEmpty()) {
            response.setText("Введите поисковый запрос после команды /forecast");
            mongoDBService.logCommand("empty_forecast_query",
                    mongoDBService.createMetadata(traceId, chatId).build());
            return;
        }

        mongoDBService.logCommand("forecast_command",
                mongoDBService.createMetadata(traceId, chatId)
                        .with("query", query)
                        .build());

        try {
            // Генерируем прогноз на 7 дней
            ForecastResult forecast = forecastService.generateForecast(query, 7);
            String forecastText = ForecastFormatter.formatForecast(forecast);
            response.setText(forecastText);

            mongoDBService.logCommand("forecast_success",
                    mongoDBService.createMetadata(traceId, chatId)
                            .with("query", query)
                            .with("forecastDays", 7)
                            .build());
        } catch (Exception e) {
            response.setText("Ошибка при генерации прогноза. Попробуйте позже.");
            mongoDBService.logError("forecast_error", "forecast_generation_failed", e,
                    mongoDBService.createMetadata(traceId, chatId)
                            .with("query", query)
                            .build());
        }
    }


    private void handleSearchCommand(String messageText, long chatId, String traceId, SendMessage response) {
        String query = messageText.substring(8).trim();
        if (query.isEmpty()) {
            response.setText("Введите поисковый запрос после команды /search");
            mongoDBService.logCommand("empty_search_query",
                    mongoDBService.createMetadata(traceId, chatId).build());
            return;
        }

        mongoDBService.logCommand("search_command",
                mongoDBService.createMetadata(traceId, chatId)
                        .with("query", query)
                        .build());

        String searchResult = wbApiClient.searchProduct(query, chatId, traceId);
        response.setText(searchResult);

        // Use the newly added logSearchQuery method
        mongoDBService.logSearchQuery(query, traceId);
    }

    private void handleCacheInfoCommand(String messageText, long chatId, String traceId, SendMessage response) {
        String query = messageText.substring(11).trim();
        if (query.isEmpty()) {
            response.setText("Введите запрос после команды /cacheinfo");
            mongoDBService.logCommand("empty_cacheinfo_query",
                    mongoDBService.createMetadata(traceId, chatId).build());
            return;
        }

        String cacheInfo = wbApiClient.getCacheInfo(query);
        response.setText(cacheInfo);

        mongoDBService.logCommand("cacheinfo_requested",
                mongoDBService.createMetadata(traceId, chatId)
                        .with("query", query)
                        .build());
    }

    private void handleError(long chatId, String traceId, SendMessage response, Exception e) {
        response.setText("Произошла ошибка при обработке запроса");
        try {
            execute(response);
        } catch (TelegramApiException ex) {
            ex.printStackTrace();
        }

        mongoDBService.logError("processing_error", "telegram_bot_error", e,
                mongoDBService.createMetadata(traceId, chatId)
                        .with("error", e.getMessage())
                        .with("stackTrace", Arrays.toString(e.getStackTrace()))
                        .build());
    }

    @Override
    public String getBotUsername() {
        return "sale_bot_VSTU";
    }

    @Override
    public String getBotToken() {
        return "7118289957:AAF7sOeJQcsefUPJnOoWA2QlB2pI8GVbZX8";
    }
}