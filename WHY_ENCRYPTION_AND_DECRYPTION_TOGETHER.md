# Why Use Encryption and Decryption Handlers Together

## Short Answer

Both encryption and decryption handlers are used **on the same channel** because:
- **Encryption Handler** encrypts **outgoing** messages (server → client)
- **Decryption Handler** decrypts **incoming** messages (client → server)

They work on different directions of the same bidirectional connection.

## Communication Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Tunnel Server Channel                     │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  INCOMING (from client):                                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Client sends encrypted data                         │   │
│  │           ↓                                           │   │
│  │  SymmetricDecryptionHandler (ByteToMessageDecoder)   │   │
│  │           ↓                                           │   │
│  │  Decrypted plaintext → ServerHandler processes it    │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                               │
│  OUTGOING (to client):                                       │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  ServerHandler sends plaintext response              │   │
│  │           ↓                                           │   │
│  │  SymmetricEncryptionHandler (MessageToByteEncoder)   │   │
│  │           ↓                                           │   │
│  │  Encrypted data → sent to client                     │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

## Netty Pipeline Order

In TunnelServerApp.java, the pipeline is configured like this:

```java
ch.pipeline().addLast(new SymmetricEncryptionHandler());    // For outgoing
ch.pipeline().addLast(new SymmetricDecryptionHandler());    // For incoming
ch.pipeline().addLast(new IdleStateHandler(...));
ch.pipeline().addLast(new TunnelServerHandler(...));        // Business logic
```

### How the Pipeline Works

**Incoming Data Flow (bottom to top):**
```
Raw encrypted bytes from network
        ↓
SymmetricDecryptionHandler (decrypts)
        ↓
ByteToMessageDecoder (frame detection)
        ↓
TunnelServerHandler (receives plaintext)
```

**Outgoing Data Flow (top to bottom):**
```
TunnelServerHandler writes plaintext response
        ↓
SymmetricEncryptionHandler (encrypts)
        ↓
MessageToByteEncoder (adds length prefix)
        ↓
Raw encrypted bytes sent to network
```

## Concrete Example

### Scenario: Client sends a PING message

1. **Client Side (sends PING)**
   ```
   Client creates: TunnelMessage with action="PING"
                ↓
   Client's SymmetricEncryptionHandler encrypts it
                ↓
   Encrypted bytes sent over network
   ```

2. **Server Side (receives PING)**
   ```
   Encrypted bytes arrive from network
                ↓
   Server's SymmetricDecryptionHandler decrypts it
                ↓
   Server's TunnelServerHandler receives plaintext
   TunnelServerHandler processes: "PING" → sends "PONG"
                ↓
   Server's SymmetricEncryptionHandler encrypts "PONG"
                ↓
   Encrypted bytes sent back to client
   ```

3. **Client Side (receives PONG)**
   ```
   Encrypted bytes arrive from network
                ↓
   Client's SymmetricDecryptionHandler decrypts it
                ↓
   Client's TunnelControlHandler receives plaintext "PONG"
   ```

## Why Not Just One Handler?

You might think "why not just encrypt on sending and decrypt on receiving?"

**Answer**: That's exactly what happens! But in a Netty pipeline:

- **Decryption** is for **inbound** messages (data received from the network)
- **Encryption** is for **outbound** messages (data being sent to the network)

Both are needed because a network connection is **bidirectional**:
- Data flows **IN** from the client → needs decryption
- Data flows **OUT** to the client → needs encryption

## Handler Types

### SymmetricEncryptionHandler
- **Type**: `MessageToByteEncoder<ByteBuf>`
- **Direction**: Outbound (messages you want to send)
- **Purpose**: Converts plaintext ByteBuf to encrypted bytes
- **When called**: When you write/flush data to the channel

### SymmetricDecryptionHandler
- **Type**: `ByteToMessageDecoder`
- **Direction**: Inbound (messages received from network)
- **Purpose**: Converts encrypted bytes to plaintext ByteBuf
- **When called**: When data arrives from the network

## Summary Table

| Handler | Type | Direction | Purpose | Input | Output |
|---------|------|-----------|---------|-------|--------|
| **SymmetricEncryptionHandler** | MessageToByteEncoder | Outbound | Encrypt messages before sending | Plaintext ByteBuf | Encrypted bytes + length |
| **SymmetricDecryptionHandler** | ByteToMessageDecoder | Inbound | Decrypt messages after receiving | Encrypted bytes + length | Plaintext ByteBuf |

## Analogy

Think of a secure postal system:

- **Encryption Handler** = Sealing envelopes before mailing (outgoing)
- **Decryption Handler** = Opening envelopes when received (incoming)

Both are necessary for **secure bidirectional communication**. You seal what you send, and you open what you receive. At the same post office (channel), you're doing both operations for different mail directions.

## Code Location

**TunnelServerApp.java (lines 111-112)**:
```java
ch.pipeline().addLast(new SymmetricEncryptionHandler());
ch.pipeline().addLast(new SymmetricDecryptionHandler());
```

**TunnelClientApp.java** (same approach):
```java
ch.pipeline().addLast(new SymmetricEncryptionHandler());
ch.pipeline().addLast(new SymmetricDecryptionHandler());
```

Both server and client have the same setup because both need to:
1. **Send** encrypted data (encryption handler)
2. **Receive** encrypted data (decryption handler)

This ensures end-to-end encryption for all communication in both directions.
