package com.local.bitget;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BitgetClientTest {
    private int checks;

    public static void main(String[] args) {
        BitgetClientTest suite = new BitgetClientTest();
        suite.run();
        System.out.println("JAVA_TESTS_OK checks=" + suite.checks);
    }

    private void run() {
        signatureMatchesKnownValue();
        parsesMarketAndRanksTopFive();
        parsesClassicAccount();
        parsesUtaAccount();
        credentialJsonRoundTrip();
        rejectsIncompleteCredentials();
        appliesProfitAndLossColors();
        formatsDensePricesWithoutUnneededDecimals();
    }

    private void signatureMatchesKnownValue() {
        String actual = BitgetClient.buildSignature("1684814440729", "GET",
                "/api/v2/mix/account/account", "marginCoin=usdt&symbol=btcusdt", "", "test-secret");
        equal("OvWUwOTppinYhIYQxOhgGegM1C+V6Cfc7vazrsCpdlk=", actual, "signature");
    }

    private void parsesMarketAndRanksTopFive() {
        Object data = MiniJson.parse("["
                + ticker("BTCUSDT", "60000", "0.01") + ","
                + ticker("ETHUSDT", "2400", "0.02") + ","
                + ticker("SOLUSDT", "150", "0.03") + ","
                + ticker("AAAUSDT", "1", "0.40") + ","
                + ticker("BBBUSDT", "1", "0.30") + ","
                + ticker("CCCUSDT", "1", "0.20") + ","
                + ticker("DDDUSDT", "1", "0.10") + ","
                + ticker("EEEUSDT", "1", "0.05") + ","
                + ticker("FFFUSDT", "1", "0.04") + "]");
        Models.Dashboard dashboard = BitgetClient.parseMarket(data, 999);
        equal(3, dashboard.quotes.size(), "major count");
        equal(5, dashboard.gainers.size(), "gainer count");
        equal("AAAUSDT", dashboard.gainers.get(0).symbol, "top gainer");
        close(40.0, dashboard.gainers.get(0).changePct, "change percent");
    }

    private void parsesClassicAccount() {
        Object accounts = MiniJson.parse("[{\"usdtEquity\":\"100.5\",\"available\":\"80\",\"unrealizedPL\":\"5.5\"}]");
        Object positions = MiniJson.parse("[{\"symbol\":\"ETHUSDT\",\"holdSide\":\"long\","
                + "\"total\":\"0.2\",\"leverage\":\"10\",\"openPriceAvg\":\"2000\","
                + "\"markPrice\":\"2200\",\"unrealizedPL\":\"40\",\"marginSize\":\"40\"}]");
        Models.AccountSnapshot account = BitgetClient.parseClassic(accounts, positions);
        close(100.5, account.totalEquity, "classic equity");
        equal("CLASSIC", account.accountMode, "classic mode");
        close(1.0, account.positions.get(0).profitRate, "classic profit rate");
    }

    private void parsesUtaAccount() {
        Object accountData = MiniJson.parse("{\"usdtEquity\":\"200.25\",\"usdtUnrealisedPnl\":\"-3.5\","
                + "\"assets\":[{\"coin\":\"USDT\",\"available\":\"175.1\"}]}");
        Object positionData = MiniJson.parse("{\"list\":[{\"symbol\":\"BTCUSDT\",\"posSide\":\"short\","
                + "\"total\":\"0.01\",\"leverage\":\"20\",\"avgPrice\":\"80000\","
                + "\"markPrice\":\"79000\",\"unrealisedPnl\":\"10\",\"profitRate\":\"0.25\"}]}");
        Models.AccountSnapshot account = BitgetClient.parseUta(accountData, positionData);
        close(175.1, account.available, "UTA available");
        equal("SHORT", account.positions.get(0).side, "UTA side");
        close(0.25, account.positions.get(0).profitRate, "UTA profit rate");
    }

    private void credentialJsonRoundTrip() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("api_key", "key\"1");
        values.put("secret_key", "sec\\ret");
        values.put("passphrase", "line\npass");
        values.put("account_mode", "auto");
        Object decoded = MiniJson.parse(MiniJson.stringify(values));
        equal("sec\\ret", ((Map<?, ?>) decoded).get("secret_key"), "JSON escape");
    }

    private void rejectsIncompleteCredentials() {
        boolean rejected = false;
        try { new Models.Credentials("key", "", "pass", "auto").validate(); }
        catch (IllegalArgumentException expected) { rejected = true; }
        equal(true, rejected, "credential validation");
    }

    private void appliesProfitAndLossColors() {
        equal(new Color(0, 230, 118), TerminalWindow.trendColor(1.0), "profit color");
        equal(new Color(255, 35, 55), TerminalWindow.trendColor(-1.0), "loss color");
        equal(new Color(146, 33, 48), TerminalWindow.trendColor(0.0), "flat color");
    }

    private void formatsDensePricesWithoutUnneededDecimals() {
        equal("2,432.34", TerminalWindow.formatPrice(2432.34), "two-decimal major price");
        equal("102.78", TerminalWindow.formatPrice(102.78), "two-decimal SOL price");
        equal("0.0264", TerminalWindow.formatPrice(0.026352), "small coin precision");
        equal("2,432.34", TerminalWindow.formatMoney(2432.34), "two-decimal account amount");
        equal("-12.34", TerminalWindow.signedMoney(-12.34), "signed two-decimal PnL");
    }

    private static String ticker(String symbol, String price, String change) {
        return "{\"symbol\":\"" + symbol + "\",\"lastPr\":\"" + price
                + "\",\"change24h\":\"" + change + "\",\"quoteVolume\":\"1000\",\"ts\":\"123\"}";
    }

    private void equal(Object expected, Object actual, String label) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
    }

    private void close(double expected, double actual, String label) {
        checks++;
        if (Math.abs(expected - actual) > 0.0000001) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
