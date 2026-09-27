# AgroLink on Render free: Java 25 needs Docker (native Render Java stops at 21).
# Multi-stage build: compile with Maven + JDK 25, run on a slim JRE 25 image.

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
# Cache dependencies in their own layer so rebuilds are fast.
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:25-jre-noble
# Slim images can miss the CA bundle (HTTPS to Open-Meteo/Cloudinary fails);
# IPv4 is forced because some shared hosts have broken IPv6 egress.
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/agrolink-*.jar app.jar
EXPOSE 8080
# Render injects $PORT; Spring takes it explicitly so SERVER_PORT never matters.
# -Xmx300m keeps heap + metaspace + native inside the 512 MB free box.
CMD ["sh", "-c", "java -Xmx300m -Xss512k -Djava.net.preferIPv4Stack=true $JAVA_OPTS -jar app.jar --server.port=$PORT"]
