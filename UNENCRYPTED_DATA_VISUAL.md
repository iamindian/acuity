# Visual: Unencrypted Data Flow Through Netty

## Complete Message Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                   USER TCP PROGRAM                              │
│                                                                  │
│        Sends raw "PING" message (plaintext)                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ TCP Connection
                              │ (Port 8080)
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│              TUNNEL SERVER NETTY CHANNEL                        │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  PIPELINE (ordered handlers)                             │  │
│  │                                                          │  │
│  │  1. SymmetricEncryptionHandler                           │  │
│  │     (MessageToByteEncoder - OUTBOUND)                   │  │
│  │     Encrypts: TunnelMessage ──→ encrypted bytes         │  │
│  │     ✓ Used for sending encrypted TunnelMessages         │  │
│  │                                                          │  │
│  │  2. SymmetricDecryptionHandler                           │  │
│  │     (ByteToMessageDecoder - INBOUND)                    │  │
│  │     Decrypts: encrypted bytes ──→ plaintext             │  │
│  │     ✗ NOT used for user TCP data (it's already plain!)  │  │
│  │                                                          │  │
│  │  3. IdleStateHandler                                     │  │
│  │     Monitors connection idle (no message processing)    │  │
│  │                                                          │  │
│  │  4. UserClientHandler (INBOUND)                          │  │
│  │     (ChannelInboundHandlerAdapter)                       │  │
│  │     Receives raw plaintext from user TCP program        │  │
│  │                                                          │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘


          INBOUND DATA FLOW (receiving from network)
          ═════════════════════════════════════════

        Raw plaintext bytes "PING" (from user TCP)
                    │
                    ↓
        SymmetricDecryptionHandler
        ├─ Reads 4 bytes expecting length prefix
        ├─ Gets 0x50494E47 ("PING" as integer = 1346458951)
        ├─ Waits for 1346458951 bytes of encrypted data
        ├─ Data never arrives → stays in buffer
        └─ Returns early (not added to output list)
                    │
                    ↓
        IdleStateHandler
        └─ Does nothing, passes through
                    │
                    ↓
        UserClientHandler.channelRead()
        ├─ Receives raw ByteBuf with "PING"
        ├─ Extracts bytes: byte[] data = "PING"
        └─ Processes as user data


          OUTBOUND DATA FLOW (sending to proxy)
          ════════════════════════════════════

        UserClientHandler wraps in TunnelMessage
        TunnelMessage{
          browserChannelId: "abc123",
          action: "FORWARD",
          data: [0x50, 0x49, 0x4E, 0x47]  // "PING"
        }
                    │
                    ↓
        Serialize to bytes
        byte[] message = tunnelMessage.toBytes()
                    │
                    ↓
        SymmetricEncryptionHandler.encode()
        ├─ Encrypts with AES key
        ├─ Adds 4-byte length prefix
        └─ Produces: [length][encrypted bytes]
                    │
                    ↓
        Send to Proxy Channel
        ├─ Proxy receives encrypted bytes
        ├─ Proxy's SymmetricDecryptionHandler decrypts
        ├─ Gets plaintext TunnelMessage
        └─ Proxy processes and forwards to target
```

---

## Handler Responsibilities

### SymmetricDecryptionHandler (Inbound)

```
Purpose:    Decrypt incoming TunnelMessage protocol messages
Type:       ByteToMessageDecoder (Inbound)
Direction:  From network → To business logic

Receives:   [4-byte-length][encrypted-bytes]
Produces:   Plaintext TunnelMessage

Flow:
  Encrypted bytes from tunnel client
         ↓
  Read length prefix (4 bytes)
         ↓
  Read encrypted message (length bytes)
         ↓
  Decrypt with AES key
         ↓
  Add plaintext to output list
         ↓
  Next handler (TunnelServerHandler) receives plaintext


⚠️ ISSUE WITH USER TCP DATA:
   User TCP data is already plaintext (no length prefix format)
   So:
   ├─ No 4-byte length prefix
   ├─ Decryption attempt fails
   ├─ Handler returns (nothing added to output)
   └─ Raw data flows to next handler anyway
```

### UserClientHandler (Inbound)

```
Purpose:    Process raw plaintext from user TCP programs
Type:       ChannelInboundHandlerAdapter (Inbound)
Direction:  From network → To business logic

Receives:   Raw plaintext bytes from user TCP program
Produces:   TunnelMessage (wrapped in plaintext)

Flow:
  Raw plaintext from user TCP program
         ↓
  Extract bytes from ByteBuf
         ↓
  Create TunnelMessage with FORWARD action
         ↓
  Serialize TunnelMessage to bytes
         ↓
  Send to proxy channel (via SymmetricEncryptionHandler)


KEY POINT:
   UserClientHandler treats input as plaintext
   It does NOT expect encrypted data
   It does NOT validate encryption
```

### SymmetricEncryptionHandler (Outbound)

```
Purpose:    Encrypt outgoing TunnelMessage for tunnel transport
Type:       MessageToByteEncoder (Outbound)
Direction:  From business logic → To network

Receives:   TunnelMessage bytes (plaintext)
Produces:   [4-byte-length][encrypted-bytes]

Flow:
  TunnelMessage plaintext bytes
         ↓
  Encrypt with AES key
         ↓
  Add 4-byte length prefix
         ↓
  Send encrypted bytes to next channel
         ↓
  Tunnel client receives encrypted bytes
```

---

## The Complete Picture

```
┌─────────────────┐
│  USER PROGRAM   │
│                 │
│ Sends plaintext │
│    "PING"       │
└────────┬────────┘
         │
         │ TCP plaintext
         ↓
┌─────────────────────────────────────┐
│    TUNNEL SERVER PORT 8080          │
│                                     │
│  ┌─────────────────────────────┐   │
│  │  Netty Channel Pipeline     │   │
│  │                             │   │
│  │  IN: "PING" plaintext       │   │
│  │       ↓                     │   │
│  │  DecryptHandler (skip)      │   │
│  │       ↓                     │   │
│  │  UserClientHandler          │   │
│  │  ├─ Receive: "PING"         │   │
│  │  ├─ Wrap in TunnelMessage   │   │
│  │  └─ Send encrypted          │   │
│  │       ↓                     │   │
│  │  EncryptHandler             │   │
│  │  ├─ Encrypt TunnelMessage   │   │
│  │  └─ Add length prefix       │   │
│  │       ↓                     │   │
│  │  OUT: [encrypted bytes]     │   │
│  │                             │   │
│  └─────────────────────────────┘   │
│                                     │
└────────┬────────────────────────────┘
         │
         │ Encrypted tunnel protocol
         ↓
┌─────────────────────────────────────┐
│    TUNNEL CLIENT (via network)      │
│                                     │
│  ┌─────────────────────────────┐   │
│  │  Netty Channel Pipeline     │   │
│  │                             │   │
│  │  IN: [encrypted bytes]      │   │
│  │       ↓                     │   │
│  │  DecryptHandler             │   │
│  │  ├─ Read length prefix      │   │
│  │  ├─ Decrypt bytes           │   │
│  │  └─ Get plaintext           │   │
│  │       ↓                     │   │
│  │  TunnelControlHandler       │   │
│  │  ├─ Get TunnelMessage       │   │
│  │  ├─ Extract: "PING"         │   │
│  │  └─ Send to target service  │   │
│  │       ↓                     │   │
│  │  EncryptHandler             │   │
│  │  ├─ Encrypt response        │   │
│  │  └─ Add length prefix       │   │
│  │       ↓                     │   │
│  │  OUT: [encrypted bytes]     │   │
│  │                             │   │
│  └─────────────────────────────┘   │
│                                     │
└────────┬────────────────────────────┘
         │
         │ Encrypted tunnel protocol
         ↓
┌─────────────────────────────────────┐
│      TUNNEL SERVER (receives)        │
│                                      │
│  Decrypts, forwards to user program  │
│                                      │
└─────────────────────────────────────┘
         │
         │ Response plaintext
         ↓
┌─────────────────┐
│  USER PROGRAM   │
│                 │
│ Receives result │
└─────────────────┘
```

---

## Data Format Comparison

### What User TCP Program Sends (Plaintext)

```
Raw bytes: 0x50 0x49 0x4E 0x47
ASCII:     P    I    N    G
Structure: [raw data] (no framing)
```

### What Tunnel Protocol Sends (Encrypted)

```
Raw bytes: [4-byte-length] [encrypted-data...]

Example:
  Length: 0x00 0x00 0x00 0x40 (64 bytes)
  Data:   0x8F 0x2C 0xA1 0x3D ... (encrypted TunnelMessage)

Structure: [length-prefix][encrypted-frame]
```

---

## Why This Design?

```
The tunnel has TWO different data formats:

1. USER TCP LAYER (Port 8080)
   ├─ Format: Raw plaintext (any format)
   ├─ Encryption: None
   ├─ Handler: UserClientHandler (no decryption needed)
   └─ Purpose: Accept data from user programs

2. TUNNEL PROTOCOL LAYER (Network)
   ├─ Format: TunnelMessage with length prefix
   ├─ Encryption: AES-256
   ├─ Handler: SymmetricEncryptionHandler + SymmetricDecryptionHandler
   └─ Purpose: Secure transport to tunnel client
```

The two layers are separate!
- User data → wrapped in TunnelMessage → encrypted for transport
- Transport → decrypted → extracted from TunnelMessage → forwarded to target
