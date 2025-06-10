package org.example.charts;

import org.bson.Document;
import org.example.db.MongoDBService;
import org.example.db.MongoDBService.Marketplace;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class PriceHistoryService {
    private final MongoDBService mongoDBService;
    private final SimpleDateFormat hourFormat = new SimpleDateFormat("HH:00");
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("dd.MM.yyyy");

    public PriceHistoryService(MongoDBService mongoDBService) {
        this.mongoDBService = mongoDBService;
    }

    public String getFormattedPriceHistory(String productId, Marketplace marketplace, String period) {
        Date[] dates = getDateRange(period);
        List<Document> priceHistory = mongoDBService.getDetailedPriceHistory(
                productId, marketplace, dates[0], dates[1]);

        if (priceHistory.isEmpty()) {
            return "Нет данных о ценах за указанный период";
        }

        Map<String, List<Document>> groupedPrices = groupPrices(priceHistory, period);
        return formatPriceHistory(productId, marketplace, groupedPrices, period);
    }

    private Date[] getDateRange(String period) {
        Date endDate = new Date();
        Date startDate;

        switch (period) {
            case "day":
                startDate = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
                break;
            case "week":
                startDate = Date.from(LocalDate.now().minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant());
                break;
            case "month":
                startDate = Date.from(LocalDate.now().minusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
                break;
            default:
                startDate = endDate;
                break;
        }

        return new Date[]{startDate, endDate};
    }

    private Map<String, List<Document>> groupPrices(List<Document> priceHistory, String period) {
        Map<String, List<Document>> groupedPrices = new LinkedHashMap<>();

        for (Document record : priceHistory) {
            Date timestamp = record.getDate("timestamp");
            String key = period.equals("day") ? hourFormat.format(timestamp) : dayFormat.format(timestamp);
            groupedPrices.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        }

        return groupedPrices;
    }

    private String formatPriceHistory(String productId, Marketplace marketplace,
                                      Map<String, List<Document>> groupedPrices, String period) {
        StringBuilder result = new StringBuilder();
        Document productInfo = mongoDBService.getProductInfo(productId, marketplace);
        String productName = productInfo != null ?
                productInfo.getList("foundProducts", Document.class).get(0).getString("name") :
                "Неизвестный товар";

        result.append("История цен для товара: ").append(productName).append("\n\n");
        result.append("За ").append(getPeriodName(period)).append(":\n\n");

        for (Map.Entry<String, List<Document>> entry : groupedPrices.entrySet()) {
            result.append(entry.getKey()).append(": ");

            int minPrice = Integer.MAX_VALUE;
            int maxPrice = Integer.MIN_VALUE;

            for (Document record : entry.getValue()) {
                int price = record.getInteger("price");
                minPrice = Math.min(minPrice, price);
                maxPrice = Math.max(maxPrice, price);
            }

            if (minPrice == maxPrice) {
                result.append(minPrice).append(" ₽\n");
            } else {
                result.append(minPrice).append(" - ").append(maxPrice).append(" ₽\n");
            }
        }

        return result.toString();
    }

    private String getPeriodName(String period) {
        switch (period) {
            case "day": return "последние 24 часа";
            case "week": return "последнюю неделю";
            case "month": return "последний месяц";
            default: return "указанный период";
        }
    }
}