# ---------- BUILD STAGE ----------
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# ---------- RUN STAGE ----------
# JRE-only runtime image, smaller attack surface than the full JDK.
FROM eclipse-temurin:21-jre-alpine

# Run as an unprivileged user — never root.
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
