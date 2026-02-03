# Quick Reference: Unencrypted Data Handling

## One-Sentence Answer

**User TCP programs send plaintext data to port 8080, and the tunnel server wraps it in encrypted TunnelMessages for secure transport to the tunnel client.**

---

## The Flow (Simplified)

```
USER TCP PROGRAM
    │ (plaintext "PING")
    ↓
TUNNEL SERVER receives on port 8080
    │
    ├─ SymmetricDecryptionHandler (tries to decrypt, fails, data flows through)
    ├─ UserClientHandler (receives raw plaintext)
    ├─ Wraps in TunnelMessage
    ├─ SymmetricEncryptionHandler (encrypts TunnelMessage)
    │
    ↓
TUNNEL CLIENT (encrypted transport)
    │ (encrypted bytes)
    ├─ SymmetricDecryptionHandler (decrypts back to plaintext)
    ├─ TunnelControlHandler (processes TunnelMessage)
    │
    ↓
TARGET SERVICE receives plaintext
```

---

## Why Plaintext from User Program?

| Layer | Encryption | Purpose |
|-------|-----------|---------|
| **User TCP → Server (Port 8080)** | ❌ None | Flexibility for any TCP client |
| **Server ↔ Client (Network)** | ✅ AES-256 | Secure tunnel transport |
| **Client → Target Service** | ❌ None | Direct connection to service |

---

## The Handlers Involved

### On Server Receiving (Inbound)

```
1. SymmetricDecryptionHandler
   ├─ Purpose: Decrypt tunnel protocol messages
   ├─ Input: [length][encrypted-data] from tunnel client
   ├─ Output: Plaintext TunnelMessage
   └─ User TCP data: SKIPPED (not in tunnel format)

2. UserClientHandler
   ├─ Purpose: Handle user TCP plaintext
   ├─ Input: Raw plaintext from user TCP program
   ├─ Processing: Wrap in TunnelMessage
   └─ Output: Send to proxy (encrypted)
```

### On Server Sending (Outbound)

```
SymmetricEncryptionHandler
├─ Purpose: Encrypt tunnel messages before sending
├─ Input: TunnelMessage plaintext
├─ Processing: Encrypt with AES-256
└─ Output: [length][encrypted-data] to tunnel client
```

---

## Code Locations

### Receiving Plaintext: UserClientHandler.java (lines 27-67)
```java
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    ByteBuf byteBuf = (ByteBuf) msg;
    byte[] data = new byte[byteBuf.readableBytes()];
    byteBuf.readBytes(data);  // Gets plaintext from user TCP program
    // ... wraps in TunnelMessage
}
```

### Decryption Attempted: SymmetricDecryptionHandler.java (lines 15-30)
```java
protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    if (in.readableBytes() < 4) return; // Waiting for length prefix
    int messageLength = in.readInt();
    // ... reads encrypted data
    try {
        byte[] decryptedData = SymmetricEncryption.decrypt(encryptedData);
        out.add(Unpooled.copiedBuffer(decryptedData));
    } catch (Exception e) {
        // Decryption failed - user TCP data flows through anyway
        System.err.println("Decryption failed: " + e.getMessage());
    }
}
```

### Encryption for Transport: SymmetricEncryptionHandler.java (lines 11-25)
```java
protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
    byte[] plaintext = new byte[msg.readableBytes()];
    msg.readBytes(plaintext);
    // ... encrypt with AES key
    byte[] encryptedData = SymmetricEncryption.encrypt(plaintext);
    out.writeInt(encryptedData.length);
    out.writeBytes(encryptedData);  // Send encrypted to tunnel client
}
```

---

## Important Points

✓ **User TCP data is plaintext** - No encryption at TCP layer
✓ **Wrapped in TunnelMessage** - For structured transport
✓ **Encrypted for tunnel** - AES-256 between server and client
✓ **Target service gets plaintext** - Tunnel client decrypts and forwards

---

## Example: PING Request

```
USER PROGRAM                  TUNNEL SERVER              TUNNEL CLIENT
    │                              │                          │
    ├─ send "PING" plaintext ─────→│                          │
    │                              │                          │
    │                              ├─ Try decrypt (fails)     │
    │                              ├─ UserClientHandler       │
    │                              ├─ Wrap in TunnelMessage   │
    │                              ├─ Encrypt TunnelMessage ──→│
    │                              │                          │
    │                              │                    Decrypt TunnelMessage
    │                              │                    Extract "PING"
    │                              │                    Forward to target
    │                              │                          │
    │                              │←─── Encrypt response ────│
    │                              │                          │
    │ receive response plaintext ←─┤                          │
    │                              │                          │
```

---

## Data Formats

### User TCP Format (Plaintext)
```
0x50 0x49 0x4E 0x47
P    I    N    G
```

### Tunnel Protocol Format (Encrypted)
```
[4 bytes length][encrypted TunnelMessage bytes]
0x00 0x00 0x00 0x40  [0x8F 0x2C 0xA1 ...]
```

---

## Key Design Decision

The tunnel supports **two data layers**:

```
Layer 1: User TCP (plaintext, any format)
    └─ Simple, flexible for any TCP client

Layer 2: Tunnel Protocol (encrypted, structured)
    └─ Secure, reliable transport between server and client

They are SEPARATE - user data is wrapped IN the tunnel protocol
```

This separation allows:
- User programs to send plaintext
- Tunnel to encrypt for transport
- Target services to receive plaintext
- All without mixing concerns
