
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
    private final Random random = new Random();

    public PriceHistoryService(MongoDBService mongoDBService) {
        this.mongoDBService = mongoDBService;
    }

    public String getFormattedPriceHistory(String productId, Marketplace marketplace, String period) {
        Document productInfo = mongoDBService.getProductInfo(productId, marketplace);
        if (productInfo == null) {
            return "Не удалось получить информацию о товаре";
        }

        List<Document> priceHistory = generateSyntheticPriceHistory(productInfo, period);

        if (priceHistory.isEmpty()) {
            return "Нет данных о ценах за указанный период";
        }

        Map<String, List<Document>> groupedPrices = groupPrices(priceHistory, period);
        return formatPriceHistory(productInfo, groupedPrices, period);
    }

    private List<Document> generateSyntheticPriceHistory(Document productInfo, String period) {
        List<Document> priceHistory = new ArrayList<>();
        int initialPrice = productInfo.getList("foundProducts", Document.class).get(0).getInteger("price");

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = getStartDate(endDate, period);

        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            int numChanges = 0;
            switch (period) {
                case "day":
                    numChanges = 0;
                    break;
                case "week":
                    numChanges = random.nextDouble() < 0.3 ? 1 : 0;
                    break;
                case "month":
                    numChanges = random.nextInt(3);
                    break;
            }
            List<LocalDate> changeDates = new ArrayList<>();
            for (int i = 0; i < numChanges; i++) {
                LocalDate changeDate;
                if (period.equals("week")) {
                    changeDate = startDate.plusDays(random.nextInt(7));
                } else {
                    changeDate = startDate.plusDays(random.nextInt(30));
                }
                changeDates.add(changeDate);
            }
            Collections.sort(changeDates);

            int currentPrice = initialPrice;

            if (period.equals("day")) {
                for (int hour = 0; hour < 24; hour++) {
                    Date timestamp = Date.from(currentDate.atStartOfDay(ZoneId.systemDefault()).plusHours(hour).toInstant());
                    Document priceRecord = new Document()
                            .append("timestamp", timestamp)
                            .append("price", currentPrice);
                    priceHistory.add(priceRecord);
                }
            } else {
                LocalDate day = startDate;
                while (!day.isAfter(endDate)) {
                    if (changeDates.contains(day)) {
                        double changePercentage = random.nextDouble() * 0.1 - 0.05;
                        currentPrice = (int) (initialPrice * (1 + changePercentage));
                        currentPrice = Math.max(1, currentPrice);
                    }

                    Date timestamp = Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
                    Document priceRecord = new Document()
                            .append("timestamp", timestamp)
                            .append("price", currentPrice);
                    priceHistory.add(priceRecord);
                    day = day.plusDays(1);
                }
            }

            break;

        }

        return priceHistory;
    }

    private LocalDate getStartDate(LocalDate endDate, String period) {
        switch (period) {
            case "day":
                return endDate;
            case "week":
                return endDate.minusDays(7);
            case "month":
                return endDate.minusMonths(1);
            default:
                return endDate;
        }
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

    private String formatPriceHistory(Document productInfo, Map<String, List<Document>> groupedPrices, String period) {
        StringBuilder result = new StringBuilder();
        String productName = productInfo.getList("foundProducts", Document.class).get(0).getString("name");

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
            case "day":
                return "последние 24 часа";
            case "week":
                return "последнюю неделю";
            case "month":
                return "последний месяц";
            default:
                return "указанный период";
        }
    }
}
