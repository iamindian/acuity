# Tunnel

## Overview
This project includes a Netty-based tunnel server and a proxy client with SSL/TLS encryption. The proxy client connects to the tunnel server, requests a proxy port, and then handles forwarded browser traffic by making HTTP requests and returning responses. All communication between client and server is encrypted using SSL/TLS.

## Features
- SSL/TLS encrypted communication between client and server
- Self-signed certificate generation for testing
- Asynchronous request handling with thread pool
- TCP-based HTTP proxy forwarding

## Quick Start

1. Start the tunnel server (defaults to port 7000):

```powershell
mvn -q -DskipTests package
java -cp target\tunnel-1.0-SNAPSHOT.jar com.acuity.server.TunnelServerApp 7000
```

2. Start the proxy client (connects to tunnel server and requests proxy port 8080):

```powershell
java -cp target\tunnel-1.0-SNAPSHOT.jar com.acuity.client.TunnelClientApp 127.0.0.1 7000 8080 127.0.0.1 80
```

3. Point a browser or HTTP client at the proxy port (8080) to send requests through the tunnel.

## Notes
- The proxy client expects raw HTTP requests from the browser and forwards the HTTP response back to the tunnel server.
- The tunnel server must be running before the proxy client connects.
- All communication between client and tunnel server is encrypted using SSL/TLS.
- For testing, self-signed certificates are automatically generated. For production, replace with proper CA-signed certificates.
