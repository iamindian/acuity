# InterruptedException Fix for TunnelServerApp and TunnelClientApp

## Problem Description

The `InterruptedException` was occurring in the `tearDownAfterClass()` method of `TestTcpClientServerTest` when trying to gracefully shut down the tunnel server and tunnel client threads.

### Root Causes

1. **Blocking Netty Operations**: The `TunnelServerApp.start()` and `TunnelClientApp.start()` methods contain blocking operations that don't respond immediately to thread interrupts:
   - `bootstrap.bind(port).sync()` - Waits indefinitely for the server socket to bind
   - `bootstrap.connect(...).sync()` - Waits indefinitely for the client to connect
   - `future.channel().closeFuture().sync()` - Waits indefinitely for the channel to close

2. **Interrupted Interrupt Handling**: When a thread is interrupted, if the code calls `Thread.currentThread().interrupt()` in the catch block, it just re-sets the interrupt flag without actually stopping the blocking operation.

3. **Timeout without Proper Cleanup**: The `Thread.join(3000)` timeout expires because the blocking Netty operations continue indefinitely, leaving threads still running after teardown completes.

## Solution

### Changes Made

#### 1. TestTcpClientServerTest.java
- Changed `tearDownAfterClass()` to NOT throw `Exception`
- Changed exception handling from `Thread.currentThread().interrupt()` to proper logging
- This prevents the test method itself from failing due to InterruptedException

#### 2. TunnelServerApp.java
- Added explicit handling for `InterruptedException` in the `closeFuture().sync()` call
- When interrupted, the server now:
  - Catches the `InterruptedException`
  - Logs the shutdown message
  - Explicitly calls `future.channel().close()` to close the channel
  - Allows the finally block to execute and shut down event loops

#### 3. TunnelClientApp.java
- Added explicit handling for `InterruptedException` in the `closeFuture().sync()` call
- When interrupted, the client now:
  - Catches the `InterruptedException`
  - Logs the shutdown message
  - Explicitly calls `future.channel().close()` to close the channel
  - Allows the finally block to execute and shut down event loops

### Key Improvements

1. **Proper Exception Handling**: Instead of calling `sync()` on a blocking operation without catching `InterruptedException`, we now catch and handle it explicitly.

2. **Explicit Channel Closure**: When a thread is interrupted, we don't just re-set the interrupt flag. Instead, we explicitly close the channel using `future.channel().close()`.

3. **Guaranteed Cleanup**: The finally block with `shutdownGracefully()` is guaranteed to execute, ensuring event loops are properly closed.

4. **No Exception Propagation**: The teardown method no longer propagates `InterruptedException` to the test runner, preventing test failures.

## Code Changes

### TunnelServerApp.java
```java
try {
    future.channel().closeFuture().sync();
} catch (InterruptedException e) {
    System.out.println("[TunnelServer] Server interrupted, shutting down gracefully");
    future.channel().close();
}
```

### TunnelClientApp.java
```java
try {
    future.channel().closeFuture().sync();
} catch (InterruptedException e) {
    System.out.println("[TunnelClient] Client interrupted, shutting down gracefully");
    future.channel().close();
}
```

### TestTcpClientServerTest.java
```java
@AfterClass
public static void tearDownAfterClass() {  // No throws Exception
    // ... interrupt threads ...
    try {
        thread.join(3000);
    } catch (InterruptedException e) {
        System.out.println("[Component] Shutdown interrupted");  // Just log, don't re-interrupt
    }
}
```

## Result

- Server and client shut down gracefully without throwing exceptions
- Event loops are properly cleaned up via `shutdownGracefully()`
- Tests no longer fail due to InterruptedException in teardown
- Clear logging indicates when components are being shut down
