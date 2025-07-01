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

import java.util.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.*;
import org.slf4j.*;
import org.slf4j.event.Level;
import org.slf4j.helpers.NOPLoggerFactory;

import io.clogr.*;
import io.confound.config.StringMapConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.*;

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

	/** @see BaseCliApplication#getName() */
	@Test
	void tesDefaultName() {
		assertThat(new BareCliApp().getName(), is("BareCliApp"));
	}

	/** @see BaseCliApplication#getSlug() */
	@Test
	void tesDefaultSlug() {
		assertThat("Application without annotated command name returns default slug.", new BareCliApp().getSlug(), is("bare-cli-app"));
		assertThat("Annotated application uses command name as slug.", new AnnotatedTestCliApp().getSlug(), is("foobar-app"));
	}

	/** @see BaseCliApplication#getConfigurationKey(OptionSpec) */
	@Test
	void testGetConfigurationKey() {
		final CommandLine testCliCommandLine = new CommandLine(TestCliApp.class);
		final CommandSpec testCliCommandSpec = testCliCommandLine.getCommandSpec();
		final CommandSpec doCommandSpec = testCliCommandSpec.subcommands().get("do").getCommandSpec();
		final CommandSpec doSomethingCommandSpec = doCommandSpec.subcommands().get("something").getCommandSpec();
		final OptionSpec doSomethingNamePrefixOptionSpec = doSomethingCommandSpec.findOption("name-prefix");
		assertThat(new TestCliApp().getConfigurationKey(doSomethingNamePrefixOptionSpec), is("test-cli.do.something.name-prefix"));
	}

	/** @see BaseCliApplication#getQualifiedCommandNamesOptionConfigurationKey(Stream, CharSequence) */
	@Test
	void testGetCommandOptionConfigurationKey() {
		assertThat(new TestCliApp().getQualifiedCommandNamesOptionConfigurationKey(Stream.of("my-cli", "do", "something-else"), "name-suffix"),
				is("my-cli.do.something-else.name-suffix"));
	}

	/** @see BaseCliApplication#getRelativeQualifiedCommandClassesOptionConfigurationKey(Stream, CharSequence) */
	@Test
	void testRelativeQualifiedCommandClassesOptionConfigurationKey() {
		assertThat(new TestCliApp().getRelativeQualifiedCommandClassesOptionConfigurationKey(
				Stream.of(TestCliApp.DoCommand.class, TestCliApp.DoCommand.SomethingCommand.class), "name-suffix"), is("test-cli.do.something.name-suffix"));
	}

	/** @see BaseCliApplication#enableOptionDefaultConfiguration(String) */
	@Test
	void testEnableOptionDefaultConfiguration() {
		final TestCliApp testCliApp = new TestCliApp();
		assertThat(testCliApp.isOptionDefaultConfigurationEnabled("foo.bar.widget-width"), is(false));
		assertThat(testCliApp.isOptionDefaultConfigurationEnabled("foo.bar.widget-height"), is(false));
		testCliApp.enableOptionDefaultConfiguration("foo.bar.widget-width");
		assertThat(testCliApp.isOptionDefaultConfigurationEnabled("foo.bar.widget-width"), is(true));
		assertThat(testCliApp.isOptionDefaultConfigurationEnabled("foo.bar.widget-height"), is(false));
	}

	/** @see BaseCliApplication#enableOptionDefaultConfigurationForRelativeCommandClasses(CharSequence, Class...) */
	@Test
	void testEnableOptionDefaultConfigurationForRelativeCommandClasses() {
		final TestCliApp testCliApp = new TestCliApp();
		assertThat(testCliApp.isOptionDefaultConfigurationEnabled("test-cli.do.something.name-prefix"), is(false));
		assertThat(testCliApp.isOptionDefaultConfigurationEnabled("test-cli.do.something.name-suffix"), is(false));
		testCliApp.enableOptionDefaultConfigurationForRelativeCommandClasses("name-prefix", TestCliApp.DoCommand.class,
				TestCliApp.DoCommand.SomethingCommand.class);
		assertThat(testCliApp.isOptionDefaultConfigurationEnabled("test-cli.do.something.name-prefix"), is(true));
		assertThat(testCliApp.isOptionDefaultConfigurationEnabled("test-cli.do.something.name-suffix"), is(false));
	}

	/**
	 * Verify that the base CLI application attempts to look up a non-required global option from the configuration if that option has configuration lookup
	 * enabled.
	 * @see BaseCliApplication#enableOptionDefaultConfigurationForRelativeCommandClasses(CharSequence, Class...)
	 * @see BaseCliApplication#findDefaultOptionConfigurationString(String)
	 */
	@Test
	void verifyOptionalGlobalOptionRequestsCorrectConfigurationKey() throws Exception {
		final TestCliApp testCliApp = new TestCliApp();
		testCliApp.enableOptionDefaultConfigurationForRelativeCommandClasses("optional-global-code");
		testCliApp.initialize();
		testCliApp.getCommandLine().parseArgs("do", "something", "--required-global-code", "xyz");
		assertThat(testCliApp.requestedDefaultOptionConfigurationKeys, contains("test-cli.optional-global-code"));
	}

	/**
	 * Verify that the base CLI application attempts to look up a required global option from the configuration if that option has configuration lookup enabled.
	 * @implNote This functionality is currently broken because of <a href="https://github.com/remkop/picocli/issues/2443">picocli Issue #2443: Default value
	 *           provider is not called for required options</a>.
	 * @see BaseCliApplication#enableOptionDefaultConfigurationForRelativeCommandClasses(CharSequence, Class...)
	 * @see BaseCliApplication#findDefaultOptionConfigurationString(String)
	 */
	@Test
	@Disabled //TODO re-enable when picocli Issue #2443 is fixed
	void verifyRequiredGlobalOptionRequestsCorrectConfigurationKey() throws Exception {
		final TestCliApp testCliApp = new TestCliApp();
		testCliApp.enableOptionDefaultConfigurationForRelativeCommandClasses("required-global-code");
		testCliApp.initialize();
		try {
			testCliApp.getCommandLine().parseArgs("do", "something");
		} catch(final MissingParameterException missingParameterException) {
			//we expect to get an exception because the required global option is missing, but a default lookup should be attempted
			assertThat(missingParameterException.getMissing().stream().map(OptionSpec.class::cast).map(OptionSpec::longestName).toList(),
					contains("--required-global-code"));
		}
		assertThat(testCliApp.requestedDefaultOptionConfigurationKeys, contains("test-cli.required-global-code"));
	}

	/**
	 * Verify that the base CLI application uses the configuration to look up values if for an option has configuration lookup enabled.
	 * @see BaseCliApplication#enableOptionDefaultConfigurationForRelativeCommandClasses(CharSequence, Class...)
	 * @see BaseCliApplication#findDefaultOptionConfigurationString(String)
	 */
	@Test
	void verifyOptionConfigurationFallback() throws Exception {
		final TestCliApp testCliApp = new TestCliApp();
		testCliApp.enableOptionDefaultConfigurationForRelativeCommandClasses("optional-global-code");
		testCliApp.initialize();
		testCliApp.setConfiguration(new StringMapConfiguration(Map.of("test-cli.optional-global-code", "abc")));
		testCliApp.getCommandLine().parseArgs("do", "something", "--required-global-code", "xyz");
		assertThat(testCliApp.getCommandLine().getCommandSpec().findOption("optional-global-code").getValue(), is("abc"));
	}

	static class BareCliApp extends BaseCliApplication {

		BareCliApp() {
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

	@Command(name = "test-cli", subcommands = {TestCliApp.DoCommand.class})
	class TestCliApp extends BaseCliApplication {

		final Set<String> requestedDefaultOptionConfigurationKeys = new HashSet<>();

		TestCliApp() {
			super(NO_ARGUMENTS);
			setAnsiEnabled(false); //prevent Jansi from being used, as it produces warnings during the build and moreover isn't needed for tests
		}

		@Option(names = "--optional-global-code", scope = ScopeType.INHERIT)
		String optionalGlobalCode;

		@Option(names = "--required-global-code", required = true, scope = ScopeType.INHERIT)
		String requiredGlobalCode;

		@Override
		protected Optional<String> findDefaultOptionConfigurationString(final String configurationKey) {
			requestedDefaultOptionConfigurationKeys.add(configurationKey);
			return super.findDefaultOptionConfigurationString(configurationKey);
		}

		@Override
		public void run() {
		}

		@Command(name = "do", subcommands = {DoCommand.SomethingCommand.class})
		static class DoCommand implements Runnable {
			@Override
			public void run() {
			}

			@Command(name = "something")
			static class SomethingCommand implements Runnable {
				@Option(names = {"-p", "--name-prefix"})
				boolean namePrefix;

				@Override
				public void run() {
				}
			}
		}

	}

}
