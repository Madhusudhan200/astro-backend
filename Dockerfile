# ---------- build stage ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---------- run stage ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/kp-jyotish.jar app.jar
# Persistent data (H2 database, generated PDFs, fonts, place database).
# On Render a persistent disk is mounted here - see render.yaml.
RUN mkdir -p /app/data
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Djava.awt.headless=true -Dfile.encoding=UTF-8"
# Render provides PORT; the app reads it (defaults to 8090 locally)
EXPOSE 8090
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
