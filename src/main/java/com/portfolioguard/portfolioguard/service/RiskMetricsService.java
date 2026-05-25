package com.portfolioguard.portfolioguard.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;



@Service

public class RiskMetricsService {

    @Autowired
    private MarketDataService marketDataService;

    // ─── Helper Methods ───────────────────────────────

    private double mean(List<Double> returns) {
        return returns.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private double standardDeviation(List<Double> returns) {
        double avg = mean(returns);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - avg, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    // ─── Simulated Historical Returns ─────────────────
    // Used until Alpha Vantage is connected on Day 4

    public List<Double> generateSimulatedReturns(int days) {
        List<Double> returns = new ArrayList<>();
        Random random = new Random(42);
        for (int i = 0; i < days; i++) {
            // Simulate realistic daily returns
            // Mean of 0.0004 (~10% annual), std dev of 0.015 (~15% annual volatility)
            double dailyReturn = 0.0004 + random.nextGaussian() * 0.015;
            returns.add(dailyReturn);
        }
        return returns;
    }

    // ─── Value at Risk ─────────────────────────────────

    public double calculateVaR(List<Double> dailyReturns, double confidenceLevel) {
        if (dailyReturns == null || dailyReturns.isEmpty()) {
            return 0.0;
        }

        List<Double> sorted = new ArrayList<>(dailyReturns);
        Collections.sort(sorted);

        int index = (int) Math.floor((1 - confidenceLevel) * sorted.size());
        return sorted.get(index) * 100;
    }

    // ─── Portfolio Beta ────────────────────────────────

    public double calculateBeta(List<Double> portfolioReturns,
                                List<Double> marketReturns) {
        if (portfolioReturns.size() != marketReturns.size()
                || portfolioReturns.isEmpty()) {
            return 1.0;
        }

        double portfolioMean = mean(portfolioReturns);
        double marketMean = mean(marketReturns);

        double covariance = 0;
        double marketVariance = 0;

        for (int i = 0; i < portfolioReturns.size(); i++) {
            covariance += (portfolioReturns.get(i) - portfolioMean)
                    * (marketReturns.get(i) - marketMean);
            marketVariance += Math.pow(marketReturns.get(i) - marketMean, 2);
        }

        return marketVariance == 0 ? 1.0 : covariance / marketVariance;
    }

    // ─── Correlation Between Two Stocks ───────────────

    public double calculateCorrelation(List<Double> returnsA,
                                       List<Double> returnsB) {
        if (returnsA.size() != returnsB.size() || returnsA.isEmpty()) {
            return 0.0;
        }

        double meanA = mean(returnsA);
        double meanB = mean(returnsB);
        double stdA = standardDeviation(returnsA);
        double stdB = standardDeviation(returnsB);

        if (stdA == 0 || stdB == 0) return 0.0;

        double covariance = 0;
        for (int i = 0; i < returnsA.size(); i++) {
            covariance += (returnsA.get(i) - meanA)
                    * (returnsB.get(i) - meanB);
        }
        covariance /= returnsA.size();

        return covariance / (stdA * stdB);
    }

    // ─── Correlation Matrix ────────────────────────────

    public double[][] calculateCorrelationMatrix(int stockCount) {
        double[][] matrix = new double[stockCount][stockCount];
        Random random = new Random(42);

        for (int i = 0; i < stockCount; i++) {
            for (int j = 0; j < stockCount; j++) {
                if (i == j) {
                    matrix[i][j] = 1.0;
                } else if (j < i) {
                    matrix[i][j] = matrix[j][i];
                } else {
                    // Simulate realistic correlation between 0.3 and 0.9
                    matrix[i][j] = 0.3 + random.nextDouble() * 0.6;
                }
            }
        }
        return matrix;
    }

    // ─── Improved Sharpe Ratio ─────────────────────────

    public double calculateSharpeRatio(List<Double> dailyReturns) {
        if (dailyReturns == null || dailyReturns.size() < 2) {
            return 0.0;
        }

        double riskFreeRateDaily = 0.05 / 252;
        double avgReturn = mean(dailyReturns);
        double stdDev = standardDeviation(dailyReturns);

        if (stdDev == 0) return 0.0;

        // Annualize the Sharpe Ratio
        return ((avgReturn - riskFreeRateDaily) / stdDev) * Math.sqrt(252);
    }

    public List<Double> getRealReturns(String ticker, int days) {
        try {
            return marketDataService.getDailyReturns(ticker, days);
        } catch (Exception e) {
            System.out.println("ERROR in getRealReturns: " + e.getMessage());
            return generateSimulatedReturns(days);
        }
    }


}
