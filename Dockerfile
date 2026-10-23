# ---------- Build stage ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
COPY src ./src
RUN mkdir -p out && javac -d out src/trading/*.java

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/out ./classes
COPY static ./static

# Render injects PORT; DATA_DIR is where portfolios are saved.
ENV PORT=8080
ENV DATA_DIR=/app/data
RUN mkdir -p /app/data
EXPOSE 8080

CMD ["java", "-Xmx256m", "-cp", "classes", "trading.Main"]
