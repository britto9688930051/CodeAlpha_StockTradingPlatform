package trading;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** HTTP entry point: serves the UI and a small JSON API. */
public class Main {
    interface Handler { String handle(Map<String, String> p) throws Exception; }

    private static MarketService market;
    private static UserRepository repo;
    private static TradingService trading;

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Path dataDir = Path.of(System.getenv().getOrDefault("DATA_DIR", "data"));

        market = new MarketService();
        market.start();
        repo = new UserRepository(dataDir);
        trading = new TradingService(market, repo);
        trading.startSnapshots();

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));

        api(server, "/api/login", p -> {
            User u = repo.getOrCreate(p.get("user"));
            trading.snapshot(u);
            return "{\"user\":" + q(u.getUsername()) + "}";
        });
        api(server, "/api/market", p -> marketJson());
        api(server, "/api/portfolio", p -> portfolioJson(repo.find(p.get("user"))));
        api(server, "/api/transactions", p -> transactionsJson(repo.find(p.get("user"))));
        api(server, "/api/performance", p -> performanceJson(repo.find(p.get("user"))));
        api(server, "/api/trade", p -> {
            User u = repo.find(p.get("user"));
            int qty;
            try { qty = Integer.parseInt(p.getOrDefault("qty", "")); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Quantity must be a whole number"); }
            String side = p.getOrDefault("side", "").toUpperCase();
            Transaction t = switch (side) {
                case "BUY" -> trading.buy(u, p.get("symbol"), qty);
                case "SELL" -> trading.sell(u, p.get("symbol"), qty);
                default -> throw new IllegalArgumentException("side must be BUY or SELL");
            };
            return String.format("{\"ok\":true,\"message\":%s}", q(String.format("%s %d %s @ $%.2f",
                    t.type(), t.quantity(), t.symbol(), t.price())));
        });

        server.createContext("/", ex -> {
            Path file = Path.of("static", "index.html");
            if (!ex.getRequestURI().getPath().equals("/") || !Files.exists(file)) {
                send(ex, 404, "text/plain", "Not found");
                return;
            }
            send(ex, 200, "text/html; charset=utf-8", Files.readString(file));
        });

        server.start();
        System.out.println("Stock trading platform running on port " + port + ", data dir: " + dataDir.toAbsolutePath());
    }

    // ---------- JSON builders ----------

    private static String marketJson() {
        StringBuilder sb = new StringBuilder("[");
        List<Stock> all = market.getAll();
        for (int i = 0; i < all.size(); i++) {
            Stock s = all.get(i);
            if (i > 0) sb.append(',');
            sb.append(String.format("{\"symbol\":%s,\"name\":%s,\"price\":%.2f,\"changePct\":%.2f}",
                    q(s.getSymbol()), q(s.getName()), s.getPrice(), s.getChangePercent()));
        }
        return sb.append(']').toString();
    }

    private static String portfolioJson(User u) {
        synchronized (u) {
            double holdingsValue = u.holdingsValue(market);
            double total = u.getCash() + holdingsValue;
            double pnl = total - User.STARTING_CASH;
            StringBuilder h = new StringBuilder("[");
            boolean first = true;
            for (Holding x : u.getHoldings().values()) {
                double price = market.get(x.getSymbol()).getPrice();
                if (!first) h.append(',');
                first = false;
                h.append(String.format("{\"symbol\":%s,\"qty\":%d,\"avgCost\":%.2f,\"price\":%.2f,\"value\":%.2f,\"pnl\":%.2f}",
                        q(x.getSymbol()), x.getQuantity(), x.getAvgCost(), price,
                        price * x.getQuantity(), (price - x.getAvgCost()) * x.getQuantity()));
            }
            h.append(']');
            return String.format("{\"cash\":%.2f,\"holdingsValue\":%.2f,\"total\":%.2f,\"pnl\":%.2f,\"pnlPct\":%.2f,\"holdings\":%s}",
                    u.getCash(), holdingsValue, total, pnl, pnl / User.STARTING_CASH * 100, h);
        }
    }

    private static String transactionsJson(User u) {
        synchronized (u) {
            StringBuilder sb = new StringBuilder("[");
            List<Transaction> list = u.getTransactions();
            int count = 0;
            for (int i = list.size() - 1; i >= 0 && count < 50; i--, count++) {
                Transaction t = list.get(i);
                if (count > 0) sb.append(',');
                sb.append(String.format("{\"time\":%d,\"type\":\"%s\",\"symbol\":%s,\"qty\":%d,\"price\":%.2f,\"total\":%.2f}",
                        t.timestamp(), t.type(), q(t.symbol()), t.quantity(), t.price(), t.total()));
            }
            return sb.append(']').toString();
        }
    }

    private static String performanceJson(User u) {
        synchronized (u) {
            StringBuilder sb = new StringBuilder("[");
            List<Snapshot> list = u.getSnapshots();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(String.format("{\"time\":%d,\"value\":%.2f}", list.get(i).timestamp(), list.get(i).totalValue()));
            }
            return sb.append(']').toString();
        }
    }

    // ---------- HTTP helpers ----------

    private static void api(HttpServer server, String path, Handler h) {
        server.createContext(path, ex -> {
            try {
                Map<String, String> params = parseParams(ex);
                send(ex, 200, "application/json", h.handle(params));
            } catch (IllegalArgumentException | IllegalStateException e) {
                send(ex, 400, "application/json", "{\"error\":" + q(e.getMessage()) + "}");
            } catch (Exception e) {
                e.printStackTrace();
                send(ex, 500, "application/json", "{\"error\":\"Internal server error\"}");
            }
        });
    }

    private static Map<String, String> parseParams(HttpExchange ex) throws IOException {
        Map<String, String> map = new HashMap<>();
        parseInto(map, ex.getRequestURI().getRawQuery());
        parseInto(map, new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        return map;
    }

    private static void parseInto(Map<String, String> map, String s) {
        if (s == null || s.isBlank()) return;
        for (String pair : s.split("&")) {
            String[] kv = pair.split("=", 2);
            map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, bytes.length);
        try (var os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
            }
        }
        return sb.append('"').toString();
    }
}
