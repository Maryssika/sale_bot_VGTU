package org.example;

import org.example.wb.WildberriesApiClient;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class TelegramBot extends TelegramLongPollingBot {
    private final WildberriesApiClient wbApiClient;

    public TelegramBot() {
        this.wbApiClient = new WildberriesApiClient();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));

            try {
                if (messageText.startsWith("/search ")) {
                    String query = messageText.substring(8).trim();
                    if (!query.isEmpty()) {
                        String searchResult = wbApiClient.searchProduct(query);
                        message.setText(searchResult);
                    } else {
                        message.setText("Введите поисковый запрос после команды /search");
                    }
                } else {
                    message.setText("Используйте команду /search [запрос] для поиска товаров на Wildberries");
                }

                execute(message);
            } catch (Exception e) {
                e.printStackTrace();
                message.setText("Произошла ошибка при обработке запроса");
                try {
                    execute(message);
                } catch (TelegramApiException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    @Override
    public String getBotUsername() {
        return "sale_bot_VSTU";
    }

    @Override
    public String getBotToken() {
        return "7472746068:AAG-Uj1CvFFtLiO9r8agC4o5dsGx1XUeZ3I";
    }
}