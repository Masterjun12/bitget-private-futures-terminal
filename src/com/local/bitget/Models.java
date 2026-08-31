package com.local.bitget;

import java.util.List;
import java.util.Map;

final class Models {
    private Models() {}

    static final class Credentials {
        final String apiKey;
        final String secretKey;
        final String passphrase;
        final String accountMode;

        Credentials(String apiKey, String secretKey, String passphrase, String accountMode) {
            this.apiKey = apiKey == null ? "" : apiKey;
            this.secretKey = secretKey == null ? "" : secretKey;
            this.passphrase = passphrase == null ? "" : passphrase;
            this.accountMode = accountMode == null ? "auto" : accountMode.toLowerCase();
        }

        void validate() {
            if (apiKey.trim().isEmpty() || secretKey.trim().isEmpty() || passphrase.isEmpty()) {
                throw new IllegalArgumentException("API Key, Secret Key, Passphrase are all required");
            }
            if (!accountMode.equals("auto") && !accountMode.equals("classic") && !accountMode.equals("uta")) {
                throw new IllegalArgumentException("invalid account mode");
            }
        }
    }

    static final class Quote {
        final String symbol;
        final double price;
        final double changePct;

        Quote(String symbol, double price, double changePct) {
            this.symbol = symbol;
            this.price = price;
            this.changePct = changePct;
        }
    }

    static final class Dashboard {
        final Map<String, Quote> quotes;
        final List<Quote> gainers;
        final long timestampMs;

        Dashboard(Map<String, Quote> quotes, List<Quote> gainers, long timestampMs) {
            this.quotes = quotes;
            this.gainers = gainers;
            this.timestampMs = timestampMs;
        }
    }

    static final class Position {
        final String symbol;
        final String side;
        final double size;
        final double leverage;
        final double entryPrice;
        final double markPrice;
        final double unrealizedPnl;
        final double profitRate;

        Position(String symbol, String side, double size, double leverage, double entryPrice,
                 double markPrice, double unrealizedPnl, double profitRate) {
            this.symbol = symbol;
            this.side = side;
            this.size = size;
            this.leverage = leverage;
            this.entryPrice = entryPrice;
            this.markPrice = markPrice;
            this.unrealizedPnl = unrealizedPnl;
            this.profitRate = profitRate;
        }
    }

    static final class AccountSnapshot {
        final double totalEquity;
        final double available;
        final double unrealizedPnl;
        final List<Position> positions;
        final String accountMode;

        AccountSnapshot(double totalEquity, double available, double unrealizedPnl,
                        List<Position> positions, String accountMode) {
            this.totalEquity = totalEquity;
            this.available = available;
            this.unrealizedPnl = unrealizedPnl;
            this.positions = positions;
            this.accountMode = accountMode;
        }
    }
}
