package com.acuity.common;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Netty handler for compressing and encrypting outgoing tunnel messages
 * Format: [1 byte compression flag][4 bytes encrypted length][encrypted data]
 */
public class SymmetricEncryptionHandler extends MessageToByteEncoder<ByteBuf> {

    // Compression threshold: compress if data > 1KB
    private static final int COMPRESSION_THRESHOLD = 1024;

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        byte[] plaintext = new byte[msg.readableBytes()];
        msg.readBytes(plaintext);

        try {
            // Determine if compression should be applied
            boolean shouldCompress = plaintext.length > COMPRESSION_THRESHOLD;
            byte[] dataToEncrypt = plaintext;

            if (shouldCompress) {
                byte[] compressedData = DataCompression.compress(plaintext);
                // Only use compression if it actually reduces size
                if (compressedData.length < plaintext.length) {
                    dataToEncrypt = compressedData;
                } else {
                    shouldCompress = false;
                }
            }

            // Encrypt the (possibly compressed) data
            byte[] encryptedData = SymmetricEncryption.encrypt(dataToEncrypt);

            // Write compression flag (1 byte)
            out.writeByte(shouldCompress ? 1 : 0);

            // Write length prefix (4 bytes) followed by encrypted data
            out.writeInt(encryptedData.length);
            out.writeBytes(encryptedData);

            if (shouldCompress) {
                double ratio = DataCompression.getCompressionRatio(plaintext.length, dataToEncrypt.length);
                System.out.println("[Compression] Encrypted data: original=" + plaintext.length +
                    " bytes, compressed=" + dataToEncrypt.length + " bytes, ratio=" +
                    String.format("%.2f%%", ratio));
            }
        } catch (Exception e) {
            System.err.println("Encryption/Compression failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}


