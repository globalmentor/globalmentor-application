/*
 * Copyright © 2025 GlobalMentor, Inc. <https://www.globalmentor.com/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.globalmentor.application;

import static com.github.npathai.hamcrestopt.OptionalMatchers.isPresentAnd;
import static java.nio.file.Files.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.helpers.NOPLoggerFactory;

import io.clogr.Clogr;
import io.clogr.LoggingConcern;
import picocli.CommandLine.Command;

/**
 * Integration tests of {@link BaseCliApplication}.
 * @author Garret Wilson
 */
public class BaseCliApplicationIT {

	private static final LoggingConcern DUMMY_LOGGING_CONCERN = new LoggingConcern() { //TODO replace with CLOGR-15, or remove altogether after JAVA-392
		@Override
		public void setLogLevel(Logger logger, Level level) {
		}

		@Override
		public ILoggerFactory getLoggerFactory() {
			return new NOPLoggerFactory();
		}
	};

	/**
	 * Force logging to use a dummy logger that ignores everything during testing. Because this library currently includes a Clogr implementation for Logback,
	 * that implementation will attempt to set the log level in the CLI application and fail (expecting a Logback logger), because the build is configured to use
	 * a different no-nop logger rather than Logback for tests.
	 */
	@BeforeAll
	static void installDummyLogger() {
		Clogr.setDefaultLoggingConcern(DUMMY_LOGGING_CONCERN);
	}

	@TempDir
	Path globalConfigHomeDirectory;

	/** The temporary directory used as a base for the local config subdirectories. */
	Path localConfigDirectoryAncestor;

	/**
	 * The temporary local configuration directory, created with each test run
	 * @implSpec Uses the path <code>one/two/three/</code> relative to {@link #localConfigDirectoryAncestor}.
	 */
	Path localConfigDirectory;

	@BeforeEach
	void createLocalConfigDirectory(@TempDir final Path localConfigBaseDirectory) throws IOException {
		this.localConfigDirectoryAncestor = localConfigBaseDirectory;
		localConfigDirectory = createDirectories(localConfigBaseDirectory.resolve("one").resolve("two").resolve("three"));
	}

	@Test
	void verifyInitializesIfNoGlobalConfigDirectoryExists() throws Exception {
		final TestCliApp testApp = new TestCliApp();
		testApp.initialize();
		assertThat(testApp.getConfiguration().findUri("foo"), is(Optional.empty()));
	}

	@Test
	void verifyInitializesIfNoGlobalConfigFileExists() throws Exception {
		createDirectory(globalConfigHomeDirectory.resolve(".test-cli"));
		final TestCliApp testApp = new TestCliApp();
		testApp.initialize();
		assertThat(testApp.getConfiguration().findUri("foo"), is(Optional.empty()));
	}

	@Test
	void verifyLoadsGlobalConfig() throws Exception {
		writeString(createDirectory(globalConfigHomeDirectory.resolve(".test-cli")).resolve("test-cli.properties"), "foo=bar");
		final TestCliApp testApp = new TestCliApp();
		testApp.initialize();
		assertThat(testApp.getConfiguration().findString("foo"), isPresentAnd(is("bar")));
	}

	@Test
	void verifySystemPropertyTakesPrecedenceOverGlobalConfig() throws Exception {
		try {
			write(createDirectory(globalConfigHomeDirectory.resolve(".test-cli")).resolve("test-cli.properties"), List.of("foo=bar", "x=y"));
			System.setProperty("x", "z");
			final TestCliApp testApp = new TestCliApp();
			testApp.initialize();
			assertThat(testApp.getConfiguration().findString("foo"), isPresentAnd(is("bar")));
			assertThat(testApp.getConfiguration().findString("x"), isPresentAnd(is("z")));
		} finally {
			System.clearProperty("x");
		}
	}

	@Test
	void verifyLoadsLocalConfigInGlobalConfigHome() throws Exception {
		writeString(globalConfigHomeDirectory.resolve(".test-cli.properties"), "foo=bar");
		final TestCliApp testApp = new TestCliApp();
		testApp.initialize();
		assertThat(testApp.getConfiguration().findString("foo"), isPresentAnd(is("bar")));
	}

	@Test
	void verifyLocalConfigInGlobalConfigHomeTakesPrecedenceOverGlobalConfig() throws Exception {
		write(createDirectory(globalConfigHomeDirectory.resolve(".test-cli")).resolve("test-cli.properties"), List.of("globalSetting=abc", "foobar=global wins"));
		write(globalConfigHomeDirectory.resolve(".test-cli.properties"), List.of("localHomeSetting=def", "foobar=local home wins"));
		final TestCliApp testApp = new TestCliApp();
		testApp.initialize();
		assertThat(testApp.getConfiguration().findString("globalSetting"), isPresentAnd(is("abc")));
		assertThat(testApp.getConfiguration().findString("localHomeSetting"), isPresentAnd(is("def")));
		assertThat(testApp.getConfiguration().findString("foobar"), isPresentAnd(is("local home wins")));
	}

	@Test
	void verifySystemPropertyTakesPrecedenceOverGlobalAndLocalConfigs() throws Exception {
		try {
			write(createDirectory(globalConfigHomeDirectory.resolve(".test-cli")).resolve("test-cli.properties"), List.of("globalSetting=abc", "foobar=global wins"));
			write(globalConfigHomeDirectory.resolve(".test-cli.properties"), List.of("localHomeSetting=def", "foobar=local home wins"));
			System.setProperty("systemSetting", "ghi");
			System.setProperty("foobar", "system wins");
			final TestCliApp testApp = new TestCliApp();
			testApp.initialize();
			assertThat(testApp.getConfiguration().findString("globalSetting"), isPresentAnd(is("abc")));
			assertThat(testApp.getConfiguration().findString("localHomeSetting"), isPresentAnd(is("def")));
			assertThat(testApp.getConfiguration().findString("systemSetting"), isPresentAnd(is("ghi")));
			assertThat(testApp.getConfiguration().findString("foobar"), isPresentAnd(is("system wins")));
		} finally {
			System.clearProperty("systemSetting");
			System.clearProperty("foobar");
		}
	}

	@Test
	void verifyLoadsLocalConfig() throws Exception {
		writeString(localConfigDirectory.resolve(".test-cli.properties"), "foo=bar");
		final TestCliApp testApp = new TestCliApp();
		testApp.initialize();
		assertThat(testApp.getConfiguration().findString("foo"), isPresentAnd(is("bar")));
	}

	@Test
	void verifyLoadsLocalConfigAncestor() throws Exception {
		writeString(localConfigDirectoryAncestor.resolve(".test-cli.properties"), "foo=bar");
		final TestCliApp testApp = new TestCliApp();
		testApp.initialize();
		assertThat(testApp.getConfiguration().findString("foo"), isPresentAnd(is("bar")));
	}

	@Test
	void verifyLocalConfigTakesPrecedenceOverGlobalConfig() throws Exception {
		write(createDirectory(globalConfigHomeDirectory.resolve(".test-cli")).resolve("test-cli.properties"), List.of("globalSetting=abc", "foobar=global wins"));
		write(localConfigDirectory.resolve(".test-cli.properties"), List.of("localSetting=def", "foobar=local wins"));
		final TestCliApp testApp = new TestCliApp();
		testApp.initialize();
		assertThat(testApp.getConfiguration().findString("globalSetting"), isPresentAnd(is("abc")));
		assertThat(testApp.getConfiguration().findString("localSetting"), isPresentAnd(is("def")));
		assertThat(testApp.getConfiguration().findString("foobar"), isPresentAnd(is("local wins")));
	}

	@Test
	void verifyLocalConfigTakesPrecedenceOverLocalConfigInGlobalConfigHome() throws Exception {
		write(globalConfigHomeDirectory.resolve(".test-cli.properties"), List.of("localHomeSetting=abc", "foobar=local home wins"));
		write(localConfigDirectory.resolve(".test-cli.properties"), List.of("localSetting=def", "foobar=local wins"));
		final TestCliApp testApp = new TestCliApp();
		testApp.initialize();
		assertThat(testApp.getConfiguration().findString("localHomeSetting"), isPresentAnd(is("abc")));
		assertThat(testApp.getConfiguration().findString("localSetting"), isPresentAnd(is("def")));
		assertThat(testApp.getConfiguration().findString("foobar"), isPresentAnd(is("local wins")));
	}

	@Test
	void verifyLocalConfigTakesPrecedenceOverLocalConfigAncestor() throws Exception {
		write(localConfigDirectoryAncestor.resolve(".test-cli.properties"), List.of("localAncestorSetting=abc", "foobar=local ancestor wins"));
		write(localConfigDirectory.resolve(".test-cli.properties"), List.of("localSetting=def", "foobar=local wins"));
		final TestCliApp testApp = new TestCliApp();
		testApp.initialize();
		assertThat(testApp.getConfiguration().findString("localAncestorSetting"), isPresentAnd(is("abc")));
		assertThat(testApp.getConfiguration().findString("localSetting"), isPresentAnd(is("def")));
		assertThat(testApp.getConfiguration().findString("foobar"), isPresentAnd(is("local wins")));
	}

	@Test
	void verifyFullPrecedence() throws Exception {
		try {
			write(createDirectory(globalConfigHomeDirectory.resolve(".test-cli")).resolve("test-cli.properties"), List.of("globalSetting=abc", "foobar=global wins"));
			write(globalConfigHomeDirectory.resolve(".test-cli.properties"), List.of("localHomeSetting=def", "foobar=local home wins"));
			write(localConfigDirectoryAncestor.resolve(".test-cli.properties"), List.of("localAncestorSetting=ghi", "foobar=local ancestor wins"));
			write(localConfigDirectory.resolve(".test-cli.properties"), List.of("localSetting=jkl", "foobar=local wins"));
			System.setProperty("systemSetting", "mno");
			System.setProperty("foobar", "system wins");
			final TestCliApp testApp = new TestCliApp();
			testApp.initialize();
			assertThat(testApp.getConfiguration().findString("globalSetting"), isPresentAnd(is("abc")));
			assertThat(testApp.getConfiguration().findString("localHomeSetting"), isPresentAnd(is("def")));
			assertThat(testApp.getConfiguration().findString("localAncestorSetting"), isPresentAnd(is("ghi")));
			assertThat(testApp.getConfiguration().findString("localSetting"), isPresentAnd(is("jkl")));
			assertThat(testApp.getConfiguration().findString("systemSetting"), isPresentAnd(is("mno")));
			assertThat(testApp.getConfiguration().findString("foobar"), isPresentAnd(is("system wins")));
		} finally {
			System.clearProperty("systemSetting");
			System.clearProperty("foobar");
		}
	}

	@Command(name = "test-cli")
	class TestCliApp extends BaseCliApplication {

		TestCliApp() {
			super(NO_ARGUMENTS);
		}

		@Override
		public String getVersion() {
			return "0.0.0";
		}

		@Override
		public void run() {
		}

		@Override
		protected Path getGlobalConfigurationHomeDirectory() {
			return globalConfigHomeDirectory;
		}

		@Override
		protected Path getLocalConfigurationDirectory() {
			return localConfigDirectory;
		}
	}

}
