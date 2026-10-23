package trading;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Business logic for buying, selling and tracking portfolio performance. */
public class TradingService {
    private final MarketService market;
    private final UserRepository repo;

    public TradingService(MarketService market, UserRepository repo) {
        this.market = market;
        this.repo = repo;
    }

    public Transaction buy(User u, String symbol, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be positive");
        synchronized (u) {
            Stock s = market.get(symbol);
            double price = s.getPrice();
            double cost = price * qty;
            if (cost > u.getCash() + 1e-9)
                throw new IllegalStateException(String.format(
                        "Insufficient funds: need $%.2f, have $%.2f", cost, u.getCash()));
            u.setCash(u.getCash() - cost);
            u.getHoldings().computeIfAbsent(s.getSymbol(), k -> new Holding(k, 0, 0)).addShares(qty, price);
            return record(u, Transaction.Type.BUY, s.getSymbol(), qty, price);
        }
    }

    public Transaction sell(User u, String symbol, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be positive");
        synchronized (u) {
            Stock s = market.get(symbol);
            Holding h = u.getHoldings().get(s.getSymbol());
            if (h == null || h.getQuantity() < qty)
                throw new IllegalStateException("You don't own enough shares of " + s.getSymbol());
            double price = s.getPrice();
            u.setCash(u.getCash() + price * qty);
            h.removeShares(qty);
            if (h.getQuantity() == 0) u.getHoldings().remove(s.getSymbol());
            return record(u, Transaction.Type.SELL, s.getSymbol(), qty, price);
        }
    }

    private Transaction record(User u, Transaction.Type type, String sym, int qty, double price) {
        Transaction t = new Transaction(System.currentTimeMillis(), type, sym, qty, price);
        u.getTransactions().add(t);
        u.addSnapshot(new Snapshot(t.timestamp(), u.totalValue(market)));
        repo.save(u);
        return t;
    }

    public void snapshot(User u) {
        synchronized (u) {
            u.addSnapshot(new Snapshot(System.currentTimeMillis(), u.totalValue(market)));
        }
        repo.save(u);
    }

    /** Record every user's portfolio value periodically so performance can be charted. */
    public void startSnapshots() {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "snapshotter");
            t.setDaemon(true);
            return t;
        });
        ex.scheduleAtFixedRate(() -> repo.all().forEach(this::snapshot), 30, 30, TimeUnit.SECONDS);
    }
}
