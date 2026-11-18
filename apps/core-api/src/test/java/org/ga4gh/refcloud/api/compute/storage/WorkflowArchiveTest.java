package org.ga4gh.refcloud.api.compute.storage;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WorkflowArchiveTest {

    private Path directory;

    private Path workflow;

    @BeforeMethod
    public void setUp() throws IOException {
        directory = Files.createTempDirectory("workflow-archive-test");
        workflow = Files.createDirectories(directory.resolve("workflow"));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws IOException {
        SharedFilesystemRunStorage.deleteRecursively(directory);
    }

    private byte[] archive() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WorkflowArchive.write(workflow, out);
        return out.toByteArray();
    }

    @Test
    public void filesDirectoriesAndExecutableBitsAreKept() throws IOException {
        Files.writeString(workflow.resolve("main.nf"), "workflow {}");
        Path bin = Files.createDirectories(workflow.resolve("bin"));
        Files.writeString(bin.resolve("tool.sh"), "#!/bin/sh\necho hi\n");
        Files.setPosixFilePermissions(bin.resolve("tool.sh"), PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.write(workflow.resolve("empty.txt"), new byte[0]);
        // 513 bytes: one full block plus one byte, so padding matters
        Files.write(workflow.resolve("odd.bin"), new byte[513]);

        Map<String, TarEntries.Entry> entries = TarEntries.read(archive());

        assertEquals(List.copyOf(entries.keySet()), List.of("bin/", "bin/tool.sh", "empty.txt", "main.nf", "odd.bin"));
        assertEquals(entries.get("bin/").type(), '5');
        assertEquals(entries.get("bin/tool.sh").mode(), 0755);
        assertEquals(entries.get("main.nf").mode(), 0644);
        assertEquals(new String(entries.get("main.nf").content(), StandardCharsets.UTF_8), "workflow {}");
        assertEquals(entries.get("empty.txt").content().length, 0);
        assertEquals(entries.get("odd.bin").content().length, 513);
    }

    @Test
    public void symlinksAndGitAreLeftOut() throws IOException {
        Files.writeString(workflow.resolve("main.nf"), "workflow {}");
        Files.writeString(directory.resolve("outside.txt"), "secret");
        Files.createSymbolicLink(workflow.resolve("link.txt"), directory.resolve("outside.txt"));
        Files.createDirectories(workflow.resolve(".git/objects"));
        Files.writeString(workflow.resolve(".git/HEAD"), "ref: refs/heads/main");

        assertEquals(List.copyOf(TarEntries.read(archive()).keySet()), List.of("main.nf"));
    }

    @Test
    public void longPathsUseTheUstarPrefix() throws IOException {
        String deep = "modules/nf-core/" + "a".repeat(60) + "/" + "b".repeat(60);
        Files.createDirectories(workflow.resolve(deep));
        Files.writeString(workflow.resolve(deep + "/main.nf"), "process X {}");

        Map<String, TarEntries.Entry> entries = TarEntries.read(archive());

        assertTrue(entries.containsKey(deep + "/main.nf"), entries.keySet().toString());
    }

    @Test
    public void pathTooLongForUstarFails() throws IOException {
        String deep = "x".repeat(120) + "/" + "y".repeat(120);
        Files.createDirectories(workflow.resolve(deep));
        Files.writeString(workflow.resolve(deep + "/" + "z".repeat(110)), "");

        IOException exception = expectThrows(IOException.class, this::archive);
        assertTrue(exception.getMessage().contains("too long"), exception.getMessage());
    }

    // the Nextflow Job extracts it with the system's tar (GNU tar in the image; bsdtar on macOS reads it too)
    @Test
    public void systemTarReadsIt() throws IOException, InterruptedException {
        Files.writeString(workflow.resolve("main.nf"), "workflow {}");
        Files.createDirectories(workflow.resolve("conf"));
        Files.writeString(workflow.resolve("conf/base.config"), "params {}");
        Path file = directory.resolve("workflow.tar.gz");
        Files.write(file, archive());
        Path extracted = Files.createDirectories(directory.resolve("extracted"));

        Process tar = new ProcessBuilder("tar", "-xzf", file.toString(), "-C", extracted.toString())
                .redirectErrorStream(true)
                .start();
        String printed = new String(tar.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(tar.waitFor(), 0, printed);
        assertEquals(Files.readString(extracted.resolve("main.nf")), "workflow {}");
        assertEquals(Files.readString(extracted.resolve("conf/base.config")), "params {}");
        assertFalse(Files.exists(extracted.resolve(".git")));
    }
}
