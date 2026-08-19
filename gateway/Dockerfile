# build context는 저장소 루트(msa-k3s-lab/)여야 한다.
# docker build -f gateway/Dockerfile -t gateway .

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY product-service product-service
COPY order-service order-service
COPY gateway gateway
RUN ./gradlew :gateway:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/gateway/build/libs/*.jar app.jar
EXPOSE 8093
ENTRYPOINT ["java", "-jar", "app.jar"]
