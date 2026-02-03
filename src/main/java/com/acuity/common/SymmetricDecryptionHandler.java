package com.acuity.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;

/**
 * Netty handler for decrypting incoming tunnel messages
 */
public class SymmetricDecryptionHandler extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 4) {
            return; // Not enough data for length
        }

        in.markReaderIndex();
        int messageLength = in.readInt();

        if (in.readableBytes() < messageLength) {
            in.resetReaderIndex();
            return; // Wait for more data
        }

        byte[] encryptedData = new byte[messageLength];
        in.readBytes(encryptedData);

        try {
            byte[] decryptedData = SymmetricEncryption.decrypt(encryptedData);
            out.add(Unpooled.copiedBuffer(decryptedData));
        } catch (Exception e) {
            System.err.println("Decryption failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
