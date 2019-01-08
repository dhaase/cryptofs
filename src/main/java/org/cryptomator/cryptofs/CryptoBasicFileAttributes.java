/*******************************************************************************
 * Copyright (c) 2016 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.cryptofs;

import org.cryptomator.cryptofs.CryptoPathMapper.CiphertextFileType;
import org.cryptomator.cryptolib.Cryptors;
import org.cryptomator.cryptolib.api.Cryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

class CryptoBasicFileAttributes implements DelegatingBasicFileAttributes {

	private static final Logger LOG = LoggerFactory.getLogger(CryptoBasicFileAttributes.class);

	private final BasicFileAttributes delegate;
	private final CiphertextFileType ciphertextFileType;
	protected final Path ciphertextPath;
	private final Cryptor cryptor;
	private final Optional<OpenCryptoFile> openCryptoFile;
	protected final boolean readonly;

	public CryptoBasicFileAttributes(BasicFileAttributes delegate, CiphertextFileType ciphertextFileType, Path ciphertextPath, Cryptor cryptor, Optional<OpenCryptoFile> openCryptoFile, boolean readonly) {
		this.delegate = delegate;
		this.ciphertextFileType = ciphertextFileType;
		this.ciphertextPath = ciphertextPath;
		this.cryptor = cryptor;
		this.openCryptoFile = openCryptoFile;
		this.readonly = readonly;
	}

	@Override
	public BasicFileAttributes getDelegate() {
		return delegate;
	}

	@Override
	public boolean isRegularFile() {
		return CiphertextFileType.FILE == ciphertextFileType;
	}

	@Override
	public boolean isDirectory() {
		return CiphertextFileType.DIRECTORY == ciphertextFileType;
	}

	@Override
	public boolean isSymbolicLink() {
		return CiphertextFileType.SYMLINK == ciphertextFileType;
	}

	@Override
	public boolean isOther() {
		return !isRegularFile() && !isDirectory() && !isSymbolicLink();
	}

	/**
	 * Gets the size of the decrypted file.
	 *
	 * @return the size of the decrypted file
	 */
	@Override
	public long size() {
		if (isDirectory()) {
			return getDelegate().size();
		} else if (isRegularFile()) {
			return fileSize();
		} else if (isSymbolicLink()) {
			return -1l;
		} else {
			assert isOther();
			return -1l;
		}
	}

	private long fileSize() {
		if (openCryptoFile.isPresent()) {
			return openCryptoFile.get().size();
		} else {
			try {
				return Cryptors.cleartextSize(getDelegate().size() - cryptor.fileHeaderCryptor().headerSize(), cryptor);
			} catch (IllegalArgumentException e) {
				LOG.warn("Wrong cipher text file size of file {}. Returning a file size of 0.", ciphertextPath);
				LOG.warn("Thrown exception was:", e);
				return 0l;
			}
		}
	}

	@Override
	public FileTime lastModifiedTime() {
		return openCryptoFile.map(OpenCryptoFile::getLastModifiedTime).orElseGet(delegate::lastModifiedTime);
	}
}
