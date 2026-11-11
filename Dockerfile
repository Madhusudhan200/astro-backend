# Works on Hugging Face Spaces (Docker, port 7860) and Render (sets its own PORT).

# ---------- build stage ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---------- run stage ----------
FROM eclipse-temurin:17-jre
# Hugging Face runs containers as user id 1000; give that user a writable app folder
RUN useradd -m -u 1000 appuser || true
WORKDIR /app
COPY --from=build /build/target/kp-jyotish.jar app.jar
# Runtime files (place database, fonts, generated PDFs; H2 database if no DB_URL is set)
RUN mkdir -p /app/data && chown -R 1000:1000 /app
USER 1000
ENV PORT=7860
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Djava.awt.headless=true -Dfile.encoding=UTF-8"
EXPOSE 7860
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
