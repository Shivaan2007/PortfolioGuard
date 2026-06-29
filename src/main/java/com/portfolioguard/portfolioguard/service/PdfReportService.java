package com.portfolioguard.portfolioguard.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.portfolioguard.portfolioguard.model.Portfolio;
import com.portfolioguard.portfolioguard.model.Stock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class PdfReportService {

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private RiskMetricsService riskMetricsService;

    @Autowired
    private SentimentService sentimentService;

    public byte[] generateReport(String portfolioId) throws Exception {
        Portfolio portfolio = portfolioService.getPortfolio(portfolioId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 60, 60);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font titleFont = new Font(Font.HELVETICA, 22, Font.BOLD, new Color(0, 112, 192));
        Font headingFont = new Font(Font.HELVETICA, 13, Font.BOLD, new Color(30, 30, 60));
        Font subFont = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(80, 80, 80));
        Font tableHeaderFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
        Font tableBodyFont = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(30, 30, 30));
        Font metricValueFont = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(0, 112, 192));
        Font metricLabelFont = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(100, 100, 100));

        // Header
        Paragraph title = new Paragraph("PORTFOLIOGUARD", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        Paragraph subtitle = new Paragraph("Institutional Risk Report", subFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(4);
        doc.add(subtitle);

        Paragraph dateP = new Paragraph("Generated: " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("MMMM dd, yyyy HH:mm")), subFont);
        dateP.setAlignment(Element.ALIGN_CENTER);
        dateP.setSpacingAfter(20);
        doc.add(dateP);

        addDivider(doc);

        // Portfolio info
        addSectionHeader(doc, "Portfolio Overview", headingFont);
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingAfter(16);
        addInfoRow(infoTable, "Portfolio Name", portfolio.getName(), tableBodyFont);
        addInfoRow(infoTable, "Strategy", portfolio.getStrategy(), tableBodyFont);
        addInfoRow(infoTable, "Description", portfolio.getDescription(), tableBodyFont);
        addInfoRow(infoTable, "Created", portfolio.getCreatedAt()
                .format(DateTimeFormatter.ofPattern("MMM dd, yyyy")), tableBodyFont);
        addInfoRow(infoTable, "Total Positions", String.valueOf(portfolio.getStocks().size()), tableBodyFont);
        doc.add(infoTable);

        // Key Metrics
        addSectionHeader(doc, "Key Performance Metrics", headingFont);
        double portfolioValue = portfolioService.calculatePortfolioValue(portfolioId);
        double pnl = portfolioService.calculatePnL(portfolioId);
        double totalReturn = portfolioService.calculateTotalReturn(portfolioId);
        double sharpe = portfolioService.calculateSharpeRatio(portfolioId);

        PdfPTable metricsTable = new PdfPTable(4);
        metricsTable.setWidthPercentage(100);
        metricsTable.setSpacingAfter(16);
        addMetricCell(metricsTable, "Portfolio Value", String.format("$%.2f", portfolioValue), metricValueFont, metricLabelFont);
        addMetricCell(metricsTable, "Total P&L", String.format("%s$%.2f", pnl >= 0 ? "+" : "", pnl), metricValueFont, metricLabelFont);
        addMetricCell(metricsTable, "Total Return", String.format("%.2f%%", totalReturn), metricValueFont, metricLabelFont);
        addMetricCell(metricsTable, "Sharpe Ratio", String.format("%.3f", sharpe), metricValueFont, metricLabelFont);
        doc.add(metricsTable);

        // Risk Metrics
        addSectionHeader(doc, "Risk Metrics", headingFont);
        try {
            String firstTicker = portfolio.getStocks().get(0).getTicker();
            List<Double> returns = riskMetricsService.getRealReturns(firstTicker, 100);
            List<Double> marketReturns = riskMetricsService.getRealReturns("SPY", 100);
            double var95 = riskMetricsService.calculateVaR(returns, 0.95);
            double var99 = riskMetricsService.calculateVaR(returns, 0.99);
            double beta = riskMetricsService.calculateBeta(returns, marketReturns);

            PdfPTable riskTable = new PdfPTable(3);
            riskTable.setWidthPercentage(100);
            riskTable.setSpacingAfter(16);
            addMetricCell(riskTable, "VaR (95%)", String.format("%.2f%%", var95), metricValueFont, metricLabelFont);
            addMetricCell(riskTable, "VaR (99%)", String.format("%.2f%%", var99), metricValueFont, metricLabelFont);
            addMetricCell(riskTable, "Portfolio Beta", String.format("%.3f", beta), metricValueFont, metricLabelFont);
            doc.add(riskTable);
        } catch (IndexOutOfBoundsException e) {
            Paragraph riskNote = new Paragraph("Risk metrics require at least one position.", subFont);
            riskNote.setSpacingAfter(16);
            doc.add(riskNote);
        } catch (Exception e) {
            Paragraph riskNote = new Paragraph("Risk metrics temporarily unavailable.", subFont);
            riskNote.setSpacingAfter(16);
            doc.add(riskNote);
        }

        // Positions Table
        addSectionHeader(doc, "Current Positions", headingFont);
        PdfPTable posTable = new PdfPTable(6);
        posTable.setWidthPercentage(100);
        posTable.setWidths(new float[]{2f, 2f, 1f, 2f, 2f, 2f});
        posTable.setSpacingAfter(16);

        for (String h : new String[]{"Ticker", "Sector", "Qty", "Purchase Price", "Current Price", "P&L"}) {
            PdfPCell cell = new PdfPCell(new Phrase(h, tableHeaderFont));
            cell.setBackgroundColor(new Color(0, 112, 192));
            cell.setPadding(8);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            posTable.addCell(cell);
        }

        for (Stock stock : portfolio.getStocks()) {
            double stockPnl = (stock.getCurrentPrice() - stock.getPurchasePrice()) * stock.getQuantity();
            double stockPnlPct = ((stock.getCurrentPrice() - stock.getPurchasePrice()) / stock.getPurchasePrice()) * 100;
            Color pnlColor = stockPnl >= 0 ? new Color(0, 150, 0) : new Color(200, 0, 0);

            addPosCell(posTable, stock.getTicker(), tableBodyFont, true);
            addPosCell(posTable, stock.getSector(), tableBodyFont, false);
            addPosCell(posTable, String.valueOf(stock.getQuantity()), tableBodyFont, false);
            addPosCell(posTable, String.format("$%.2f", stock.getPurchasePrice()), tableBodyFont, false);
            addPosCell(posTable, String.format("$%.2f", stock.getCurrentPrice()), tableBodyFont, false);

            Font pnlFont = new Font(Font.HELVETICA, 10, Font.BOLD, pnlColor);
            PdfPCell pnlCell = new PdfPCell(new Phrase(
                    String.format("%s$%.2f (%.1f%%)", stockPnl >= 0 ? "+" : "", stockPnl, stockPnlPct), pnlFont));
            pnlCell.setPadding(6);
            pnlCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            posTable.addCell(pnlCell);
        }
        doc.add(posTable);

        // Sentiment
        addSectionHeader(doc, "News Sentiment Analysis", headingFont);
        PdfPTable sentTable = new PdfPTable(3);
        sentTable.setWidthPercentage(100);
        sentTable.setSpacingAfter(16);

        for (String h : new String[]{"Ticker", "Sentiment", "Score"}) {
            PdfPCell cell = new PdfPCell(new Phrase(h, tableHeaderFont));
            cell.setBackgroundColor(new Color(0, 112, 192));
            cell.setPadding(8);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            sentTable.addCell(cell);
        }

        for (Stock stock : portfolio.getStocks()) {
            try {
                Map<String, Object> sentiment = sentimentService.getSentiment(stock.getTicker());
                String label = (String) sentiment.getOrDefault("label", "NEUTRAL");
                double score = sentiment.get("score") instanceof Number
                        ? ((Number) sentiment.get("score")).doubleValue() : 0.0;
                Color sentColor = label.equals("POSITIVE") ? new Color(0, 150, 0)
                        : label.equals("NEGATIVE") ? new Color(200, 0, 0) : new Color(100, 100, 100);
                addPosCell(sentTable, stock.getTicker(), tableBodyFont, true);
                PdfPCell labelCell = new PdfPCell(new Phrase(label,
                        new Font(Font.HELVETICA, 10, Font.BOLD, sentColor)));
                labelCell.setPadding(6);
                labelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                sentTable.addCell(labelCell);
                addPosCell(sentTable, String.format("%.4f", score), tableBodyFont, false);
            } catch (Exception e) {
                addPosCell(sentTable, stock.getTicker(), tableBodyFont, true);
                addPosCell(sentTable, "N/A", tableBodyFont, false);
                addPosCell(sentTable, "N/A", tableBodyFont, false);
            }
        }
        doc.add(sentTable);

        // Footer
        addDivider(doc);
        Paragraph footer = new Paragraph(
                "CONFIDENTIAL — Generated by PortfolioGuard Risk Management System\n" +
                "This report is for informational purposes only and does not constitute investment advice.",
                new Font(Font.HELVETICA, 8, Font.ITALIC, new Color(120, 120, 120)));
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(8);
        doc.add(footer);

        doc.close();
        return out.toByteArray();
    }

    private void addSectionHeader(Document doc, String text, Font font) throws Exception {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(12);
        p.setSpacingAfter(8);
        doc.add(p);
    }

    private void addDivider(Document doc) throws Exception {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        line.setSpacingBefore(4);
        line.setSpacingAfter(12);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(new Color(0, 112, 192));
        cell.setFixedHeight(2);
        cell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        line.addCell(cell);
        doc.add(line);
    }

    private void addInfoRow(PdfPTable table, String label, String value, Font font) {
        Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(60, 60, 60));
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setPadding(6);
        labelCell.setBackgroundColor(new Color(240, 240, 240));
        table.addCell(labelCell);
        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "—", font));
        valueCell.setPadding(6);
        table.addCell(valueCell);
    }

    private void addMetricCell(PdfPTable table, String label, String value, Font valueFont, Font labelFont) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(12);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBackgroundColor(new Color(245, 248, 255));
        Paragraph p = new Paragraph();
        p.add(new Chunk(value + "\n", valueFont));
        p.add(new Chunk(label, labelFont));
        p.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p);
        table.addCell(cell);
    }

    private void addPosCell(PdfPTable table, String text, Font font, boolean bold) {
        Font f = bold ? new Font(Font.HELVETICA, 10, Font.BOLD, new Color(0, 112, 192)) : font;
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "—", f));
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }
}
