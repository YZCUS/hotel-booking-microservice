FROM eclipse-temurin:21-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY build/libs/booking-service-1.0.0.jar app.jar

EXPOSE 8083 9003

ENTRYPOINT ["java", "-jar", "app.jar"]
