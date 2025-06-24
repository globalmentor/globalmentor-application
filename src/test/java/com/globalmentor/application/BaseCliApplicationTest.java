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

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.*;
import org.slf4j.*;
import org.slf4j.event.Level;
import org.slf4j.helpers.NOPLoggerFactory;

import io.clogr.*;
import picocli.CommandLine.Command;

/**
 * Tests of {@link BaseCliApplication}.
 * @author Garret Wilson
 */
public class BaseCliApplicationTest {

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

	/** @see Application#getName() */
	@Test
	void tesDefaultName() {
		assertThat(new TestCliApp().getName(), is("TestCliApp"));
	}

	/** @see Application#getSlug() */
	@Test
	void tesDefaultSlug() {
		assertThat("Application without annotated command name returns default slug.", new TestCliApp().getSlug(), is("test-cli-app"));
		assertThat("Annotated application uses command name as slug.", new AnnotatedTestCliApp().getSlug(), is("foobar-app"));
	}

	static class TestCliApp extends BaseCliApplication {

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
	}

	@Command(name = "foobar-app")
	static class AnnotatedTestCliApp extends BaseCliApplication {

		AnnotatedTestCliApp() {
			super(NO_ARGUMENTS);
		}

		@Override
		public String getVersion() {
			return "0.0.0";
		}

		@Override
		public void run() {
		}
	}

}
