# build context는 저장소 루트(msa-k3s-lab/)여야 한다 — settings.gradle.kts가 3개 모듈을 함께 본다.
# docker build -f product-service/Dockerfile -t product-service .

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY product-service product-service
COPY order-service order-service
COPY gateway gateway
RUN ./gradlew :product-service:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/product-service/build/libs/*.jar app.jar
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]
