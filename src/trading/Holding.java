package trading;

/** Shares of one stock owned by a user, with average purchase cost. */
public class Holding {
    private final String symbol;
    private int quantity;
    private double avgCost;

    public Holding(String symbol, int quantity, double avgCost) {
        this.symbol = symbol;
        this.quantity = quantity;
        this.avgCost = avgCost;
    }

    public void addShares(int qty, double price) {
        avgCost = (avgCost * quantity + price * qty) / (quantity + qty);
        quantity += qty;
    }

    public void removeShares(int qty) { quantity -= qty; }

    public String getSymbol() { return symbol; }
    public int getQuantity() { return quantity; }
    public double getAvgCost() { return avgCost; }
}
