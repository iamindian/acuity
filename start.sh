#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

JAR="target/tunnel-1.0-SNAPSHOT.jar"
SERVER_CONFIG="${SERVER_CONFIG:-server-config.toml}"
CLIENT_CONFIG="${CLIENT_CONFIG:-client-config.toml}"

if [ ! -f "$JAR" ]; then
  mvn -q -DskipTests package
fi

case "${1:-both}" in
  server)
    java -cp "$JAR" com.acuity.server.TunnelServerApp "$SERVER_CONFIG"
    ;;
  client)
    java -cp "$JAR" com.acuity.client.TunnelClientApp "$CLIENT_CONFIG"
    ;;
  both)
    java -cp "$JAR" com.acuity.server.TunnelServerApp "$SERVER_CONFIG" &
    SERVER_PID=$!
    sleep 1
    java -cp "$JAR" com.acuity.client.TunnelClientApp "$CLIENT_CONFIG" &
    CLIENT_PID=$!
    echo "Server PID: $SERVER_PID"
    echo "Client PID: $CLIENT_PID"
    wait
    ;;
  *)
    echo "Usage: $0 [server|client|both]"
    echo "Environment overrides: SERVER_CONFIG, CLIENT_CONFIG"
    exit 1
    ;;
esac
