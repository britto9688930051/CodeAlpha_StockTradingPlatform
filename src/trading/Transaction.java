package trading;

/** Immutable record of one buy or sell. */
public record Transaction(long timestamp, Type type, String symbol, int quantity, double price) {
    public enum Type { BUY, SELL }

    public double total() { return quantity * price; }
}
