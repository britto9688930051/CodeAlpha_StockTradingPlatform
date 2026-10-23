package trading;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** A tradable stock whose price is updated by the MarketService. */
public class Stock {
    private static final int MAX_HISTORY = 60;

    private final String symbol;
    private final String name;
    private final double volatility;
    private final double openPrice;
    private double price;
    private final Deque<Double> history = new ArrayDeque<>();

    public Stock(String symbol, String name, double price, double volatility) {
        this.symbol = symbol;
        this.name = name;
        this.price = price;
        this.openPrice = price;
        this.volatility = volatility;
        history.add(price);
    }

    public synchronized void setPrice(double newPrice) {
        this.price = Math.round(newPrice * 100.0) / 100.0;
        history.addLast(this.price);
        if (history.size() > MAX_HISTORY) history.removeFirst();
    }

    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public double getVolatility() { return volatility; }
    public synchronized double getPrice() { return price; }
    public double getOpenPrice() { return openPrice; }

    public synchronized double getChangePercent() {
        return (price - openPrice) / openPrice * 100.0;
    }

    public synchronized List<Double> getHistory() { return new ArrayList<>(history); }
}
