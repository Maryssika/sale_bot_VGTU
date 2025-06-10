package org.example;

import org.example.db.MongoDBService;
import org.example.forecast.ForecastService;
import org.example.forecast.ForecastFormatter;
import org.example.forecast.ForecastResult;
import org.example.wb.WildberriesApiClient;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Arrays;
import java.util.UUID;

public class TelegramBot extends TelegramLongPollingBot {
    private final WildberriesApiClient wbApiClient;
    private final MongoDBService mongoDBService;
    private final ForecastService forecastService;

    public TelegramBot() {
        this.wbApiClient = new WildberriesApiClient();
        this.mongoDBService = new MongoDBService();
        this.forecastService = new ForecastService(mongoDBService);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long startTime = System.currentTimeMillis();
            String traceId = UUID.randomUUID().toString();
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();
            User user = update.getMessage().getFrom();

            // Определяем тип действия
            String action = "command";
            String query = "";

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
                if (messageText.startsWith("/search ")) {
                    handleSearchCommand(messageText, chatId, traceId, response);
                } else if (messageText.startsWith("/cacheinfo ")) {
                    handleCacheInfoCommand(messageText, chatId, traceId, response);
                } else if (messageText.startsWith("/forecast ")) {
                    handleForecastCommand(messageText, chatId, traceId, response);
                } else if (messageText.equals("/start")) {
                    response.setText("Добро пожаловать! Используйте команды:\n" +
                            "/search [запрос] - поиск товаров\n" +
                            "/cacheinfo [запрос] - информация о кэше\n" +
                            "/forecast [запрос] - прогноз цен на 7 дней");
                } else {
                    response.setText("Неизвестная команда. Доступные команды:\n" +
                            "/search [запрос] - поиск товаров\n" +
                            "/cacheinfo [запрос] - информация о кэше\n" +
                            "/forecast [запрос] - прогноз цен на 7 дней");
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
        }
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
        return "8041329311:AAEKWgpk-4yGXjBw6sV_CUoKAHWuWT9sqKE";
    }
}