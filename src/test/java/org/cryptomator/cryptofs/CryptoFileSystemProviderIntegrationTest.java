/*******************************************************************************
 * Copyright (c) 2016 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.cryptofs;

import com.google.common.io.MoreFiles;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.cryptomator.cryptofs.ch.CleartextFileChannel;
import org.cryptomator.cryptolib.api.InvalidPassphraseException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

import static java.nio.file.Files.readAllBytes;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.cryptomator.cryptofs.CryptoFileSystemProperties.cryptoFileSystemProperties;
import static org.hamcrest.Matchers.is;

public class CryptoFileSystemProviderIntegrationTest {

	private FileSystem tmpFs;
	private Path pathToVault;
	private Path masterkeyFile;

	@BeforeEach
	public void setup() throws IOException {
		tmpFs = Jimfs.newFileSystem(Configuration.unix());
		pathToVault = tmpFs.getPath("/vaultDir");
		masterkeyFile = pathToVault.resolve("masterkey.cryptomator");
		Files.createDirectory(pathToVault);
	}

	@AfterEach
	public void teardown() throws IOException {
		tmpFs.close();
	}

	@Test
	public void testGetFsViaNioApi() throws IOException {
		URI fsUri = CryptoFileSystemUri.create(pathToVault);
		FileSystem fs = FileSystems.newFileSystem(fsUri, cryptoFileSystemProperties().withPassphrase("asd").build());
		Assertions.assertTrue(fs instanceof CryptoFileSystemImpl);
		Assertions.assertTrue(Files.exists(masterkeyFile));
		FileSystem fs2 = FileSystems.getFileSystem(fsUri);
		Assertions.assertSame(fs, fs2);
	}

	@Test
	public void testInitAndOpenFsWithPepper() throws IOException {
		Path dataDir = pathToVault.resolve("d");
		byte[] pepper = "pepper".getBytes(StandardCharsets.US_ASCII);

		// Initialize vault:
		CryptoFileSystemProvider.initialize(pathToVault, "masterkey.cryptomator", pepper, "asd");
		Assertions.assertTrue(Files.isDirectory(dataDir));
		Assertions.assertTrue(Files.isRegularFile(masterkeyFile));

		// Attempt 1: Correct pepper:
		CryptoFileSystemProperties properties = cryptoFileSystemProperties() //
				.withFlags() //
				.withMasterkeyFilename("masterkey.cryptomator") //
				.withPassphrase("asd") //
				.withPepper(pepper) //
				.build();
		try (CryptoFileSystem cryptoFs = CryptoFileSystemProvider.newFileSystem(pathToVault, properties)) {
			Assertions.assertNotNull(cryptoFs);
		}

		// Attempt 2: Invalid pepper resulting in InvalidPassphraseException:
		CryptoFileSystemProperties wrongProperties = cryptoFileSystemProperties() //
				.withFlags() //
				.withMasterkeyFilename("masterkey.cryptomator") //
				.withPassphrase("asd") //
				.build();
		Assertions.assertThrows(InvalidPassphraseException.class, () -> {
			CryptoFileSystemProvider.newFileSystem(pathToVault, wrongProperties);
		});
	}

	@Test
	public void testChangePassphraseWithUnsupportedVersion() throws IOException {
		Files.write(masterkeyFile, "{\"version\": 0}".getBytes(StandardCharsets.US_ASCII));
		Assertions.assertThrows(FileSystemNeedsMigrationException.class, () -> {
			CryptoFileSystemProvider.changePassphrase(pathToVault, "masterkey.cryptomator", "foo", "bar");
		});
	}

	@Test
	public void testChangePassphrase() throws IOException {
		CryptoFileSystemProvider.initialize(pathToVault, "masterkey.cryptomator", "foo");
		CryptoFileSystemProvider.changePassphrase(pathToVault, "masterkey.cryptomator", "foo", "bar");
		try (FileSystem fs = CryptoFileSystemProvider.newFileSystem(pathToVault, CryptoFileSystemProperties.withPassphrase("bar").build())) {
			Assertions.assertNotNull(fs);
		}
	}

	@Test
	public void testOpenAndCloseFileChannel() throws IOException {
		FileSystem fs = CryptoFileSystemProvider.newFileSystem(pathToVault, cryptoFileSystemProperties().withPassphrase("asd").build());
		try (FileChannel ch = FileChannel.open(fs.getPath("/foo"), EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW))) {
			Assertions.assertTrue(ch instanceof CleartextFileChannel);
		}
	}

	@Test
	public void testReadAndWriteToFileChannelOnSymlink() throws IOException {
		FileSystem fs = CryptoFileSystemProvider.newFileSystem(pathToVault, cryptoFileSystemProperties().withPassphrase("asd").build());
		Path link = fs.getPath("/link");
		Path target = fs.getPath("/target");
		Files.createSymbolicLink(link, target);
		try (WritableByteChannel ch = Files.newByteChannel(link, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)) {
			ch.write(StandardCharsets.US_ASCII.encode("hello world"));
		}
		try (ReadableByteChannel ch = Files.newByteChannel(target, StandardOpenOption.READ)) {
			ByteBuffer buf = ByteBuffer.allocate(100);
			ch.read(buf);
			buf.flip();
			String str = StandardCharsets.US_ASCII.decode(buf).toString();
			Assertions.assertEquals("hello world", str);
		}
	}

	@Test
	public void testLongFileNames() throws IOException {
		FileSystem fs = CryptoFileSystemProvider.newFileSystem(pathToVault, cryptoFileSystemProperties().withPassphrase("asd").build());
		Path longNamePath = fs.getPath("/Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen");
		Files.createDirectory(longNamePath);
		Assertions.assertTrue(Files.isDirectory(longNamePath));
		MatcherAssert.assertThat(MoreFiles.listFiles(fs.getPath("/")), Matchers.contains(longNamePath));
	}

	@Test
	public void testCopyFileFromOneCryptoFileSystemToAnother() throws IOException {
		byte[] data = new byte[] {1, 2, 3, 4, 5, 6, 7};

		Path fs1Location = pathToVault.resolve("foo");
		Path fs2Location = pathToVault.resolve("bar");
		Files.createDirectories(fs1Location);
		Files.createDirectories(fs2Location);
		FileSystem fs1 = CryptoFileSystemProvider.newFileSystem(fs1Location, cryptoFileSystemProperties().withPassphrase("asd").build());
		FileSystem fs2 = CryptoFileSystemProvider.newFileSystem(fs2Location, cryptoFileSystemProperties().withPassphrase("qwe").build());
		Path file1 = fs1.getPath("/foo/bar");
		Path file2 = fs2.getPath("/bar/baz");
		Files.createDirectories(file1.getParent());
		Files.createDirectories(file2.getParent());
		Files.write(file1, data);

		Files.copy(file1, file2);

		MatcherAssert.assertThat(readAllBytes(file1), is(data));
		MatcherAssert.assertThat(readAllBytes(file2), is(data));
	}

	@Test
	public void testCopyFileByRelacingExistingFromOneCryptoFileSystemToAnother() throws IOException {
		byte[] data = new byte[] {1, 2, 3, 4, 5, 6, 7};
		byte[] data2 = new byte[] {10, 11, 12};

		Path fs1Location = pathToVault.resolve("foo");
		Path fs2Location = pathToVault.resolve("bar");
		Files.createDirectories(fs1Location);
		Files.createDirectories(fs2Location);
		FileSystem fs1 = CryptoFileSystemProvider.newFileSystem(fs1Location, cryptoFileSystemProperties().withPassphrase("asd").build());
		FileSystem fs2 = CryptoFileSystemProvider.newFileSystem(fs2Location, cryptoFileSystemProperties().withPassphrase("qwe").build());
		Path file1 = fs1.getPath("/foo/bar");
		Path file2 = fs2.getPath("/bar/baz");
		Files.createDirectories(file1.getParent());
		Files.createDirectories(file2.getParent());
		Files.write(file1, data);
		Files.write(file2, data2);

		Files.copy(file1, file2, REPLACE_EXISTING);

		MatcherAssert.assertThat(readAllBytes(file1), is(data));
		MatcherAssert.assertThat(readAllBytes(file2), is(data));
	}

	@Test
	public void testLazinessOfFileAttributeViews() throws IOException {
		Path fs1Location = pathToVault.resolve("foo");
		Files.createDirectories(fs1Location);
		FileSystem fs = CryptoFileSystemProvider.newFileSystem(fs1Location, cryptoFileSystemProperties().withPassphrase("asd").build());

		Path file = fs.getPath("/foo.txt");
		BasicFileAttributeView attrView = Files.getFileAttributeView(file, BasicFileAttributeView.class);
		Assertions.assertNotNull(attrView);

		Files.write(file, new byte[3], StandardOpenOption.CREATE_NEW);
		BasicFileAttributes attrs = attrView.readAttributes();
		Assertions.assertNotNull(attrs);
		Assertions.assertEquals(3, attrs.size());

		Files.delete(file);
		Assertions.assertThrows(NoSuchFileException.class, () -> {
			attrView.readAttributes();
		});
	}

	@Test
	public void testSymbolicLinks() throws IOException {
		Path fs1Location = pathToVault.resolve("foo");
		Files.createDirectories(fs1Location);
		FileSystem fs1 = CryptoFileSystemProvider.newFileSystem(fs1Location, cryptoFileSystemProperties().withPassphrase("asd").build());

		Path link1 = fs1.getPath("/foo/bar1");
		Files.createDirectories(link1.getParent());
		Files.createSymbolicLink(link1, fs1.getPath("/linked/target1"));
		Path target1 = Files.readSymbolicLink(link1);
		MatcherAssert.assertThat(target1.getFileSystem(), is(link1.getFileSystem())); // as per contract of readSymbolicLink
		MatcherAssert.assertThat(target1.toString(), Matchers.equalTo("/linked/target1"));
		MatcherAssert.assertThat(link1.resolveSibling(target1).toString(), Matchers.equalTo("/linked/target1"));

		Path link2 = fs1.getPath("/foo/bar2");
		Files.createDirectories(link2.getParent());
		Files.createSymbolicLink(link2, fs1.getPath("./target2"));
		Path target2 = Files.readSymbolicLink(link2);
		MatcherAssert.assertThat(target2.getFileSystem(), is(link2.getFileSystem()));
		MatcherAssert.assertThat(target2.toString(), Matchers.equalTo("./target2"));
		MatcherAssert.assertThat(link2.resolveSibling(target2).normalize().toString(), Matchers.equalTo("/foo/target2"));

		Path link3 = fs1.getPath("/foo/bar3");
		Files.createDirectories(link3.getParent());
		Files.createSymbolicLink(link3, fs1.getPath("../target3"));
		Path target3 = Files.readSymbolicLink(link3);
		MatcherAssert.assertThat(target3.getFileSystem(), is(link3.getFileSystem()));
		MatcherAssert.assertThat(target3.toString(), Matchers.equalTo("../target3"));
		MatcherAssert.assertThat(link3.resolveSibling(target3).normalize().toString(), Matchers.equalTo("/target3"));
	}

	@Test
	public void testMoveFileFromOneCryptoFileSystemToAnother() throws IOException {
		byte[] data = new byte[] {1, 2, 3, 4, 5, 6, 7};

		Path fs1Location = pathToVault.resolve("foo");
		Path fs2Location = pathToVault.resolve("bar");
		Files.createDirectories(fs1Location);
		Files.createDirectories(fs2Location);
		FileSystem fs1 = CryptoFileSystemProvider.newFileSystem(fs1Location, cryptoFileSystemProperties().withPassphrase("asd").build());
		FileSystem fs2 = CryptoFileSystemProvider.newFileSystem(fs2Location, cryptoFileSystemProperties().withPassphrase("qwe").build());
		Path file1 = fs1.getPath("/foo/bar");
		Path file2 = fs2.getPath("/bar/baz");
		Files.createDirectories(file1.getParent());
		Files.createDirectories(file2.getParent());
		Files.write(file1, data);

		Files.move(file1, file2);

		MatcherAssert.assertThat(Files.exists(file1), is(false));
		MatcherAssert.assertThat(readAllBytes(file2), is(data));
	}

	@Test
	public void testDosFileAttributes(@TempDir Path tmpPath) throws IOException {
		Assumptions.assumeTrue(System.getProperty("os.name") != null && System.getProperty("os.name").startsWith("Windows"));

		FileSystem fs = CryptoFileSystemProvider.newFileSystem(tmpPath, cryptoFileSystemProperties().withPassphrase("asd").build());
		Path file = fs.getPath("/test");
		Files.write(file, new byte[1]);

		Files.setAttribute(file, "dos:hidden", true);
		Files.setAttribute(file, "dos:system", true);
		Files.setAttribute(file, "dos:archive", true);
		Files.setAttribute(file, "dos:readOnly", true);

		MatcherAssert.assertThat(Files.getAttribute(file, "dos:hidden"), is(false));
		MatcherAssert.assertThat(Files.getAttribute(file, "dos:system"), is(false));
		MatcherAssert.assertThat(Files.getAttribute(file, "dos:archive"), is(false));
		MatcherAssert.assertThat(Files.getAttribute(file, "dos:readOnly"), is(true));

		Files.setAttribute(file, "dos:hidden", false);
		Files.setAttribute(file, "dos:system", false);
		Files.setAttribute(file, "dos:archive", false);
		Files.setAttribute(file, "dos:readOnly", false);

		MatcherAssert.assertThat(Files.getAttribute(file, "dos:hidden"), is(false));
		MatcherAssert.assertThat(Files.getAttribute(file, "dos:system"), is(false));
		MatcherAssert.assertThat(Files.getAttribute(file, "dos:archive"), is(false));
		MatcherAssert.assertThat(Files.getAttribute(file, "dos:readOnly"), is(false));
	}

}
