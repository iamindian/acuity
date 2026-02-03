package com.acuity.common;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Netty handler for encrypting outgoing tunnel messages
 */
public class SymmetricEncryptionHandler extends MessageToByteEncoder<ByteBuf> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        byte[] plaintext = new byte[msg.readableBytes()];
        msg.readBytes(plaintext);

        try {
            byte[] encryptedData = SymmetricEncryption.encrypt(plaintext);
            // Write length prefix followed by encrypted data
            out.writeInt(encryptedData.length);
            out.writeBytes(encryptedData);
        } catch (Exception e) {
            System.err.println("Encryption failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
