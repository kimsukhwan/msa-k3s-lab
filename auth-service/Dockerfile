# build context는 저장소 루트(msa-k3s-lab/)여야 한다.
# docker build -f auth-service/Dockerfile -t auth-service .

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY product-service product-service
COPY order-service order-service
COPY gateway gateway
COPY auth-service auth-service
RUN ./gradlew :auth-service:bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/auth-service/build/libs/*.jar app.jar
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar otel-javaagent.jar
ENV OTEL_SERVICE_NAME=auth-service
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4318
ENV OTEL_TRACES_EXPORTER=otlp
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_LOGS_EXPORTER=none
EXPOSE 8094
ENTRYPOINT ["java", "-javaagent:/app/otel-javaagent.jar", "-jar", "app.jar"]
