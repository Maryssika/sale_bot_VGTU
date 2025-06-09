package org.example.forecast;

import org.bson.Document;
import org.example.db.MongoDBService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ForecastService {

    private final MongoDBService mongoDBService;

    public ForecastService(MongoDBService mongoDBService) {
        this.mongoDBService = mongoDBService;
    }

    public ForecastResult generateForecast(String query, int daysAhead) {
        Document stats = mongoDBService.getSearchStats(MongoDBService.Marketplace.WILDBERRIES, query);
        if (stats == null || !stats.containsKey("foundProducts")) {
            return new ForecastResult(query, Collections.emptyList(), "Нет данных для прогноза по запросу: " + query);
        }

        List<Document> products = (List<Document>) stats.get("foundProducts");
        List<Integer> prices = new ArrayList<>();

        for (Document product : products) {
            Object priceObj = product.get("price");
            if (priceObj instanceof Number) {
                prices.add(((Number) priceObj).intValue());
            }
        }

        if (prices.size() < 2) {
            return new ForecastResult(query, Collections.emptyList(), "Недостаточно данных для прогноза по запросу: " + query);
        }

        double averagePrice = prices.stream().mapToInt(Integer::intValue).average().orElse(0);
        List<ForecastResult.DayForecast> forecast = new ArrayList<>();

        for (int i = 1; i <= daysAhead; i++) {
            double predictedPrice = averagePrice + (i * 4.14); // Simple linear growth
            forecast.add(new ForecastResult.DayForecast(LocalDate.now().plusDays(i), predictedPrice));
        }

        return new ForecastResult(query, forecast, null);
    }
}
