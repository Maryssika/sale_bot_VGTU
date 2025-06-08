package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            try {
                botsApi.registerBot(new TelegramBot());
                System.out.println("Bot started successfully!");

                while (true) {
                    Thread.sleep(1000);
                }
            } catch (TelegramApiException e) {
                if (e.getMessage().contains("Error removing old webhook")) {
                    System.out.println("Bot started (webhook was not set)");
                    while (true) {
                        Thread.sleep(1000);
                    }
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}