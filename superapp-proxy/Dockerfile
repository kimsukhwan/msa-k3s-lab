# build context는 저장소 루트(msa-k3s-lab/)여야 한다.
# docker build -f superapp-proxy/Dockerfile -t superapp-proxy .

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY product-service product-service
COPY order-service order-service
COPY gateway gateway
COPY auth-service auth-service
COPY superapp-proxy superapp-proxy
RUN ./gradlew :superapp-proxy:bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/superapp-proxy/build/libs/*.jar app.jar
EXPOSE 8095
ENTRYPOINT ["java", "-jar", "app.jar"]
