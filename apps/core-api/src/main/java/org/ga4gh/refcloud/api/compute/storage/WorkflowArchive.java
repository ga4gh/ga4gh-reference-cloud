package org.ga4gh.refcloud.api.compute.storage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPOutputStream;

// writes a run's workflow directory as a gzipped tar archive (POSIX ustar), which the Nextflow Job extracts with the
// image's own tar (spec 0012, Task 2). written here rather than with a library: it's one format, files and
// directories only. what goes in:
//   * directories, and regular files with their size and whether they're executable (scripts in a workflow's bin/);
//   * not symlinks (one could point outside the workflow), and not .git (Nextflow runs the checked-out files only).
// paths are relative to the directory, with "/" separators; one longer than ustar allows (255 bytes, split at a "/"
// into 155 + 100) fails the archive rather than being cut short
public final class WorkflowArchive {

    static final int BLOCK = 512;

    private WorkflowArchive() {}

    public static void write(Path directory, OutputStream out) throws IOException {
        List<Path> entries = new ArrayList<>();
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                if (dir.equals(directory)) {
                    return FileVisitResult.CONTINUE;
                }
                if (dir.endsWith(".git")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                entries.add(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile()) {
                    entries.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        // sorted, so a directory comes before what's in it and the archive is the same for the same files
        entries.sort(Comparator.comparing(entry -> name(directory, entry)));

        GZIPOutputStream gzip = new GZIPOutputStream(out);
        for (Path entry : entries) {
            boolean isDirectory = Files.isDirectory(entry);
            long size = isDirectory ? 0 : Files.size(entry);
            int mode = isDirectory || Files.isExecutable(entry) ? 0755 : 0644;
            long modified = Files.getLastModifiedTime(entry).toMillis() / 1000;
            gzip.write(header(name(directory, entry) + (isDirectory ? "/" : ""), size, mode, modified, isDirectory));
            if (!isDirectory) {
                try (InputStream in = Files.newInputStream(entry)) {
                    long copied = in.transferTo(gzip);
                    if (copied != size) {
                        throw new IOException("workflow file " + entry + " changed while it was being archived");
                    }
                }
                gzip.write(new byte[padding(size)]);
            }
        }
        // the end of the archive: two empty blocks
        gzip.write(new byte[BLOCK * 2]);
        gzip.finish();
    }

    // a ustar header block: name (split into prefix + name if it's long), mode, owner (root, unnamed), size, time,
    // type (0 file, 5 directory), the magic "ustar\0" "00", and the checksum over the whole block
    static byte[] header(String path, long size, int mode, long modified, boolean isDirectory) throws IOException {
        byte[] header = new byte[BLOCK];
        byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        String name = path;
        String prefix = "";
        if (pathBytes.length > 100) {
            // the prefix is everything before some "/", and both halves must fit
            int split = path.lastIndexOf('/', path.length() - 2);
            while (split > 0
                    && (path.substring(0, split).getBytes(StandardCharsets.UTF_8).length > 155
                            || path.substring(split + 1).getBytes(StandardCharsets.UTF_8).length > 100)) {
                split = path.lastIndexOf('/', split - 1);
            }
            if (split <= 0) {
                throw new IOException("workflow path is too long to archive (" + pathBytes.length + " bytes): " + path);
            }
            prefix = path.substring(0, split);
            name = path.substring(split + 1);
        }
        if (size >= 077777777777L) {
            throw new IOException("workflow file is too large to archive: " + path);
        }
        put(header, 0, 100, name.getBytes(StandardCharsets.UTF_8));
        put(header, 100, 8, octal(mode, 7));
        put(header, 108, 8, octal(0, 7));
        put(header, 116, 8, octal(0, 7));
        put(header, 124, 12, octal(size, 11));
        put(header, 136, 12, octal(Math.max(0, modified), 11));
        header[156] = (byte) (isDirectory ? '5' : '0');
        put(header, 257, 6, "ustar\0".getBytes(StandardCharsets.US_ASCII));
        put(header, 263, 2, "00".getBytes(StandardCharsets.US_ASCII));
        put(header, 345, 155, prefix.getBytes(StandardCharsets.UTF_8));
        // the checksum is computed with its own field as spaces, then written as six octal digits, NUL, space
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }
        long checksum = 0;
        for (byte b : header) {
            checksum += b & 0xff;
        }
        byte[] digits = octal(checksum, 6);
        put(header, 148, 8, new byte[] {digits[0], digits[1], digits[2], digits[3], digits[4], digits[5], 0, ' '});
        return header;
    }

    // a ustar entry's data is padded to a whole number of blocks
    static int padding(long size) {
        return (int) ((BLOCK - size % BLOCK) % BLOCK);
    }

    private static String name(Path directory, Path entry) {
        return directory.relativize(entry).toString().replace(File.separatorChar, '/');
    }

    // digits octal digits with leading zeros, NUL-terminated where the field has room (callers size the field)
    private static byte[] octal(long value, int digits) {
        String text = Long.toOctalString(value);
        String padded = "0".repeat(Math.max(0, digits - text.length())) + text;
        return padded.getBytes(StandardCharsets.US_ASCII);
    }

    private static void put(byte[] header, int offset, int length, byte[] value) throws IOException {
        if (value.length > length) {
            throw new IOException("tar header field overflow");
        }
        System.arraycopy(value, 0, header, offset, value.length);
    }
}
