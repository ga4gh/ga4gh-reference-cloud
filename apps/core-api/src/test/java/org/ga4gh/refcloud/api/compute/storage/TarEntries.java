package org.ga4gh.refcloud.api.compute.storage;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

// reads a gzipped ustar archive back (tests only): each entry's full name, type, mode and content, and checks every
// header's checksum and the archive's end
public final class TarEntries {

    public record Entry(String name, char type, int mode, byte[] content) {}

    private TarEntries() {}

    public static Map<String, Entry> read(byte[] gzipped) throws IOException {
        Map<String, Entry> entries = new LinkedHashMap<>();
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(gzipped)))) {
            while (true) {
                byte[] header = new byte[512];
                in.readFully(header);
                if (isZero(header)) {
                    byte[] second = new byte[512];
                    in.readFully(second);
                    if (!isZero(second) || in.read() != -1) {
                        throw new IOException("the archive doesn't end with two empty blocks");
                    }
                    return entries;
                }
                checkChecksum(header);
                if (!text(header, 257, 6).equals("ustar")) {
                    throw new IOException("not a ustar header");
                }
                String prefix = text(header, 345, 155);
                String name = (prefix.isEmpty() ? "" : prefix + "/") + text(header, 0, 100);
                int mode = Integer.parseInt(text(header, 100, 8), 8);
                long size = Long.parseLong(text(header, 124, 12), 8);
                byte[] content = new byte[(int) size];
                in.readFully(content);
                in.skipNBytes(WorkflowArchive.padding(size));
                entries.put(name, new Entry(name, (char) header[156], mode, content));
            }
        }
    }

    public static Map<String, Entry> read(InputStream gzipped) throws IOException {
        return read(gzipped.readAllBytes());
    }

    private static void checkChecksum(byte[] header) throws IOException {
        long expected = Long.parseLong(text(header, 148, 8).strip(), 8);
        long sum = 0;
        for (int i = 0; i < header.length; i++) {
            sum += i >= 148 && i < 156 ? ' ' : header[i] & 0xff;
        }
        if (sum != expected) {
            throw new IOException("header checksum " + expected + " doesn't match " + sum);
        }
    }

    private static String text(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static boolean isZero(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
}
