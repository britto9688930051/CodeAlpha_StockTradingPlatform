package trading;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Simulated market: prices follow a random walk, updated every few seconds. */
public class MarketService {
    private final Map<String, Stock> stocks = new LinkedHashMap<>();
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "market-ticker");
                t.setDaemon(true);
                return t;
            });

    public MarketService() {
        add(new Stock("AAPL", "Apple Inc.", 190.00, 0.004));
        add(new Stock("GOOGL", "Alphabet Inc.", 140.00, 0.005));
        add(new Stock("MSFT", "Microsoft Corp.", 410.00, 0.004));
        add(new Stock("AMZN", "Amazon.com Inc.", 175.00, 0.006));
        add(new Stock("TSLA", "Tesla Inc.", 250.00, 0.012));
        add(new Stock("INFY", "Infosys Ltd.", 18.50, 0.006));
        add(new Stock("TCS", "Tata Consultancy", 42.00, 0.005));
        add(new Stock("NFLX", "Netflix Inc.", 600.00, 0.008));
    }

    private void add(Stock s) { stocks.put(s.getSymbol(), s); }

    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, 3, 3, TimeUnit.SECONDS);
    }

    private void tick() {
        for (Stock s : stocks.values()) {
            double change = random.nextGaussian() * s.getVolatility();
            s.setPrice(Math.max(1.0, s.getPrice() * (1 + change)));
        }
    }

    public Stock get(String symbol) {
        Stock s = stocks.get(symbol == null ? "" : symbol.toUpperCase());
        if (s == null) throw new IllegalArgumentException("Unknown symbol: " + symbol);
        return s;
    }

    public List<Stock> getAll() { return new ArrayList<>(stocks.values()); }
}
