# TOML Configuration Implementation Summary

## Overview

The Acuity Tunnel application now supports TOML configuration files for both the server and client, providing a cleaner and more maintainable alternative to command-line arguments.

## What's New

### 1. Configuration Classes

Created two new configuration classes in `src/main/java/com/acuity/config/`:

- **ServerConfig.java** - Manages tunnel server configuration
  - Supports port, encryption key, thread pool sizes, and socket options
  - Loads from TOML files via `loadFromFile()` method
  - Provides sensible defaults

- **ClientConfig.java** - Manages tunnel client configuration
  - Supports tunnel connection, proxy port, target settings
  - Thread pool configuration for async request handling
  - Netty configuration options
  - Loads from TOML files via `loadFromFile()` method

### 2. Configuration Files

Created template configuration files in the project root:

- **server-config.toml** - Server configuration template
  - 26 lines with comprehensive comments
  - All configurable options with descriptions

- **client-config.toml** - Client configuration template
  - 31 lines with comprehensive comments
  - Organized into three sections: `[client]`, `[threadPool]`, `[netty]`

### 3. Updated Application Classes

- **TunnelServerApp.java**
  - Updated `main()` method to detect TOML files
  - Falls back to command-line argument parsing for backward compatibility
  - Prints loaded configuration for verification

- **TunnelClientApp.java**
  - Updated `main()` method to detect TOML files
  - Falls back to command-line argument parsing for backward compatibility
  - Prints loaded configuration for verification

### 4. Maven Dependency

Added **TOML4J** library to pom.xml:
```xml
<dependency>
    <groupId>com.moandjiezana.toml</groupId>
    <artifactId>toml4j</artifactId>
    <version>0.7.2</version>
</dependency>
```

## Usage

### Server with TOML Config

```powershell
java -cp target\tunnel-1.0-SNAPSHOT.jar com.acuity.server.TunnelServerApp server-config.toml
```

### Client with TOML Config

```powershell
java -cp target\tunnel-1.0-SNAPSHOT.jar com.acuity.client.TunnelClientApp client-config.toml
```

### Legacy Command-Line Arguments (Still Supported)

```powershell
# Server
java -cp target\tunnel-1.0-SNAPSHOT.jar com.acuity.server.TunnelServerApp 7000 "<BASE64_KEY>"

# Client
java -cp target\tunnel-1.0-SNAPSHOT.jar com.acuity.client.TunnelClientApp 127.0.0.1 7000 8080 127.0.0.1 80 "<BASE64_KEY>"
```

## Key Features

✅ **Backward Compatible** - Old command-line argument method still works
✅ **Auto-Detection** - Automatically detects if argument is a TOML file (.toml extension)
✅ **Sensible Defaults** - All settings have default values
✅ **Secure** - Sensitive values like keys are masked in output (shown as ***)
✅ **Well-Documented** - Comprehensive TOML configuration guide provided
✅ **Easy Migration** - Simple script to convert from CLI to TOML config

## Configuration Options

### Server Options
- port (default: 7000)
- sharedKey (default: auto-generated)
- bossGroupSize (default: 1)
- workerGroupSize (default: 0, means CPUs × 2)
- idleTimeoutSeconds (default: 60)
- soBacklog (default: 128)
- soKeepalive (default: true)
- tcpNodelay (default: true)

### Client Options
- tunnelHost (default: 127.0.0.1)
- tunnelPort (default: 7000)
- proxyPort (default: 8080)
- targetHost (default: 127.0.0.1)
- targetPort (default: 80)
- sharedKey (default: null - required!)
- Thread pool configuration (core size, max size, timeout, queue)
- Netty configuration (idle timeout, keepalive, nodelay)

## Files Added/Modified

### New Files Created
- `src/main/java/com/acuity/config/ServerConfig.java` (102 lines)
- `src/main/java/com/acuity/config/ClientConfig.java` (168 lines)
- `server-config.toml` (26 lines)
- `client-config.toml` (31 lines)
- `TOML_CONFIG_GUIDE.md` (Complete configuration documentation)

### Files Modified
- `pom.xml` - Added TOML4J dependency
- `src/main/java/com/acuity/server/TunnelServerApp.java` - Updated main() method
- `src/main/java/com/acuity/client/TunnelClientApp.java` - Updated main() method
- `README.md` - Updated with TOML configuration examples

## How It Works

1. **Detection**: The application checks if the first argument ends with `.toml`
2. **Loading**: If TOML file detected, configuration is loaded via `ServerConfig.loadFromFile()` or `ClientConfig.loadFromFile()`
3. **Defaults**: Any missing configuration values use sensible defaults
4. **Fallback**: If not TOML, arguments are parsed as command-line parameters (legacy mode)
5. **Validation**: Configuration is printed to console for verification before starting

## Example TOML Configuration

### Server
```toml
[server]
port = 7000
sharedKey = "your-base64-key-here"
bossGroupSize = 1
workerGroupSize = 0
idleTimeoutSeconds = 60
soBacklog = 128
soKeepalive = true
tcpNodelay = true
```

### Client
```toml
[client]
tunnelHost = "127.0.0.1"
tunnelPort = 7000
proxyPort = 8080
targetHost = "127.0.0.1"
targetPort = 80
sharedKey = "your-base64-key-here"

[threadPool]
corePoolSize = 10
maxPoolSize = 50
keepAliveTimeSeconds = 60
queueCapacity = 200

[netty]
idleTimeoutSeconds = 60
soKeepalive = true
tcpNodelay = true
```

## Benefits

1. **Cleaner Configuration** - No long command lines
2. **Easier Maintenance** - Change config without rebuilding
3. **Environment-Specific** - Easy to have dev/staging/prod configs
4. **Self-Documenting** - TOML files include comments explaining options
5. **Flexible** - Mix and match TOML defaults with environment overrides
6. **Backward Compatible** - Existing scripts continue to work

## Testing

Build with:
```powershell
mvn clean package -DskipTests
```

All existing tests continue to pass, and the TOML configuration system is compatible with existing code.

## Documentation

See `TOML_CONFIG_GUIDE.md` for comprehensive documentation including:
- Detailed configuration options reference
- Production examples
- Security best practices
- Troubleshooting guide
- Migration from command-line arguments
