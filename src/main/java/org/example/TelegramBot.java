
package org.example;

import org.bson.Document;
import org.example.charts.PriceHistoryService;
import org.example.db.MongoDBService;
import org.example.db.MongoDBService.Marketplace;
import org.example.forecast.ForecastFormatter;
import org.example.forecast.ForecastResult;
import org.example.forecast.ForecastService;
import org.example.google.GoogleShoppingParser;
import org.example.wb.WildberriesApiClient;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TelegramBot extends TelegramLongPollingBot {
    private final WildberriesApiClient wbApiClient;
    private final MongoDBService mongoDBService;
    private final PriceHistoryService priceHistoryService;
    private final ForecastService forecastService;
    private final GoogleShoppingParser googleShoppingParser;

    public TelegramBot() {
        this.wbApiClient = new WildberriesApiClient();
        this.mongoDBService = new MongoDBService();
        this.priceHistoryService = new PriceHistoryService(mongoDBService);
        this.forecastService = new ForecastService(mongoDBService);
        this.googleShoppingParser = new GoogleShoppingParser();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessage(update);
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }

    private void handleMessage(Update update) {
        String messageText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        User user = update.getMessage().getFrom();
        String traceId = UUID.randomUUID().toString();

        String action = "command";
        String query = "";

        if (messageText.startsWith("/wb ")) {
            action = "search";
            query = messageText.substring(4).trim();
        } else if (messageText.startsWith("/cacheinfo ")) {
            action = "cache_info";
            query = messageText.substring(11).trim();
        } else if (messageText.startsWith("/forecast ")) {
            action = "forecast";
            query = messageText.substring(10).trim();
        } else if (messageText.startsWith("/google ")) {
            action = "google";
            query = messageText.substring(8).trim();
        }

        mongoDBService.logUserAction(
                user.getId(),
                user.getUserName() != null ? user.getUserName() : "unknown",
                user.getFirstName() != null ? user.getFirstName() : "unknown",
                user.getLastName() != null ? user.getLastName() : "unknown",
                action,
                query,
                messageText
        );

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));

        try {
            if (messageText.startsWith("/wb ")) {
                handleSearchCommand(update, message, traceId);
            } else if (messageText.startsWith("/cacheinfo ")) {
                handleCacheInfoCommand(messageText, chatId, message, traceId);
            } else if (messageText.startsWith("/forecast ")) {
                handleForecastCommand(messageText, chatId, traceId, message);
            } else if (messageText.startsWith("/google ")) {
                handleGoogleShoppingCommand(messageText, chatId, traceId, message);
            } else if (messageText.equals("/wb")) {
                message.setText("Введите название товара для поиска на Wildberries в формате:\n\n/wb [название товара]\n\nНапример:\n/wb пиджак");
                execute(message);
                return;
            } else if (messageText.equals("/google")) {
                message.setText("Введите название товара для поиска на Google Shopping в формате:\n\n/google [название товара]\n\nНапример:\n/google пиджак");
                execute(message);
                return;
            } else if (messageText.equals("/cacheinfo")) {
                message.setText("Введите название товара для поиска для получения информация о кэше в формате:\n\n/cacheinfo [название товара]\n\nНапример:\n/cacheinfo пиджак");
                execute(message);
                return;
            } else if (messageText.equals("/forecast")) {
                message.setText("Введите название товара для прогноза цен на 7 дней в формате:\n\n/forecast [название товара]\n\nНапример:\n/forecast пиджак");
                execute(message);
                return;
            } else if (messageText.equals("/start")) {
                message.setText("Добро пожаловать! Используйте команды:\n" +
                        "/wb - поиск товаров на Wildberries\n" +
                        "/cacheinfo [запрос] - информация о кэше\n" +
                        "/forecast [запрос] - прогноз цен на 7 дней\n" +
                        "/google [запрос] - поиск товаров в Google Shopping");
                execute(message);
                return;
            } else {
                message.setText("Используйте команду /wb [запрос] для поиска товаров на Wildberries, /cacheinfo [запрос] для информации о кэше, /forecast [запрос] для прогноза цен, /google [запрос] для поиска в Google Shopping");
                execute(message);
                return;
            }
        } catch (Exception e) {
            handleError(message, e);
        }
    }

    private void handleSearchCommand(Update update, SendMessage message, String traceId) {
        String query = update.getMessage().getText().substring(4).trim();
        if (!query.isEmpty()) {
            Document searchResult = wbApiClient.searchProduct(query, update.getMessage().getChatId(), traceId);

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
            message.setText("Введите поисковый запрос после команды /wb");
        }
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
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
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            rows.add(row);
        }

        keyboardMarkup.setKeyboard(rows);
        return keyboardMarkup;
    }

    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();

        try {
            if (callbackData.startsWith("select_product_")) {
                handleProductSelection(update, chatId, callbackData.substring("select_product_".length()));
            } else if (callbackData.startsWith("price_detail_")) {
                handlePriceDetails(update, chatId, callbackData);
            } else if (callbackData.startsWith("back_to_search_")) {
                handleBackToSearch(update, chatId, callbackData.substring("back_to_search_".length()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleProductSelection(Update update, long chatId, String productId) throws TelegramApiException {
        Document productInfo = mongoDBService.getProductInfo(productId, Marketplace.WILDBERRIES);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));

        if (productInfo != null) {
            String productName = productInfo.getList("foundProducts", Document.class).get(0).getString("name");
            message.setText("Выбран товар: " + productName + "\nВыберите период для просмотра цен:");
            message.setReplyMarkup(createPeriodSelectionKeyboard(productId, productInfo));
        } else {
            message.setText("Не удалось получить информацию о товаре");
        }

        execute(message);
    }

    private InlineKeyboardMarkup createPeriodSelectionKeyboard(String productId, Document productInfo) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineButton("Цены за день", "price_detail_day_" + productId));
        row1.add(createInlineButton("Цены за неделю", "price_detail_week_" + productId));

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createInlineButton("Цены за месяц", "price_detail_month_" + productId));

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createInlineButton("Назад к выбору товара", "back_to_search_" + productInfo.getString("query")));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);

        keyboardMarkup.setKeyboard(rows);
        return keyboardMarkup;
    }

    private void handlePriceDetails(Update update, long chatId, String callbackData) throws TelegramApiException {
        String[] parts = callbackData.split("_");
        String period = parts[2];
        String productId = parts[3];

        // Попытка сгенерировать график и отправить фото
        try {
            File chartFile = priceHistoryService.getPriceHistoryChartFile(productId, Marketplace.WILDBERRIES, period);
            if (chartFile != null) {
                SendPhoto photo = new SendPhoto();
                photo.setChatId(String.valueOf(chatId));
                photo.setPhoto(new InputFile(chartFile));
                execute(photo);
                chartFile.deleteOnExit(); // Очистка временного файла
            } else {
                sendTextPriceHistory(chatId, productId, period);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendTextPriceHistory(chatId, productId, period);
        }
    }

    // Если график не сгенерирован — отправляем текст с историей цен
    private void sendTextPriceHistory(long chatId, String productId, String period) throws TelegramApiException {
        String priceHistory = priceHistoryService.getFormattedPriceHistory(productId, Marketplace.WILDBERRIES, period);
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(priceHistory);
        execute(message);
    }

    private void handleBackToSearch(Update update, long chatId, String query) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));

        if (query == null) {
            message.setText("Произошла ошибка: невозможно вернуться к результатам поиска. Пожалуйста, выполните новый поиск.");
            execute(message);
            return;
        }
        Document searchResult = wbApiClient.searchProduct(query, chatId, UUID.randomUUID().toString());

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
        execute(message);
    }

    private InlineKeyboardButton createInlineButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private void handleCacheInfoCommand(String messageText, long chatId, SendMessage response, String traceId) {
        // ... (оставляем без изменений)
    }

    private void handleForecastCommand(String messageText, long chatId, String traceId, SendMessage response) {
        // ... (оставляем без изменений)
    }

    private void handleGoogleShoppingCommand(String messageText, long chatId, String traceId, SendMessage response) {
        // ... (оставляем без изменений)
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

    @Override
    public String getBotUsername() {
        return "sale_bot_VSTU";
    }

    @Override
    public String getBotToken() {
        return "7118289957:AAF7sOeJQcsefUPJnOoWA2QlB2pI8GVbZX8";
    }
}
