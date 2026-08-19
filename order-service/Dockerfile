# build context는 저장소 루트(msa-k3s-lab/)여야 한다.
# docker build -f order-service/Dockerfile -t order-service .

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY product-service product-service
COPY order-service order-service
COPY gateway gateway
RUN ./gradlew :order-service:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/order-service/build/libs/*.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "app.jar"]
