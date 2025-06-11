
package org.example.charts;

import org.bson.Document;
import org.example.db.MongoDBService;
import org.example.db.MongoDBService.Marketplace;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.style.Styler;

import java.io.File;
import java.io.IOException;
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

    // Основной метод — возвращает файл с графиком для отправки в Telegram
    public File getPriceHistoryChartFile(String productId, Marketplace marketplace, String period) throws IOException {
        Document productInfo = mongoDBService.getProductInfo(productId, marketplace);
        if (productInfo == null) {
            return null; // Можно выбросить исключение или вернуть null
        }

        List<Document> priceHistory = generateSyntheticPriceHistory(productInfo, period);

        if (priceHistory.isEmpty()) {
            return null;
        }

        Map<String, List<Document>> groupedPrices = groupPrices(priceHistory, period);

        return generateChartImage(groupedPrices, period);
    }

    private List<Document> generateSyntheticPriceHistory(Document productInfo, String period) {
        List<Document> priceHistory = new ArrayList<>();
        int initialPrice = productInfo.getList("foundProducts", Document.class).get(0).getInteger("price");

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = getStartDate(endDate, period);

        int currentPrice = initialPrice;

        if (period.equals("day")) {
            for (int hour = 0; hour < 24; hour++) {
                double changePercentage = (random.nextDouble() * 0.1) - 0.05;
                currentPrice = Math.max(1, (int) (currentPrice * (1 + changePercentage)));

                Date timestamp = Date.from(endDate.atStartOfDay(ZoneId.systemDefault()).plusHours(hour).toInstant());
                Document priceRecord = new Document()
                        .append("timestamp", timestamp)
                        .append("price", currentPrice);
                priceHistory.add(priceRecord);
            }
        } else {
            LocalDate day = startDate;
            while (!day.isAfter(endDate)) {
                if (random.nextDouble() < 0.3) {
                    double changePercentage = (random.nextDouble() * 0.1) - 0.05;
                    currentPrice = Math.max(1, (int) (currentPrice * (1 + changePercentage)));
                }

                Date timestamp = Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
                Document priceRecord = new Document()
                        .append("timestamp", timestamp)
                        .append("price", currentPrice);
                priceHistory.add(priceRecord);

                day = day.plusDays(1);
            }
        }

        return priceHistory;
    }

    private LocalDate getStartDate(LocalDate endDate, String period) {
        switch (period) {
            case "day":   return endDate;
            case "week":  return endDate.minusDays(7);
            case "month": return endDate.minusMonths(1);
            default:      return endDate;
        }
    }

    // Группируем цену по меткам времени (часам или датам)
    private Map<String, List<Document>> groupPrices(List<Document> priceHistory, String period) {
        Map<String, List<Document>> groupedPrices = new LinkedHashMap<>();

        for (Document record : priceHistory) {
            Date timestamp = record.getDate("timestamp");
            String key = period.equals("day") ? hourFormat.format(timestamp) : dayFormat.format(timestamp);
            groupedPrices.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        }

        return groupedPrices;
    }

    // Генерируем PNG-файл с графиком
    private File generateChartImage(Map<String, List<Document>> groupedPrices, String period) throws IOException {
        List<String> xData = new ArrayList<>();
        List<Integer> yData = new ArrayList<>();

        // Собираем данные для графика
        for (Map.Entry<String, List<Document>> entry : groupedPrices.entrySet()) {
            xData.add(entry.getKey());
            int avgPrice = (int) entry.getValue().stream().mapToInt(d -> d.getInteger("price")).average().orElse(0);
            yData.add(avgPrice);
        }

        // Создаём график
        CategoryChart chart = new CategoryChartBuilder()
                .width(800)
                .height(400)
                .title("История цен за " + getPeriodName(period))
                .xAxisTitle(period.equals("day") ? "Часы" : "Дата")
                .yAxisTitle("Цена (₽)")
                .build();

        // Настройки стиля
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setXAxisLabelRotation(45);
        chart.getStyler().setPlotGridVerticalLinesVisible(false);
        chart.getStyler().setPlotGridHorizontalLinesVisible(true);
        chart.getStyler().setDatePattern("dd.MM");

        // Добавляем данные в график
        chart.addSeries("Цена", xData, yData);

        // Сохраняем в файл PNG
        File chartFile = File.createTempFile("price_chart_", ".png");
        BitmapEncoder.saveBitmap(chart, chartFile.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);

        return chartFile;
    }

    private String getPeriodName(String period) {
        switch (period) {
            case "day":   return "последние 24 часа";
            case "week":  return "последнюю неделю";
            case "month": return "последний месяц";
            default:      return "указанный период";
        }
    }

    // Добавляем метод для текстового представления истории, если нужно
    public String getFormattedPriceHistory(String productId, Marketplace marketplace, String period) {
        Document productInfo = mongoDBService.getProductInfo(productId, marketplace);
        if (productInfo == null) {
            return "Информация о продукте не найдена.";
        }

        List<Document> priceHistory = generateSyntheticPriceHistory(productInfo, period);

        if (priceHistory.isEmpty()) {
            return "История цен для данного продукта отсутствует.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("История цен для товара: ").append(productInfo.getList("foundProducts", Document.class).get(0).getString("name")).append("\n");

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        for (Document record : priceHistory) {
            Date timestamp = record.getDate("timestamp");
            Integer price = record.getInteger("price");
            sb.append(dateFormat.format(timestamp)).append(": ").append(price).append(" ₽\n");
        }

        return sb.toString();
    }
}
