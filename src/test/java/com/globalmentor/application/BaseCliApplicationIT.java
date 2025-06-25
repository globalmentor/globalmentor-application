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

	@Test
	void verifyInitializesIfNoGlobalConfigDirectoryExists() throws Exception {
		final TestCliApp testApp = new TestCliApp();
		testApp.initialize();
		assertThat(testApp.getConfig().findUri("foo"), is(Optional.empty()));
	}

	@Test
	void verifyInitializesIfNoGlobalConfigFileExists() throws Exception {
		createDirectory(globalConfigHomeDirectory.resolve(".test-cli"));
		final TestCliApp testApp = new TestCliApp();
		testApp.initialize();
		assertThat(testApp.getConfig().findUri("foo"), is(Optional.empty()));
	}

	@Test
	void verifyLoadsGlobalConfiguration() throws Exception {
		final Path configDirectory = createDirectory(globalConfigHomeDirectory.resolve(".test-cli"));
		writeString(configDirectory.resolve("test-cli.properties"), "foo=bar");
		final TestCliApp testApp = new TestCliApp();
		testApp.initialize();
		assertThat(testApp.getConfig().findString("foo"), isPresentAnd(is("bar")));
	}

	@Test
	void verifySystemPropertyTakesPrecedenceOverGlobalConfiguration() throws Exception {
		try {
			final Path configDirectory = createDirectory(globalConfigHomeDirectory.resolve(".test-cli"));
			write(configDirectory.resolve("test-cli.properties"), List.of("foo=bar", "x=y"));
			System.setProperty("x", "z");
			final TestCliApp testApp = new TestCliApp();
			testApp.initialize();
			assertThat(testApp.getConfig().findString("foo"), isPresentAnd(is("bar")));
			assertThat(testApp.getConfig().findString("x"), isPresentAnd(is("z")));
		} finally {
			System.clearProperty("x");
		}
	}

	@Test
	void verifyLoadsLocalConfigurationInGlobalConfigHome() throws Exception {
		writeString(globalConfigHomeDirectory.resolve(".test-cli.properties"), "foo=bar");
		final TestCliApp testApp = new TestCliApp();
		testApp.initialize();
		assertThat(testApp.getConfig().findString("foo"), isPresentAnd(is("bar")));
	}

	@Test
	void verifyLocalConfigurationInGlobalConfigHomeTakesPrecedenceOverGlobalConfiguration() throws Exception {
		final Path configDirectory = createDirectory(globalConfigHomeDirectory.resolve(".test-cli"));
		write(configDirectory.resolve("test-cli.properties"), List.of("globalsetting=abc", "foobar=global wins"));
		write(globalConfigHomeDirectory.resolve(".test-cli.properties"), List.of("localhomesetting=def", "foobar=localhome wins"));
		final TestCliApp testApp = new TestCliApp();
		testApp.initialize();
		assertThat(testApp.getConfig().findString("globalsetting"), isPresentAnd(is("abc")));
		assertThat(testApp.getConfig().findString("localhomesetting"), isPresentAnd(is("def")));
		assertThat(testApp.getConfig().findString("foobar"), isPresentAnd(is("localhome wins")));
	}

	@Test
	void verifySystemPropertyTakesPrecedenceOverGlobalAndLocalConfigurations() throws Exception {
		try {
			final Path configDirectory = createDirectory(globalConfigHomeDirectory.resolve(".test-cli"));
			write(configDirectory.resolve("test-cli.properties"), List.of("globalsetting=abc", "foobar=global wins"));
			write(globalConfigHomeDirectory.resolve(".test-cli.properties"), List.of("localhomesetting=def", "foobar=localhome wins"));
			System.setProperty("systemsetting", "ghi");
			System.setProperty("foobar", "system wins");

			final TestCliApp testApp = new TestCliApp();
			testApp.initialize();
			assertThat(testApp.getConfig().findString("globalsetting"), isPresentAnd(is("abc")));
			assertThat(testApp.getConfig().findString("localhomesetting"), isPresentAnd(is("def")));
			assertThat(testApp.getConfig().findString("systemsetting"), isPresentAnd(is("ghi")));
			assertThat(testApp.getConfig().findString("foobar"), isPresentAnd(is("system wins")));
		} finally {
			System.clearProperty("systemsetting");
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
		public Path getGlobalConfigHomeDirectory() {
			return globalConfigHomeDirectory;
		}

	}

}
