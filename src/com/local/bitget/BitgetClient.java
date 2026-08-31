package com.local.bitget;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class BitgetClient {
    static final String API_BASE = "https://api.bitget.com";
    private static final String[] MAJOR_SYMBOLS = {"BTCUSDT", "ETHUSDT", "SOLUSDT"};
    private final HttpClient http;
    private volatile String resolvedAccountMode;

    BitgetClient() {
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    static String buildSignature(String timestamp, String method, String requestPath,
                                 String queryString, String body, String secretKey) {
        String query = queryString == null || queryString.isEmpty() ? "" : "?" + queryString;
        String message = timestamp + method.toUpperCase(Locale.ROOT) + requestPath + query
                + (body == null ? "" : body);
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(hmac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", error);
        }
    }

    Models.Dashboard fetchMarketDashboard() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("productType", "USDT-FUTURES");
        Object data = requestJson("/api/v2/mix/market/tickers", params, null);
        return parseMarket(data, System.currentTimeMillis());
    }

    Models.AccountSnapshot fetchAccount(Models.Credentials credentials) {
        credentials.validate();
        if ("uta".equals(credentials.accountMode)) return fetchUta(credentials);
        if ("classic".equals(credentials.accountMode)) return fetchClassic(credentials);
        if ("uta".equals(resolvedAccountMode)) return fetchUta(credentials);
        if ("classic".equals(resolvedAccountMode)) return fetchClassic(credentials);

        ApiException utaError;
        try {
            Models.AccountSnapshot result = fetchUta(credentials);
            resolvedAccountMode = "uta";
            return result;
        } catch (ApiException error) {
            utaError = error;
        }
        try {
            Models.AccountSnapshot result = fetchClassic(credentials);
            resolvedAccountMode = "classic";
            return result;
        } catch (ApiException error) {
            throw utaError;
        }
    }

    private Models.AccountSnapshot fetchClassic(Models.Credentials credentials) {
        Map<String, String> accountParams = Collections.singletonMap("productType", "USDT-FUTURES");
        Map<String, String> positionParams = new LinkedHashMap<>();
        positionParams.put("productType", "USDT-FUTURES");
        positionParams.put("marginCoin", "USDT");
        Object accounts = requestJson("/api/v2/mix/account/accounts", accountParams, credentials);
        Object positions = requestJson("/api/v2/mix/position/all-position", positionParams, credentials);
        return parseClassic(accounts, positions);
    }

    private Models.AccountSnapshot fetchUta(Models.Credentials credentials) {
        Object account = requestJson("/api/v3/account/assets", Collections.emptyMap(), credentials);
        Object positions = requestJson("/api/v3/position/current-position",
                Collections.singletonMap("category", "USDT-FUTURES"), credentials);
        return parseUta(account, positions);
    }

    private Object requestJson(String path, Map<String, String> params, Models.Credentials credentials) {
        String query = encodeQuery(params);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + path + (query.isEmpty() ? "" : "?" + query)))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("locale", "en-US")
                .header("User-Agent", "bitget-private-terminal-java/3.1.1")
                .GET();
        if (credentials != null) {
            credentials.validate();
            String timestamp = Long.toString(System.currentTimeMillis());
            builder.header("ACCESS-KEY", credentials.apiKey)
                    .header("ACCESS-SIGN", buildSignature(timestamp, "GET", path, query, "", credentials.secretKey))
                    .header("ACCESS-TIMESTAMP", timestamp)
                    .header("ACCESS-PASSPHRASE", credentials.passphrase);
        }

        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new ClientException("Bitget network error: " + safeMessage(error), error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ClientException("Bitget request interrupted", error);
        }

        Object decoded;
        try {
            decoded = MiniJson.parse(response.body());
        } catch (RuntimeException error) {
            throw new ClientException("Bitget returned invalid JSON (HTTP " + response.statusCode() + ")", error);
        }
        if (!(decoded instanceof Map)) throw new ClientException("Bitget returned a non-object response");
        Map<?, ?> payload = (Map<?, ?>) decoded;
        String code = string(payload.get("code"));
        String message = string(payload.get("msg"));
        if (response.statusCode() < 200 || response.statusCode() >= 300 || !"00000".equals(code)) {
            throw new ApiException(code.isEmpty() ? Integer.toString(response.statusCode()) : code,
                    message.isEmpty() ? "request rejected" : message);
        }
        return payload.get("data");
    }

    private static String encodeQuery(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            parts.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return String.join("&", parts);
    }

    static Models.Dashboard parseMarket(Object data, long now) {
        if (!(data instanceof List)) throw new ClientException("ticker list is missing");
        Map<String, Models.Quote> quoteBySymbol = new LinkedHashMap<>();
        List<Models.Quote> all = new ArrayList<>();
        long latest = 0;
        for (Object row : (List<?>) data) {
            if (!(row instanceof Map)) continue;
            Map<?, ?> item = (Map<?, ?>) row;
            String symbol = string(item.get("symbol")).toUpperCase(Locale.ROOT);
            if (!symbol.endsWith("USDT") || symbol.contains("_")) continue;
            double price = number(item.get("lastPr"));
            double volume = number(first(item, "quoteVolume", "usdtVolume"));
            if (price <= 0 || volume <= 0) continue;
            Models.Quote quote = new Models.Quote(symbol, price, number(item.get("change24h")) * 100.0);
            all.add(quote);
            quoteBySymbol.put(symbol, quote);
            latest = Math.max(latest, longNumber(item.get("ts")));
        }
        Map<String, Models.Quote> majors = new LinkedHashMap<>();
        for (String symbol : MAJOR_SYMBOLS) {
            Models.Quote quote = quoteBySymbol.get(symbol);
            if (quote == null) throw new ClientException("missing market quote: " + symbol);
            majors.put(symbol, quote);
        }
        all.sort(Comparator.comparingDouble((Models.Quote quote) -> quote.changePct).reversed());
        List<Models.Quote> gainers = new ArrayList<>(all.subList(0, Math.min(5, all.size())));
        return new Models.Dashboard(majors, gainers, latest == 0 ? now : latest);
    }

    static Models.AccountSnapshot parseClassic(Object accountsObject, Object positionsObject) {
        if (!(accountsObject instanceof List) || !(positionsObject instanceof List)) {
            throw new ClientException("classic account response is incomplete");
        }
        double equity = 0, available = 0, pnl = 0;
        for (Object row : (List<?>) accountsObject) {
            if (!(row instanceof Map)) continue;
            Map<?, ?> item = (Map<?, ?>) row;
            equity += number(first(item, "usdtEquity", "accountEquity"));
            available += number(item.get("available"));
            pnl += number(item.get("unrealizedPL"));
        }
        List<Models.Position> positions = new ArrayList<>();
        for (Object row : (List<?>) positionsObject) {
            if (!(row instanceof Map)) continue;
            Map<?, ?> item = (Map<?, ?>) row;
            double size = number(item.get("total"));
            if (size <= 0) continue;
            double positionPnl = number(item.get("unrealizedPL"));
            double margin = number(item.get("marginSize"));
            positions.add(new Models.Position(string(item.get("symbol")),
                    string(item.get("holdSide")).toUpperCase(Locale.ROOT), size,
                    number(item.get("leverage")), number(item.get("openPriceAvg")),
                    number(item.get("markPrice")), positionPnl, margin == 0 ? 0 : positionPnl / margin));
        }
        return new Models.AccountSnapshot(equity, available, pnl, positions, "CLASSIC");
    }

    static Models.AccountSnapshot parseUta(Object accountObject, Object positionsObject) {
        if (!(accountObject instanceof Map) || !(positionsObject instanceof Map)) {
            throw new ClientException("UTA account response is incomplete");
        }
        Map<?, ?> account = (Map<?, ?>) accountObject;
        Object listObject = ((Map<?, ?>) positionsObject).get("list");
        if (!(listObject instanceof List)) throw new ClientException("UTA position list is missing");
        List<Models.Position> positions = new ArrayList<>();
        for (Object row : (List<?>) listObject) {
            if (!(row instanceof Map)) continue;
            Map<?, ?> item = (Map<?, ?>) row;
            double size = number(item.get("total"));
            if (size <= 0) continue;
            positions.add(new Models.Position(string(item.get("symbol")),
                    string(item.get("posSide")).toUpperCase(Locale.ROOT), size,
                    number(item.get("leverage")), number(item.get("avgPrice")),
                    number(item.get("markPrice")), number(item.get("unrealisedPnl")),
                    number(item.get("profitRate"))));
        }
        double available = 0;
        Object assetsObject = account.get("assets");
        if (assetsObject instanceof List) {
            for (Object row : (List<?>) assetsObject) {
                if (row instanceof Map && "USDT".equalsIgnoreCase(string(((Map<?, ?>) row).get("coin")))) {
                    available = number(((Map<?, ?>) row).get("available"));
                    break;
                }
            }
        }
        if (available == 0) available = number(account.get("effEquity"));
        return new Models.AccountSnapshot(number(first(account, "usdtEquity", "accountEquity")),
                available, number(first(account, "usdtUnrealisedPnl", "unrealisedPnl")), positions, "UTA");
    }

    private static Object first(Map<?, ?> map, String first, String second) {
        Object value = map.get(first);
        if (value == null || string(value).isEmpty()) value = map.get(second);
        return value;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double number(Object value) {
        if (value == null || string(value).isEmpty()) return 0;
        try { return Double.parseDouble(string(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static long longNumber(Object value) {
        if (value == null || string(value).isEmpty()) return 0;
        try { return Long.parseLong(string(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    static class ClientException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        ClientException(String message) { super(message); }
        ClientException(String message, Throwable cause) { super(message, cause); }
    }

    static final class ApiException extends ClientException {
        private static final long serialVersionUID = 1L;
        final String code;
        ApiException(String code, String message) {
            super("Bitget " + code + ": " + message);
            this.code = code;
        }
    }
}
