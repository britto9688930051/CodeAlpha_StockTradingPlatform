package trading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-based persistence. One text file per user:
 *   CASH|amount
 *   HOLD|symbol|qty|avgCost
 *   TX|timestamp|type|symbol|qty|price
 *   SNAP|timestamp|totalValue
 */
public class UserRepository {
    private final Path dir;
    private final Map<String, User> users = new ConcurrentHashMap<>();

    public UserRepository(Path dir) throws IOException {
        this.dir = dir;
        Files.createDirectories(dir);
        loadAll();
    }

    public static String validate(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{1,20}"))
            throw new IllegalArgumentException("Username must be 1-20 letters, digits or underscores");
        return name.toLowerCase();
    }

    /** Returns the user, creating a fresh account if new. */
    public User getOrCreate(String rawName) {
        String name = validate(rawName);
        return users.computeIfAbsent(name, n -> {
            User u = new User(n);
            save(u);
            return u;
        });
    }

    public User find(String rawName) {
        User u = users.get(validate(rawName));
        if (u == null) throw new IllegalArgumentException("Unknown user. Log in first.");
        return u;
    }

    public Collection<User> all() { return users.values(); }

    public void save(User u) {
        List<String> lines = new ArrayList<>();
        synchronized (u) {
            lines.add("CASH|" + u.getCash());
            for (Holding h : u.getHoldings().values())
                lines.add("HOLD|" + h.getSymbol() + "|" + h.getQuantity() + "|" + h.getAvgCost());
            for (Transaction t : u.getTransactions())
                lines.add("TX|" + t.timestamp() + "|" + t.type() + "|" + t.symbol() + "|"
                        + t.quantity() + "|" + t.price());
            for (Snapshot s : u.getSnapshots())
                lines.add("SNAP|" + s.timestamp() + "|" + s.totalValue());
        }
        try {
            Path tmp = dir.resolve(u.getUsername() + ".tmp");
            Files.write(tmp, lines);
            Files.move(tmp, dir.resolve(u.getUsername() + ".txt"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to save " + u.getUsername() + ": " + e.getMessage());
        }
    }

    private void loadAll() throws IOException {
        try (var files = Files.list(dir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String fn = p.getFileName().toString();
                if (!fn.endsWith(".txt")) continue;
                User u = new User(fn.substring(0, fn.length() - 4));
                for (String line : Files.readAllLines(p)) {
                    String[] f = line.split("\\|");
                    try {
                        switch (f[0]) {
                            case "CASH" -> u.setCash(Double.parseDouble(f[1]));
                            case "HOLD" -> u.getHoldings().put(f[1],
                                    new Holding(f[1], Integer.parseInt(f[2]), Double.parseDouble(f[3])));
                            case "TX" -> u.getTransactions().add(new Transaction(Long.parseLong(f[1]),
                                    Transaction.Type.valueOf(f[2]), f[3],
                                    Integer.parseInt(f[4]), Double.parseDouble(f[5])));
                            case "SNAP" -> u.getSnapshots().add(
                                    new Snapshot(Long.parseLong(f[1]), Double.parseDouble(f[2])));
                            default -> { }
                        }
                    } catch (RuntimeException ignored) { /* skip corrupt line */ }
                }
                users.put(u.getUsername(), u);
            }
        }
    }
}
