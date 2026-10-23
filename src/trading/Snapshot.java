package trading;

/** Total portfolio value (cash + holdings) at a point in time. */
public record Snapshot(long timestamp, double totalValue) { }
