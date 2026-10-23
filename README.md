# Stock Trading Simulator (Java)

A simulated trading platform: live-updating market, buy/sell, portfolio tracking over time, file-based persistence.

## OOP design
| Class | Role |
|---|---|
| `Stock` | A tradable symbol with price + short price history |
| `MarketService` | Simulates price movement (random walk every 3s) |
| `User` | Cash, holdings, transactions, performance snapshots |
| `Holding` | Shares of one stock + average cost |
| `Transaction` (record) | Immutable buy/sell log entry |
| `Snapshot` (record) | Portfolio value at a point in time |
| `TradingService` | Buy/sell rules, validation, snapshots |
| `UserRepository` | File I/O persistence (`data/<user>.txt`) |
| `Main` | HTTP server + JSON API + serves `static/index.html` |

## Run locally
    mkdir out && javac -d out src/trading/*.java
    java -cp out trading.Main          # http://localhost:8080

## Docker
    docker build -t stock-sim .
    docker run -p 8080:8080 stock-sim

## Deploy on Render
1. Push this folder to a GitHub repo.
2. Render → New → Web Service → connect the repo → Runtime: **Docker**.
3. Deploy. Render sets `PORT` automatically.
4. Persistence: Render's free tier has an ephemeral disk (data resets on redeploy/restart).
   For permanent storage add a Disk (paid) mounted at `/app/data`.
