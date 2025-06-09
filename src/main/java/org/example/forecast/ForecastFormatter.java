package org.example.forecast;

import java.time.format.DateTimeFormatter;

public class ForecastFormatter {

    public static String formatForecast(ForecastResult result) {
        if (result.getErrorMessage() != null) {
            return "⚠️ " + result.getErrorMessage();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📈 Прогноз цены для запроса: ").append(result.getQuery()).append("\n\n");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (ForecastResult.DayForecast day : result.getForecasts()) {
            sb.append(day.getDate().format(formatter))
                    .append(" → ")
                    .append(String.format("%.2f ₽", day.getPredictedPrice()))
                    .append("\n");
        }

        return sb.toString();
    }
}
