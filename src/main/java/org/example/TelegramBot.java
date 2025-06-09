package org.example;

import org.example.db.MongoDBService;
import org.example.wb.WildberriesApiClient;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Arrays;
import java.util.UUID;

public class TelegramBot extends TelegramLongPollingBot {
    private final WildberriesApiClient wbApiClient;
    private final MongoDBService mongoDBService;

    // Конструктор для инициализации WB и MongoDB
    public TelegramBot() {
        this.wbApiClient = new WildberriesApiClient();
        this.mongoDBService = new MongoDBService();
    }

    // Метод, который вызывается при каждом новом сообщении от пользователя
    @Override
    public void onUpdateReceived(Update update) {
        // Проверяем, что сообщение существует и содержит текст
        if (update.hasMessage() && update.getMessage().hasText()) {
            long startTime = System.currentTimeMillis(); // метка начала обработки
            String traceId = UUID.randomUUID().toString(); // уникальный ID для отслеживания
            long chatId = update.getMessage().getChatId(); // ID чата пользователя
            String messageText = update.getMessage().getText(); // текст сообщения

            // Логируем факт получения сообщения
            mongoDBService.logCommand("message_received", mongoDBService.createMetadata(traceId, chatId)
                    .with("message", messageText)
                    .build());

            SendMessage response = new SendMessage();
            response.setChatId(String.valueOf(chatId));

            try {
                // Обработка команды поиска
                if (messageText.startsWith("/search ")) {
                    handleSearchCommand(messageText, chatId, traceId, response);
                } else {
                    // Обработка неизвестных команд
                    handleOtherCommand(messageText, chatId, traceId, response);
                }

                // Отправка ответа пользователю
                execute(response);

                // Логируем успешную отправку ответа с временем и размером
                mongoDBService.logCommand("response_sent", mongoDBService.createMetadata(traceId, chatId)
                        .with("responseTimeMs", System.currentTimeMillis() - startTime)
                        .with("responseLength", response.getText().length())
                        .build());
            } catch (Exception e) {
                handleError(chatId, traceId, response, e);
            }
        }
    }

    // Метод обработки команды /search
    private void handleSearchCommand(String messageText, long chatId, String traceId, SendMessage response) {
        String query = messageText.substring(8).trim();
        if (query.isEmpty()) {
            // Если строка пуста — отправляем предупреждение и логируем
            response.setText("Введите поисковый запрос после команды /search");
            mongoDBService.logCommand("empty_search_query", mongoDBService.createMetadata(traceId, chatId).build());
            return;
        }

        // Логируем команду поиска с введённым запросом
        mongoDBService.logCommand("search_command", mongoDBService.createMetadata(traceId, chatId)
                .with("query", query)
                .build());

        String searchResult = wbApiClient.searchProduct(query, chatId, traceId);
        response.setText(searchResult);
    }

    // Метод обработки неизвестных команд
    private void handleOtherCommand(String messageText, long chatId, String traceId, SendMessage response) {
        response.setText("Используйте команду /search [запрос] для поиска товаров");
        mongoDBService.logCommand("unknown_command", mongoDBService.createMetadata(traceId, chatId)
                .with("message", messageText)
                .build());
    }

    // Метод обработки исключений
    private void handleError(long chatId, String traceId, SendMessage response, Exception e) {
        response.setText("Произошла ошибка при обработке запроса");
        try {
            execute(response);
        } catch (TelegramApiException ex) {
            ex.printStackTrace();
        }

        // Логируем ошибку в MongoDB с трассировкой
        mongoDBService.logCommand("processing_error", mongoDBService.createMetadata(traceId, chatId)
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
        return "747246068:AAG-Uj1CvFFtLiO9r8agC4o5dsGx1XUeZ3I";
    }
}