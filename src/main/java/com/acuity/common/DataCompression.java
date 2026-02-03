package com.acuity.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utility class for compressing and decompressing data using GZIP
 */
public class DataCompression {

    private static final int BUFFER_SIZE = 8192;

    /**
     * Compress data using GZIP
     */
    public static byte[] compress(byte[] data) throws Exception {
        if (data == null || data.length == 0) {
            return new byte[0];
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {

            gzipOut.write(data, 0, data.length);
            gzipOut.finish();
            gzipOut.flush();

            return baos.toByteArray();
        }
    }

    /**
     * Decompress data that was compressed using GZIP
     */
    public static byte[] decompress(byte[] compressedData) throws Exception {
        if (compressedData == null || compressedData.length == 0) {
            return new byte[0];
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = gzipIn.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }

            return baos.toByteArray();
        }
    }

    /**
     * Calculate compression ratio
     */
    public static double getCompressionRatio(int originalSize, int compressedSize) {
        if (originalSize == 0) {
            return 0;
        }
        return 100.0 * (1.0 - (double) compressedSize / originalSize);
    }
}
