# Tests run before this build: they need Docker themselves, which is not available inside a build.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN sh mvnw -B -q dependency:go-offline
COPY src src
RUN sh mvnw -B -q package -DskipTests
RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination /layers

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
# One COPY per layer: a code change does not re-send the unchanged libraries.
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER app
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/actuator/health/readiness || exit 1
# Exec form: Java receives the stop signal directly and can shut down gracefully.
ENTRYPOINT ["java", "-jar", "appointme.jar"]
