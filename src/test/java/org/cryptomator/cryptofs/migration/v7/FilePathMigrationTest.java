package org.cryptomator.cryptofs.migration.v7;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import sun.jvm.hotspot.utilities.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

public class FilePathMigrationTest {

	private Path vaultRoot = Mockito.mock(Path.class, "vaultRoot");

	@ParameterizedTest(name = "getOldCanonicalNameWithoutTypePrefix() expected to be {1} for {0}")
	@CsvSource({
			"ORSXG5A=,ORSXG5A=",
			"0ORSXG5A=,ORSXG5A=",
			"1SORSXG5A=,ORSXG5A=",
	})
	public void testGetOldCanonicalNameWithoutTypePrefix(String oldCanonicalName, String expectedResult) {
		FilePathMigration migration = new FilePathMigration(vaultRoot, oldCanonicalName);

		Assertions.assertEquals(expectedResult, migration.getOldCanonicalNameWithoutTypePrefix());
	}

	@ParameterizedTest(name = "getNewInflatedName() expected to be {1} for {0}")
	@CsvSource({
			"ORSXG5A=,dGVzdA==.c9r",
			"0ORSXG5A=,dGVzdA==.c9r",
			"1SORSXG5A=,dGVzdA==.c9r",
	})
	public void testGetNewInflatedName(String oldCanonicalName, String expectedResult) {
		FilePathMigration migration = new FilePathMigration(vaultRoot, oldCanonicalName);

		Assertions.assertEquals(expectedResult, migration.getNewInflatedName());
	}

	@ParameterizedTest(name = "getNewInflatedName() expected to be {1} for {0}")
	@CsvSource({
			"ORSXG5A=,dGVzdA==.c9r",
			"ORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSQ====,dGVzdCB0ZXN0IHRlc3QgdGVzdCB0ZXN0IHRlc3QgdGVzdCB0ZXN0IHRlc3QgdGVzdCB0ZXN0IHRlc3QgdGVzdCB0ZXN0IHRlc3QgdGVzdCB0ZXN0IHRlc3QgdGVzdCB0ZXN0IHRlc3QgdGVzdCB0ZXN0IHRlc3QgdGVzdCB0ZXN0IHRlc3QgdGVzdCB0ZXN0IHRlc3QgdGVzdCB0ZXN0IHRl.c9r",
			"ORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG5BAORSXG===,30xtS3YjsiMJRwu1oAVc_0S2aAU=.c9s",
	})
	public void testGetNewDeflatedName(String oldCanonicalName, String expectedResult) {
		FilePathMigration migration = new FilePathMigration(vaultRoot, oldCanonicalName);

		Assertions.assertEquals(expectedResult, migration.getNewDeflatedName());
	}
	
	@ParameterizedTest(name = "isDirectory() expected to be {1} for {0}")
	@CsvSource({
			"ORSXG5A=,false",
			"0ORSXG5A=,true",
			"1SORSXG5A=,false",
	})
	public void testIsDirectory(String oldCanonicalName, boolean expectedResult) {
		FilePathMigration migration = new FilePathMigration(vaultRoot, oldCanonicalName);
		
		Assertions.assertEquals(expectedResult, migration.isDirectory());
	}

	@ParameterizedTest(name = "isSymlink() expected to be {1} for {0}")
	@CsvSource({
			"ORSXG5A=,false",
			"0ORSXG5A=,false",
			"1SORSXG5A=,true",
	})
	public void testIsSymlink(String oldCanonicalName, boolean expectedResult) {
		FilePathMigration migration = new FilePathMigration(vaultRoot, oldCanonicalName);

		Assertions.assertEquals(expectedResult, migration.isSymlink());
	}
	
	@DisplayName("FilePathMigration.parse(...)")
	@Nested
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class Parsing {

		private FileSystem fs;
		private Path dataDir;
		private Path metaDir;
		
		@BeforeAll
		public void beforeAll() {
			fs = Jimfs.newFileSystem(Configuration.unix());
			vaultRoot = fs.getPath("/vaultDir");
			dataDir = vaultRoot.resolve("d");
			metaDir = vaultRoot.resolve("m");
		}
		
		@BeforeEach
		public void beforeEach() throws IOException {
			Files.createDirectory(vaultRoot);
			Files.createDirectory(dataDir);
			Files.createDirectory(metaDir);
		}
		
		@AfterEach
		public void afterEach() throws IOException {
			MoreFiles.deleteRecursively(vaultRoot, RecursiveDeleteOption.ALLOW_INSECURE);
		}

		@DisplayName("regular files")
		@ParameterizedTest(name = "parse(vaultRoot, {0}).getOldCanonicalName() expected to be {1}")
		@CsvSource({
				"00/000000000000000000000000000000/ORSXG5A=,ORSXG5A=",
				"00/000000000000000000000000000000/0ORSXG5A=,0ORSXG5A=",
				"00/000000000000000000000000000000/1SORSXG5A=,1SORSXG5A=",
				// TODO: add conflicting files
		})
		public void testParseNonShortenedFile(String oldPath, String expected) throws IOException {
			Path path = dataDir.resolve(oldPath);

			FilePathMigration migration = FilePathMigration.parse(vaultRoot, path);

			Assertions.assertEquals(expected, migration.getOldCanonicalName());
		}

		@DisplayName("shortened files")
		@ParameterizedTest(name = "parse(vaultRoot, {0}).getOldCanonicalName() expected to be {2}")
		@CsvSource({
				"00/000000000000000000000000000000/NTJDZUB3J5S25LGO7CD4TE5VOJCSW7HF.lng,NT/JD/NTJDZUB3J5S25LGO7CD4TE5VOJCSW7HF.lng,ORSXG5A=",
				"00/000000000000000000000000000000/ZNPCXPWRWYFOGTZHVDBOOQDYPAMKKI5R.lng,ZN/PC/ZNPCXPWRWYFOGTZHVDBOOQDYPAMKKI5R.lng,0ORSXG5A=",
				"00/000000000000000000000000000000/NUC3VFSMWKLD4526JDZKSE5V2IIMSYW5.lng,NU/C3/NUC3VFSMWKLD4526JDZKSE5V2IIMSYW5.lng,1SORSXG5A=",
				// TODO: add conflicting files
		})
		public void testParseShortenedFile(String oldPath, String metadataFilePath, String expected) throws IOException {
			Path path = dataDir.resolve(oldPath);
			Path lngFilePath = metaDir.resolve(metadataFilePath);
			Files.createDirectories(lngFilePath.getParent());
			Files.write(lngFilePath, expected.getBytes(UTF_8));

			FilePathMigration migration = FilePathMigration.parse(vaultRoot, path);

			Assertions.assertEquals(expected, migration.getOldCanonicalName());
		}
		
	}
	
}