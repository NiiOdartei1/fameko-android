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

# Build the backend application
RUN ./gradlew :backend:installDist --no-daemon

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy built application from build stage
COPY --from=build /app/backend/build/install/backend /app/backend

# Use the PORT environment variable provided by Render
ENV PORT=8080
EXPOSE $PORT

# Start the application
CMD ["/app/backend/bin/backend"]
