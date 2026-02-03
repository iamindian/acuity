package com.acuity.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;

/**
 * Netty handler for decrypting and decompressing incoming tunnel messages
 * Format: [1 byte compression flag][4 bytes encrypted length][encrypted data]
 */
public class SymmetricDecryptionHandler extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Need at least 1 byte for compression flag + 4 bytes for length
        if (in.readableBytes() < 5) {
            return;
        }

        in.markReaderIndex();

        // Read compression flag (1 byte)
        byte compressionFlag = in.readByte();
        boolean isCompressed = (compressionFlag & 0xFF) == 1;

        // Read message length (4 bytes)
        int messageLength = in.readInt();

        if (in.readableBytes() < messageLength) {
            in.resetReaderIndex();
            return; // Wait for more data
        }

        byte[] encryptedData = new byte[messageLength];
        in.readBytes(encryptedData);

        try {
            // Decrypt the data
            byte[] decryptedData = SymmetricEncryption.decrypt(encryptedData);

            // Decompress if needed
            if (isCompressed) {
                byte[] decompressedData = DataCompression.decompress(decryptedData);
                System.out.println("[Decompression] Decrypted data: compressed=" + decryptedData.length +
                    " bytes, decompressed=" + decompressedData.length + " bytes, ratio=" +
                    String.format("%.2f%%", DataCompression.getCompressionRatio(decompressedData.length, decryptedData.length)));
                out.add(Unpooled.copiedBuffer(decompressedData));
            } else {
                out.add(Unpooled.copiedBuffer(decryptedData));
            }
        } catch (Exception e) {
            System.err.println("Decryption/Decompression failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
