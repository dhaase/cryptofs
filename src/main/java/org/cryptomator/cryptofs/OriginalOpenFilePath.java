package org.cryptomator.cryptofs;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import javax.inject.Qualifier;

/**
 * The Path used to create an OpenCryptoFile
 */
@Qualifier
@Documented
@Retention(RUNTIME)
@interface OriginalOpenFilePath {
}