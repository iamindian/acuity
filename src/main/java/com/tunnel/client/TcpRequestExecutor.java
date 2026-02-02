package com.tunnel.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class TcpRequestExecutor {

    private TcpRequestExecutor() {
    }

    public static byte[] execute(byte[] requestBytes, String targetHost, int targetPort) {
        if (requestBytes == null || requestBytes.length == 0) {
            return new byte[0];
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetHost, targetPort), 10000);
            socket.setSoTimeout(20000);

            try (OutputStream out = socket.getOutputStream()) {
                out.write(requestBytes);
                out.flush();
            }

            try (InputStream in = socket.getInputStream()) {
                return readAllBytes(in);
            }
        } catch (IOException ex) {
            return new byte[0];
        }
    }


    private static byte[] readAllBytes(InputStream input) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
