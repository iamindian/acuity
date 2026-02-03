# Tunnel

## Overview
This project includes a Netty-based tunnel server and a proxy client with symmetric encryption (AES-256). The proxy client connects to the tunnel server, requests a proxy port, and then handles forwarded browser traffic by making HTTP requests and returning responses. All communication between client and server is encrypted using AES-256 symmetric encryption.

## Features
- AES-256 symmetric encryption for secure communication
- Asynchronous request handling with thread pool
- TCP-based HTTP proxy forwarding
- Shared symmetric key for client-server communication

## Quick Start

### Option 1: Auto-generate encryption key

1. Start the tunnel server (defaults to port 7000):

```powershell
mvn -q -DskipTests package
java -cp target\tunnel-1.0-SNAPSHOT.jar com.acuity.server.TunnelServerApp 7000
```

The server will generate and display a Base64-encoded encryption key. Copy this key for the client.

2. Start the proxy client with the shared key:

```powershell
java -cp target\tunnel-1.0-SNAPSHOT.jar com.acuity.client.TunnelClientApp 127.0.0.1 7000 8080 127.0.0.1 80 <BASE64_KEY>
```

### Option 2: Provide your own encryption key

1. Start the tunnel server with a custom key:

```powershell
java -cp target\tunnel-1.0-SNAPSHOT.jar com.acuity.server.TunnelServerApp 7000 <BASE64_KEY>
```

2. Start the proxy client with the same key:

```powershell
java -cp target\tunnel-1.0-SNAPSHOT.jar com.acuity.client.TunnelClientApp 127.0.0.1 7000 8080 127.0.0.1 80 <BASE64_KEY>
```

### Arguments

**Server**: `java -cp target\tunnel-1.0-SNAPSHOT.jar com.acuity.server.TunnelServerApp [port] [sharedKey]`
- `port` (optional): Server port (default: 7000)
- `sharedKey` (optional): Base64-encoded AES-256 key (if not provided, one will be generated)

**Client**: `java -cp target\tunnel-1.0-SNAPSHOT.jar com.acuity.client.TunnelClientApp [tunnelHost] [tunnelPort] [proxyPort] [targetHost] [targetPort] [sharedKey]`
- `tunnelHost` (optional): Tunnel server host (default: 127.0.0.1)
- `tunnelPort` (optional): Tunnel server port (default: 7000)
- `proxyPort` (optional): Port to request from server (default: 8080)
- `targetHost` (optional): Target host for proxying (default: 127.0.0.1)
- `targetPort` (optional): Target port for proxying (default: 80)
- `sharedKey` (optional): Base64-encoded AES-256 key (must match server's key)

3. Point a browser or HTTP client at the proxy port (8080) to send requests through the tunnel.

## Notes
- The proxy client expects raw HTTP requests from the browser and forwards the HTTP response back to the tunnel server.
- The tunnel server must be running before the proxy client connects.
- All communication between client and tunnel server is encrypted using AES-256 symmetric encryption.
- **Important**: The client and server must use the same encryption key. Either:
  - Let the server generate a key and copy it to the client, OR
  - Provide the same key to both server and client via command line arguments
- For production use, store the encryption key securely (e.g., environment variables, secrets manager) instead of passing it on the command line.
