package org.example.forecast;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

public class ForecastFormatter {
    public static String formatForecast(ForecastResult result) {
        if (result.getErrorMessage() != null) {
            return "⚠️ " + result.getErrorMessage();
        }

        if (result.getForecasts().isEmpty()) {
            return "Нет данных для прогноза по запросу: " + result.getQuery();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📈 Прогноз цен для запроса: ").append(result.getQuery()).append("\n\n");

        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(new Locale("ru"));

        for (ForecastResult.DayForecast day : result.getForecasts()) {
            sb.append("📅 ")
                    .append(day.getDate().format(formatter))
                    .append(" → ")
                    .append(String.format("%,.2f ₽", day.getPredictedPrice()))
                    .append("\n");
        }

        sb.append("\nℹ️ Прогноз основан на анализе текущих цен");
        return sb.toString();
    }
}
