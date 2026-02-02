# Tunnel

## Overview
This project includes a Netty-based tunnel server and a proxy client. The proxy client connects to the tunnel server, requests a proxy port, and then handles forwarded browser traffic by making HTTP requests and returning responses.

## Quick Start

1. Start the tunnel server (defaults to port 7000):

```powershell
mvn -q -DskipTests package
java -cp target\tunnel-1.0-SNAPSHOT.jar com.acuity.server.TunnelServerApp 7000
```

2. Start the proxy client (connects to tunnel server and requests proxy port 8080):

```powershell
java -cp target\tunnel-1.0-SNAPSHOT.jar com.acuity.client.TunnelClientApp 127.0.0.1 7000 8080
```

3. Point a browser or HTTP client at the proxy port (8080) to send requests through the tunnel.

## Notes
- The proxy client expects raw HTTP requests from the browser and forwards the HTTP response back to the tunnel server.
- The tunnel server must be running before the proxy client connects.
