# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and pom.xml first so dependency layers can be cached.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy the source code and build (skip tests in image build; CI runs tests separately).
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S worthit && adduser -S worthit -G worthit
COPY --from=build --chown=worthit:worthit /app/target/*.jar app.jar

EXPOSE 8080

USER worthit
ENTRYPOINT ["java", "-jar", "app.jar"]
