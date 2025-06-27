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

/**
 * Integration tests of {@link AbstractApplication}.
 * @author Garret Wilson
 */
public class AbstractApplicationIT {

	@TempDir
	Path globalConfigHomeDirectory;

	@Test
	void verifyInitializesIfNoGlobalConfigDirectoryExists() throws Exception {
		final TestApp testApp = new TestApp();
		testApp.initialize();
		assertThat(testApp.getConfiguration().findUri("foo"), is(Optional.empty()));
	}

	@Test
	void verifyInitializesIfNoGlobalConfigFileExists() throws Exception {
		createDirectory(globalConfigHomeDirectory.resolve(".test-app"));
		final TestApp testApp = new TestApp();
		testApp.initialize();
		assertThat(testApp.getConfiguration().findUri("foo"), is(Optional.empty()));
	}

	@Test
	void verifyLoadsGlobalConfig() throws Exception {
		writeString(createDirectory(globalConfigHomeDirectory.resolve(".test-app")).resolve("test-app.properties"), "foo=bar");
		final TestApp testApp = new TestApp();
		testApp.initialize();
		assertThat(testApp.getConfiguration().findString("foo"), isPresentAnd(is("bar")));
	}

	@Test
	void verifySystemPropertyTakesPrecedenceOverGlobalConfig() throws Exception {
		write(createDirectory(globalConfigHomeDirectory.resolve(".test-app")).resolve("test-app.properties"), List.of("foo=bar", "x=y"));
		System.setProperty("x", "z");
		final TestApp testApp = new TestApp();
		testApp.initialize();
		assertThat(testApp.getConfiguration().findString("foo"), isPresentAnd(is("bar")));
		assertThat(testApp.getConfiguration().findString("x"), isPresentAnd(is("z")));
	}

	class TestApp extends AbstractApplication {
		@Override
		public String getVersion() {
			return "0.0.0";
		}

		@Override
		public void run() {
		}

		@Override
		public Path getGlobalConfigurationHomeDirectory() {
			return globalConfigHomeDirectory;
		}

	}

}
