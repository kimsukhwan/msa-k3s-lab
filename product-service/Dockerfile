# build context는 저장소 루트(msa-k3s-lab/)여야 한다 — settings.gradle.kts가 3개 모듈을 함께 본다.
# docker build -f product-service/Dockerfile -t product-service .

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY product-service product-service
COPY order-service order-service
COPY gateway gateway
COPY auth-service auth-service
RUN ./gradlew :product-service:bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/product-service/build/libs/*.jar app.jar
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar otel-javaagent.jar
ENV OTEL_SERVICE_NAME=product-service
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4318
ENV OTEL_TRACES_EXPORTER=otlp
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_LOGS_EXPORTER=none
EXPOSE 8091
ENTRYPOINT ["java", "-javaagent:/app/otel-javaagent.jar", "-jar", "app.jar"]
