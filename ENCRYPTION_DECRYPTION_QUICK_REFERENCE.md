# Quick Reference: Encryption vs Decryption

## One-Sentence Answer

**Encryption** encrypts messages you're **sending OUT**, while **Decryption** decrypts messages you're **receiving IN** — both are needed for secure two-way communication.

---

## In the Code

**File**: `TunnelServerApp.java` (lines 111-112)

```java
ch.pipeline().addLast(new SymmetricEncryptionHandler());    // For SENDING data
ch.pipeline().addLast(new SymmetricDecryptionHandler());    // For RECEIVING data
```

Same with **TunnelClientApp.java** and all handlers.

---

## Quick Comparison

### SymmetricEncryptionHandler

```
What it does:    Encrypts plaintext before sending
When it runs:    When you write/flush data to the channel (outbound)
Example:         Your app says "Send PONG" 
                 → Handler encrypts it
                 → Encrypted bytes go to the network
Type:            MessageToByteEncoder<ByteBuf>
```

### SymmetricDecryptionHandler

```
What it does:    Decrypts encrypted data after receiving
When it runs:    When data arrives from the network (inbound)
Example:         Server receives encrypted bytes
                 → Handler decrypts it
                 → Your app gets plaintext "PING"
Type:            ByteToMessageDecoder
```

---

## Message Lifecycle

```
CLIENT SENDS MESSAGE:
  plaintext "PING" 
    ↓
  Client's SymmetricEncryptionHandler 
    ↓
  encrypted bytes on network
    ↓
  Server receives encrypted bytes
    ↓
  Server's SymmetricDecryptionHandler
    ↓
  plaintext "PING" to business logic


SERVER SENDS RESPONSE:
  plaintext "PONG"
    ↓
  Server's SymmetricEncryptionHandler
    ↓
  encrypted bytes on network
    ↓
  Client receives encrypted bytes
    ↓
  Client's SymmetricDecryptionHandler
    ↓
  plaintext "PONG" to business logic
```

---

## Why Not Just One?

| Scenario | Problem |
|----------|---------|
| Only Encryption | You can send securely, but can't receive anything encrypted |
| Only Decryption | You can receive, but can't send securely |
| **Both** ✅ | Full bidirectional secure communication |

---

## Key Insight

A network connection is **bidirectional**:
- Data flows IN (needs decryption)
- Data flows OUT (needs encryption)

On the **same channel**, you need **both handlers** to handle both directions.

It's like having a mailbox:
- **Encryption handler** = sealing outgoing letters
- **Decryption handler** = opening incoming letters
- **Both on same mailbox** = secure in both directions

---

## Netty Pipeline Concept

In Netty, handlers are arranged in a pipeline:

```
Inbound (bottom-up):    encrypted bytes → decrypt → plaintext → business logic
Outbound (top-down):    business logic → plaintext → encrypt → encrypted bytes
```

Both directions use the **same pipeline**, so both handlers are on the same channel.

---

## Files Involved

**Encryption/Decryption Classes:**
- `SymmetricEncryptionHandler.java` - Encrypts outbound messages
- `SymmetricDecryptionHandler.java` - Decrypts inbound messages
- `SymmetricEncryption.java` - Core encryption/decryption logic

**Where Used:**
- `TunnelServerApp.java` - Server's channel pipeline
- `TunnelClientApp.java` - Client's channel pipeline
- All handlers inherit this setup

---

## Visual Summary

```
              CHANNEL (Network Connection)
              
     OUTBOUND          │          INBOUND
   (To Network)        │       (From Network)
                       │
Plaintext bytebuf      │      Encrypted bytes
         ↓             │             ↑
   ┌──────────┐        │        ┌──────────┐
   │Encryption│        │        │Decryption│
   │ Handler  │        │        │ Handler  │
   └────┬─────┘        │        └────┬─────┘
        ↓              │             ↓
   Encrypted bytes ────→│←── Plaintext bytebuf
                        │
                   Business Logic
```

---

## Remember

✅ Both handlers are on the **same channel**
✅ Encryption for **outgoing** messages
✅ Decryption for **incoming** messages
✅ Both are **required** for secure bidirectional communication
✅ This applies to both **server** and **client**

Without encryption, data sent in plaintext (insecure).
Without decryption, you can't understand received encrypted data.
**Both together = Secure communication! 🔒**
