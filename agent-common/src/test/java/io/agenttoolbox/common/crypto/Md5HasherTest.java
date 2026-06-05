package io.agenttoolbox.common.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Md5HasherTest {
    @TempDir Path tempDir;

    @Test void hashBytes_returnsCorrectMd5ForKnownInput() {
        String hash = Md5Hasher.hashBytes("hello".getBytes());
        assertThat(hash).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }
    @Test void hashBytes_returnsCorrectMd5ForEmptyInput() {
        String hash = Md5Hasher.hashBytes(new byte[0]);
        assertThat(hash).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
    }
    @Test void hashFile_returnsCorrectMd5() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");
        String hash = Md5Hasher.hashFile(file);
        assertThat(hash).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }
    @Test void hashFile_throwsForMissingFile() {
        Path missing = tempDir.resolve("nonexistent.txt");
        assertThatThrownBy(() -> Md5Hasher.hashFile(missing)).isInstanceOf(IOException.class);
    }
    @Test void sameContentProducesSameHash() throws IOException {
        Path f1 = tempDir.resolve("a.txt"); Path f2 = tempDir.resolve("b.txt");
        Files.writeString(f1, "same"); Files.writeString(f2, "same");
        assertThat(Md5Hasher.hashFile(f1)).isEqualTo(Md5Hasher.hashFile(f2));
    }
    @Test void differentContentProducesDifferentHash() throws IOException {
        Path f1 = tempDir.resolve("a.txt"); Path f2 = tempDir.resolve("b.txt");
        Files.writeString(f1, "A"); Files.writeString(f2, "B");
        assertThat(Md5Hasher.hashFile(f1)).isNotEqualTo(Md5Hasher.hashFile(f2));
    }
}
