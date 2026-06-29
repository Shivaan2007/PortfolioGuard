package com.portfolioguard.portfolioguard.service;

import com.portfolioguard.portfolioguard.dto.*;
import com.portfolioguard.portfolioguard.exception.ForbiddenException;
import com.portfolioguard.portfolioguard.exception.ResourceNotFoundException;
import com.portfolioguard.portfolioguard.model.*;
import com.portfolioguard.portfolioguard.repository.ChatMessageRepository;
import com.portfolioguard.portfolioguard.repository.ChatSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatService {

    @Autowired
    private ChatSessionRepository sessionRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private RiskMetricsService riskMetricsService;

    @Autowired
    private StockSearchService stockSearchService;

    @Autowired
    private SentimentService sentimentService;

    private static final Pattern TICKER_PATTERN = Pattern.compile("\\b[A-Z]{2,5}\\b");

    private static final java.util.Map<String, String> COMPANY_NAME_TO_TICKER = java.util.Map.ofEntries(
            java.util.Map.entry("apple", "AAPL"),
            java.util.Map.entry("tesla", "TSLA"),
            java.util.Map.entry("microsoft", "MSFT"),
            java.util.Map.entry("google", "GOOGL"),
            java.util.Map.entry("alphabet", "GOOGL"),
            java.util.Map.entry("amazon", "AMZN"),
            java.util.Map.entry("nvidia", "NVDA"),
            java.util.Map.entry("meta", "META"),
            java.util.Map.entry("facebook", "META"),
            java.util.Map.entry("netflix", "NFLX"),
            java.util.Map.entry("intel", "INTC"),
            java.util.Map.entry("amd", "AMD"),
            java.util.Map.entry("disney", "DIS"),
            java.util.Map.entry("walmart", "WMT"),
            java.util.Map.entry("boeing", "BA"),
            java.util.Map.entry("jpmorgan", "JPM"),
            java.util.Map.entry("visa", "V"),
            java.util.Map.entry("mastercard", "MA")
    );

    // ------------------------------------------------------------------
    // Session management
    // ------------------------------------------------------------------
    public ChatSession createSession(String userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle((title == null || title.isBlank()) ? "New conversation" : title);
        return sessionRepository.save(session);
    }

    public List<ChatSession> getUserSessions(String userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public ChatSession getSessionForUser(String sessionId, String userId) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("You do not have access to this chat session");
        }
        return session;
    }

    public void deleteSession(String sessionId, String userId) {
        getSessionForUser(sessionId, userId); // ownership check
        sessionRepository.deleteById(sessionId);
    }

    public List<ChatMessage> getMessages(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    // ------------------------------------------------------------------
    // Send a message, save it, generate + save a reply
    // ------------------------------------------------------------------
    public ChatMessage sendMessage(String sessionId, String userId, String userContent, String portfolioId) {
        ChatSession session = getSessionForUser(sessionId, userId);

        ChatMessage userMessage = new ChatMessage();
        userMessage.setRole("user");
        userMessage.setContent(userContent);
        userMessage.setSession(session);
        messageRepository.save(userMessage);

        String replyText = generateReply(userContent, userId, portfolioId);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(replyText);
        assistantMessage.setSession(session);
        ChatMessage savedAssistant = messageRepository.save(assistantMessage);

        // Auto-title the session from the first user message
        if ("New conversation".equals(session.getTitle())) {
            session.setTitle(userContent.length() > 50 ? userContent.substring(0, 50) + "…" : userContent);
        }
        session.setUpdatedAt(java.time.LocalDateTime.now());
        sessionRepository.save(session);

        return assistantMessage;
    }

    // ------------------------------------------------------------------
    // Rule-based intent matching against REAL backend data
    // ------------------------------------------------------------------
    private String generateReply(String message, String userId, String portfolioId) {
        String lower = message.toLowerCase();

        try {
            // "Why is my portfolio risky?" / general risk question - loosened to catch
            // any phrasing containing "risky" or "risk" alongside "portfolio" or "my"
            if ((lower.contains("risky") || (lower.contains("risk") && (lower.contains("portfolio") || lower.contains("my"))))
                    && portfolioId != null) {
                return explainPortfolioRisk(portfolioId, userId);
            }

            // "Explain my VaR" / "what is VaR"
            if (lower.contains("var") || lower.contains("value at risk")) {
                return explainVaR(portfolioId, userId);
            }

            // "What does a high beta mean?" / beta questions
            if (lower.contains("beta")) {
                return explainBeta(portfolioId, userId);
            }

            // Sharpe ratio questions
            if (lower.contains("sharpe")) {
                return explainSharpe(portfolioId, userId);
            }

            // "Which stock is contributing most to my risk?"
            if (matchesAny(lower, "contributing most", "biggest risk", "riskiest position")) {
                return explainBiggestRiskContributor(portfolioId, userId);
            }

            // "Why did PortfolioGuard flag an anomaly?"
            if (lower.contains("anomaly") || lower.contains("anomalies")) {
                return explainAnomalies(portfolioId, userId);
            }

            // Check for company names first (e.g. "tesla", "apple") and resolve to real tickers
            java.util.List<String> tickers = new java.util.ArrayList<>();
            String lowerForNames = message.toLowerCase();
            for (var entry : COMPANY_NAME_TO_TICKER.entrySet()) {
                if (lowerForNames.contains(entry.getKey()) && !tickers.contains(entry.getValue())) {
                    tickers.add(entry.getValue());
                }
            }

            // "Summarize AAPL's recent pattern" / "Compare TSLA and NVDA risk" — ticker-driven
            Matcher m = TICKER_PATTERN.matcher(message.toUpperCase());
            while (m.find()) {
                String candidate = m.group();
                // filter out common English words that happen to be all-caps after toUpperCase
                String c = candidate.toLowerCase();
                boolean isCommonWord = matchesAny(c,
                        "what", "why", "how", "the", "and", "for", "are", "you", "can",
                        "is", "my", "me", "it", "be", "to", "of", "in", "on", "at", "by",
                        "or", "if", "so", "do", "did", "has", "had", "have", "this", "that",
                        "risky", "risk", "with", "from", "about", "today", "now", "all",
                        "any", "but", "not", "was", "were", "will", "would", "should",
                        "could", "than", "then", "more", "most", "less", "much", "very");
                if (!isCommonWord && !tickers.contains(candidate)) {
                    tickers.add(candidate);
                }
            }
            if (!tickers.isEmpty()) {
                return summarizeTickers(tickers);
            }

            // "What should I pay attention to in this portfolio?"
            if (matchesAny(lower, "pay attention", "what should i", "overview", "summary")) {
                return explainPortfolioRisk(portfolioId, userId);
            }

            return "I can help explain your portfolio risk, VaR, Sharpe ratio, Beta, anomalies, or look up a specific "
                    + "stock ticker for you. Try asking something like \"Explain my VaR in simple terms\" or "
                    + "\"Summarize AAPL's recent pattern.\"";

        } catch (Exception e) {
            return "I ran into an issue pulling that data. Make sure you have a portfolio selected with at least one position.";
        }
    }

    private boolean matchesAny(String text, String... phrases) {
        for (String p : phrases) if (text.contains(p)) return true;
        return false;
    }

    private String explainPortfolioRisk(String portfolioId, String userId) {
        if (portfolioId == null) return "Please select a portfolio first so I can analyze its risk.";
        Portfolio portfolio = portfolioService.getPortfolioForUser(portfolioId, userId);
        double sharpe = portfolioService.calculateSharpeRatio(portfolioId);
        double pnl = portfolioService.calculatePnL(portfolioId);
        int positions = portfolio.getStocks().size();

        StringBuilder sb = new StringBuilder();
        sb.append("Your portfolio \"").append(portfolio.getName()).append("\" has ").append(positions)
          .append(" position(s) with a current P&L of $").append(String.format("%.2f", pnl)).append(". ");
        sb.append("Its Sharpe ratio is ").append(String.format("%.2f", sharpe)).append(" — ");
        if (sharpe > 1.5) sb.append("that's a strong risk-adjusted return.");
        else if (sharpe > 0.5) sb.append("that's a moderate risk-adjusted return.");
        else sb.append("that's a relatively weak risk-adjusted return, meaning you're taking on risk without much reward to show for it.");

        if (positions <= 2) {
            sb.append(" With only ").append(positions).append(" position(s), concentration risk is a real concern — a single bad move in one stock can swing your whole portfolio.");
        }
        return sb.toString();
    }

    private String explainVaR(String portfolioId, String userId) {
        if (portfolioId == null || portfolioService.getPortfolio(portfolioId).getStocks().isEmpty()) {
            return "Value at Risk (VaR) estimates the worst loss you'd expect on a normal bad day, at a given confidence level. "
                    + "For example, a 1-day 95% VaR of -3% means there's a 95% chance you won't lose more than 3% of your portfolio's value tomorrow. "
                    + "Select a portfolio with positions and I can calculate your actual VaR.";
        }
        Portfolio portfolio = portfolioService.getPortfolioForUser(portfolioId, userId);
        List<Double> returns = riskMetricsService.getRealReturns(portfolio.getStocks().get(0).getTicker(), 100);
        double var95 = riskMetricsService.calculateVaR(returns, 0.95);
        return "Your portfolio's 1-day Value at Risk at 95% confidence is " + String.format("%.2f", var95)
                + "%. In plain terms: on a normal bad day, there's a 95% chance you won't lose more than "
                + String.format("%.2f", Math.abs(var95)) + "% of your portfolio's value. The remaining 5% covers more extreme, less likely scenarios.";
    }

    private String explainBeta(String portfolioId, String userId) {
        String base = "Beta measures how much a stock or portfolio moves relative to the overall market (usually the S&P 500). "
                + "A beta of 1.0 means it moves in lockstep with the market. A beta above 1.0 (like 1.5) means it tends to amplify market moves — "
                + "rising more in good times and falling more in bad times. A beta below 1.0 means it's more defensive/stable than the market.";
        if (portfolioId == null) return base;
        try {
            Portfolio portfolio = portfolioService.getPortfolioForUser(portfolioId, userId);
            if (portfolio.getStocks().isEmpty()) return base;
            List<Double> returns = riskMetricsService.getRealReturns(portfolio.getStocks().get(0).getTicker(), 100);
            List<Double> marketReturns = riskMetricsService.getRealReturns("SPY", 100);
            double beta = riskMetricsService.calculateBeta(returns, marketReturns);
            return base + " Your portfolio's current beta is " + String.format("%.2f", beta) + ", meaning it "
                    + (beta > 1.2 ? "moves noticeably more than the market — higher upside, but also higher downside."
                       : beta < 0.8 ? "is more defensive than the broader market." : "moves roughly in line with the broader market.");
        } catch (Exception e) {
            return base;
        }
    }

    private String explainSharpe(String portfolioId, String userId) {
        String base = "Sharpe ratio measures return per unit of risk — it tells you whether your returns are worth the volatility you're taking on. "
                + "Above 1.0 is generally considered good, above 2.0 is very good, and above 3.0 is exceptional.";
        if (portfolioId == null) return base;
        double sharpe = portfolioService.calculateSharpeRatio(portfolioId);
        return base + " Your current portfolio's Sharpe ratio is " + String.format("%.2f", sharpe) + ".";
    }

    private String explainBiggestRiskContributor(String portfolioId, String userId) {
        if (portfolioId == null) return "Select a portfolio first so I can identify which position is contributing the most risk.";
        Portfolio portfolio = portfolioService.getPortfolioForUser(portfolioId, userId);
        if (portfolio.getStocks().isEmpty()) return "This portfolio has no positions yet.";

        Stock riskiest = portfolio.getStocks().get(0);
        double biggestSwing = 0;
        for (Stock s : portfolio.getStocks()) {
            double exposure = Math.abs(s.getCurrentPrice() - s.getPurchasePrice()) * s.getQuantity();
            if (exposure > biggestSwing) {
                biggestSwing = exposure;
                riskiest = s;
            }
        }
        return riskiest.getTicker() + " is currently your largest swing in dollar terms (~$" + String.format("%.2f", biggestSwing)
                + " of unrealized P&L movement), making it the biggest single contributor to your portfolio's risk right now.";
    }

    private String explainAnomalies(String portfolioId, String userId) {
        return "PortfolioGuard's anomaly detection uses an Isolation Forest model that monitors five dimensions at once: "
                + "daily return, Sharpe ratio, beta, VaR, and average correlation. When your portfolio's combined behavior across "
                + "these dimensions looks statistically unusual compared to its own recent history, it gets flagged as an anomaly — "
                + "this can happen even if no single metric looks extreme on its own, because the model looks at how they move together.";
    }

    private String summarizeTickers(List<String> tickers) {
        StringBuilder sb = new StringBuilder();
        for (String ticker : tickers) {
            try {
                var quote = stockSearchService.getQuote(ticker);
                String sentLabel = null;
                try {
                    var sentiment = sentimentService.getSentiment(ticker);
                    sentLabel = (String) sentiment.get("label");
                } catch (Exception ignored) {}

                sb.append(ticker).append(" is trading at $").append(String.format("%.2f", quote.price()))
                  .append(" (").append(quote.changePercent() >= 0 ? "+" : "").append(String.format("%.2f", quote.changePercent())).append("% today)");
                if (sentLabel != null) sb.append(", with ").append(sentLabel.toLowerCase()).append(" news sentiment");
                sb.append(". ");
            } catch (Exception e) {
                sb.append("I couldn't find live data for ").append(ticker).append(" right now. ");
            }
        }
        if (tickers.size() > 1) {
            sb.append("Comparing the two: the one with the larger price swing today carries more short-term volatility risk.");
        }
        return sb.toString();
    }
}
