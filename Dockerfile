FROM node:24-bookworm-slim AS frontend
WORKDIR /ui
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
COPY --from=frontend /ui/dist/ app/src/main/resources/static/
RUN chmod +x gradlew && ./gradlew --no-daemon :app:bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system --gid 10001 trading && useradd --system --uid 10001 --gid trading trading \
    && install -d -o trading -g trading /data
COPY --from=build /src/app/build/libs/app-0.1.0-SNAPSHOT.jar /app/trading.jar
USER 10001:10001
ENV TRADING_JOURNAL_PATH=/data/journal.log
VOLUME ["/data"]
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/trading.jar"]
