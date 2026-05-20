#!/bin/bash
# This script is used by Railway's Railpack if Dockerfile is not used.
# However, we recommend using the Dockerfile via railway.json.

echo "Installing backend distribution..."
./gradlew :backend:installDist --no-daemon

echo "Starting backend..."
./backend/build/install/backend/bin/backend
