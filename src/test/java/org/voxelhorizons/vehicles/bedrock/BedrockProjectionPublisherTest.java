package org.voxelhorizons.vehicles.bedrock;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** Writes are revision-safe single-file atomic replacements, not direct edits. */
public final class BedrockProjectionPublisherTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void replacesSnapshotAsOneCompleteFileWithoutStaleTempArtifacts() throws Exception {
        Path root=temporary.newFolder().toPath();
        Path file=root.resolve("voxelcore-projection/state.json");
        byte[] first="{\"schema\":1,\"value\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] second="{\"schema\":1,\"value\":2}".getBytes(StandardCharsets.UTF_8);
        BedrockProjectionPublisher.publishAtomically(file,first);
        assertArrayEquals(first,Files.readAllBytes(file));
        BedrockProjectionPublisher.publishAtomically(file,second);
        assertArrayEquals(second,Files.readAllBytes(file));
        try(java.util.stream.Stream<Path> entries=Files.list(file.getParent())) {
            assertEquals(1,entries.count());
        }
    }

    @Test public void rejectsSymlinkedProjectionTarget() throws Exception {
        Path root=temporary.newFolder().toPath();
        Path target=root.resolve("other.json");
        Files.write(target,new byte[]{1});
        Path folder=root.resolve("voxelcore-projection");
        Files.createDirectories(folder);
        Path path=folder.resolve("state.json");
        try { Files.createSymbolicLink(path,target); }
        catch(UnsupportedOperationException | java.nio.file.FileSystemException unavailable){return;}
        try {
            BedrockProjectionPublisher.publishAtomically(path,"{}".getBytes(StandardCharsets.UTF_8));
            fail("Symlink must not be overwritten");
        } catch(java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("symlink"));
        }
        assertArrayEquals(new byte[]{1},Files.readAllBytes(target));
    }
}
