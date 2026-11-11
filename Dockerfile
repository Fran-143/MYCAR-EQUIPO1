# MyCar backend — imagen mínima para Render Free (512 MB) + Neon
# Build: docker build -t mycar-backend .
# Secretos SOLO por env en runtime (nunca en la imagen).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml ./
COPY backend/src ./src
RUN mvn -B -DskipTests package \
    && cp target/backend-0.0.1-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 --create-home mycar
COPY --from=build /workspace/app.jar /app/app.jar
USER mycar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55.0 -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=160m -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=neon,render \
    SERVER_PORT=8080 \
    MYCAR_ENV_FILE=optional:file:/dev/null

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
