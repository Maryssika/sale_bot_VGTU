package org.example.forecast;

import java.time.LocalDate;
import java.util.List;

public class ForecastResult {
    private final String query;
    private final List<DayForecast> forecasts;
    private final String errorMessage;

    public ForecastResult(String query, List<DayForecast> forecasts, String errorMessage) {
        this.query = query;
        this.forecasts = forecasts;
        this.errorMessage = errorMessage;
    }

    public String getQuery() {
        return query;
    }

    public List<DayForecast> getForecasts() {
        return forecasts;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static class DayForecast {
        private final LocalDate date;
        private final double predictedPrice;

        public DayForecast(LocalDate date, double predictedPrice) {
            this.date = date;
            this.predictedPrice = predictedPrice;
        }

        public LocalDate getDate() {
            return date;
        }

        public double getPredictedPrice() {
            return predictedPrice;
        }
    }
}
