# Stage 1: Build stage
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Copy gradle executable and configuration
COPY gradlew .
RUN chmod +x gradlew
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# Copy shared modules and backend
COPY shared-models shared-models
COPY backend backend

# Create dummy directories and files for other modules to satisfy Gradle configuration
# without copying their entire contents (to save memory/time)
RUN mkdir -p app app-driver app-customer core && \
    touch app/build.gradle.kts app-driver/build.gradle.kts app-customer/build.gradle.kts core/build.gradle.kts

# Build the backend application
# Use lower memory settings for Gradle inside Docker
RUN ./gradlew :backend:installDist --no-daemon -Dorg.gradle.jvmargs="-Xmx384m"

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy built application from build stage
COPY --from=build /app/backend/build/install/backend /app/backend

# Use the PORT environment variable provided by Render
ENV PORT=8080
# Set Java memory limits for the running app as well
ENV JAVA_OPTS="-Xmx256m"
EXPOSE $PORT

# Start the application
CMD ["sh", "-c", "/app/backend/bin/backend"]
