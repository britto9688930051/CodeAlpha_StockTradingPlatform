package trading;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A trader with a cash balance, holdings, transaction log and performance history. */
public class User {
    public static final double STARTING_CASH = 10_000.00;
    private static final int MAX_SNAPSHOTS = 2000;

    private final String username;
    private double cash = STARTING_CASH;
    private final Map<String, Holding> holdings = new LinkedHashMap<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<Snapshot> snapshots = new ArrayList<>();

    public User(String username) { this.username = username; }

    public String getUsername() { return username; }
    public double getCash() { return cash; }
    public void setCash(double cash) { this.cash = cash; }
    public Map<String, Holding> getHoldings() { return holdings; }
    public List<Transaction> getTransactions() { return transactions; }
    public List<Snapshot> getSnapshots() { return snapshots; }

    public double holdingsValue(MarketService market) {
        double sum = 0;
        for (Holding h : holdings.values()) {
            sum += h.getQuantity() * market.get(h.getSymbol()).getPrice();
        }
        return sum;
    }

    public double totalValue(MarketService market) {
        return cash + holdingsValue(market);
    }

    public void addSnapshot(Snapshot s) {
        snapshots.add(s);
        if (snapshots.size() > MAX_SNAPSHOTS) snapshots.remove(0);
    }
}
