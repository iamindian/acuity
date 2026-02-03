# Visual Guide: Encryption & Decryption in Acuity

## The Complete Picture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                                                                 │
│                          TUNNEL SERVER                                          │
│                                                                                 │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │                    Netty Channel Pipeline                                │  │
│  │                                                                          │  │
│  │  ┌────────────────────────────────────────────────────────────────────┐ │  │
│  │  │ INBOUND HANDLERS (receiving from client)                           │ │  │
│  │  │ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │ │  │
│  │  │                                                                    │ │  │
│  │  │  Encrypted Bytes ┐                                                │ │  │
│  │  │       ↑          │                                                │ │  │
│  │  │       │          │ SymmetricDecryptionHandler                     │ │  │
│  │  │  (from network)  │ (ByteToMessageDecoder)                         │ │  │
│  │  │       │          │                                                │ │  │
│  │  │       ↓          ┘                                                │ │  │
│  │  │  Plaintext ByteBuf ──→ TunnelServerHandler                        │ │  │
│  │  │                      (Processes business logic)                   │ │  │
│  │  │                                                                    │ │  │
│  │  └────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                          │  │
│  │  ┌────────────────────────────────────────────────────────────────────┐ │  │
│  │  │ OUTBOUND HANDLERS (sending to client)                              │ │  │
│  │  │ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │ │  │
│  │  │                                                                    │ │  │
│  │  │  TunnelServerHandler writes Plaintext ByteBuf                      │ │  │
│  │  │       ↑                                                            │ │  │
│  │  │       │ ┌─────────────────────────────────────────────────┐       │ │  │
│  │  │       │ │ SymmetricEncryptionHandler                      │       │ │  │
│  │  │       │ │ (MessageToByteEncoder)                          │       │ │  │
│  │  │       └─┤ - Encrypts plaintext                            │       │ │  │
│  │  │         │ - Adds length prefix                            │       │ │  │
│  │  │         │ - Produces encrypted bytes                      │       │ │  │
│  │  │         └─────────────────────────────────────────────────┘       │ │  │
│  │  │       ↓                                                            │ │  │
│  │  │  Encrypted Bytes                                                   │ │  │
│  │  │  (to network)                                                      │ │  │
│  │  │                                                                    │ │  │
│  │  └────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                          │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Message Journey: Client → Server → Client

### Step 1: Client Sends Encrypted PING

```
┌─────────────────────────────────────┐
│       TUNNEL CLIENT                 │
│                                     │
│  create "PING" message (plaintext)  │
│            ↓                        │
│   SymmetricEncryptionHandler        │
│            ↓                        │
│   [encrypted bytes sent over TCP]   │
└─────────────────────────────────────┘
             │
             │ Network (TCP)
             ↓
┌─────────────────────────────────────┐
│      TUNNEL SERVER RECEIVES          │
│                                     │
│  [encrypted bytes arrive]           │
│            ↓                        │
│  SymmetricDecryptionHandler         │
│  (reads length prefix, decrypts)    │
│            ↓                        │
│  plaintext "PING" message           │
│            ↓                        │
│  TunnelServerHandler                │
│  (processes PING → creates PONG)    │
└─────────────────────────────────────┘
```

### Step 2: Server Sends Encrypted PONG

```
┌─────────────────────────────────────┐
│      TUNNEL SERVER RESPONDS          │
│                                     │
│  TunnelServerHandler writes PONG    │
│            ↓                        │
│  SymmetricEncryptionHandler         │
│            ↓                        │
│  [encrypted bytes sent over TCP]    │
└─────────────────────────────────────┘
             │
             │ Network (TCP)
             ↓
┌─────────────────────────────────────┐
│       TUNNEL CLIENT RECEIVES         │
│                                     │
│  [encrypted bytes arrive]           │
│            ↓                        │
│  SymmetricDecryptionHandler         │
│  (reads length prefix, decrypts)    │
│            ↓                        │
│  plaintext "PONG" message           │
│            ↓                        │
│  TunnelControlHandler               │
│  (receives response)                │
└─────────────────────────────────────┘
```

## Why Both at the Same Time?

```
BIDIRECTIONAL COMMUNICATION = ENCRYPTION + DECRYPTION

One connection carries BOTH:
├─ Incoming traffic (needs DECRYPTION)
└─ Outgoing traffic (needs ENCRYPTION)

Like a two-way mirror:
├─ Light coming IN → needs to be reflected (decryption)
└─ Light going OUT → needs to be generated (encryption)
```

## Code Example

```java
// In TunnelServerApp.start() method:

ch.pipeline().addLast(new SymmetricEncryptionHandler());    // ← Outbound
ch.pipeline().addLast(new SymmetricDecryptionHandler());    // ← Inbound
ch.pipeline().addLast(new IdleStateHandler(...));
ch.pipeline().addLast(new TunnelServerHandler(...));

// Same channel handles BOTH directions:
// - Messages TO client → encrypted by SymmetricEncryptionHandler
// - Messages FROM client → decrypted by SymmetricDecryptionHandler
```

## Data Flow Comparison

### ❌ WRONG: Only Encryption (one-way encryption)
```
Client → [encrypt] → Server (can send to server only)
Server → [plaintext] → Client (client can't understand!)
```

### ❌ WRONG: Only Decryption (one-way decryption)
```
Client → [plaintext] → Server (no security!)
Server → [decrypt] → Client (server can't send encrypted)
```

### ✅ CORRECT: Both Encryption AND Decryption (bidirectional)
```
Client → [encrypt] → Server [decrypt] ✓ (secure receive)
         [decrypt] ← Server [encrypt] ✓ (secure send)
```

## Real-World Analogy

Imagine a secure telephone line:

```
YOU (Server)                         THEM (Client)
─────────────                        ─────────────

Ears (Decryption)                    Mouth (Encryption)
Listen to encrypted call    ←━━━━━  Speak encrypted message
Decode what you hear                 Encode before speaking

Mouth (Encryption)                   Ears (Decryption)
Speak encrypted reply       ━━━━━→  Listen to encrypted call
Encode before speaking               Decode what they hear
```

Both people need:
- **Ears (Decryption)** to understand incoming messages
- **Mouth (Encryption)** to send secure messages

## Summary

| Aspect | Details |
|--------|---------|
| **Why Both?** | Bidirectional communication requires both sending and receiving |
| **When Used?** | Encryption: when writing to channel; Decryption: when reading from channel |
| **Direction** | Encryption = Outbound; Decryption = Inbound |
| **Same Channel?** | Yes - both handlers on the same Netty pipeline |
| **Both Necessary?** | Yes - without either, communication would be insecure or impossible |

## Visual Summary

```
                    NETTY CHANNEL
     ┌──────────────────────────────────┐
     │  SymmetricEncryptionHandler      │  (Outbound)
     │          ▲                       │
     │          │ (plaintext from app)  │
     │          │                       │
     │   Business Logic (TunnelServerHandler)
     │          │                       │
     │          ↓ (plaintext to send)   │
     │  SymmetricDecryptionHandler      │  (Inbound)
     │          ▲                       │
     │          │ (encrypted from net)  │
     └──────────────────────────────────┘
```

Everything is encrypted on the wire, but the application works with plaintext internally.
