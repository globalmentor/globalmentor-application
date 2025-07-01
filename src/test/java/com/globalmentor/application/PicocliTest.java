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

import org.junit.jupiter.api.*;

import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.*;

/**
 * Tests of {@link Picocli} utility class.
 * @author Garret Wilson
 */
public class PicocliTest {

	/** @see Picocli#qualifiedCommandSpecs(CommandSpec) */
	@Test
	void testQualifiedCommandSpecs() {
		final CommandLine commandLine = new CommandLine(TestCliApp.class);
		final CommandSpec testCliCommandSpec = commandLine.getCommandSpec();
		final CommandSpec doCommandSpec = testCliCommandSpec.subcommands().get("do").getCommandSpec();
		final CommandSpec somethingCommandSpec = doCommandSpec.subcommands().get("something").getCommandSpec();
		assertThat(Picocli.qualifiedCommandSpecs(testCliCommandSpec).toList(), contains(testCliCommandSpec));
		assertThat(Picocli.qualifiedCommandSpecs(doCommandSpec).toList(), contains(testCliCommandSpec, doCommandSpec));
		assertThat(Picocli.qualifiedCommandSpecs(somethingCommandSpec).toList(), contains(testCliCommandSpec, doCommandSpec, somethingCommandSpec));
	}

	@Command(name = "test-cli", subcommands = {TestCliApp.DoCommand.class})
	class TestCliApp implements Runnable {
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
				@Override
				public void run() {
				}
			}

		}
	}

	/**
	 * Tests that Picocli itself correctly calls a default value provider for a missing global option that is not required.
	 * @see <a href="https://github.com/remkop/picocli/issues/2443">Default value provider is not called for required options. #2443</a>
	 */
	@Test
	void verifyPicoCliCallsDefaultValueProviderForOptionalGlobalOption() {
		final PicocliIssue2443App picocliIssue2443App = new PicocliIssue2443App();
		final CommandLine commandLine = new CommandLine(picocliIssue2443App);
		final Set<String> argsCallingDefaultValueProvider = new HashSet<>();
		commandLine.setDefaultValueProvider(new IDefaultValueProvider() {
			@Override
			public String defaultValue(final ArgSpec argSpec) throws Exception {
				argsCallingDefaultValueProvider.add(((OptionSpec)argSpec).longestName());
				return "foobar";
			}
		});
		commandLine.parseArgs("do", "something", "--required-global-code", "xyz");
		assertThat(argsCallingDefaultValueProvider, contains("--optional-global-code"));
	}

	/**
	 * Tests that Picocli itself correctly calls a default value provider for a missing global option that is not required.
	 * @see <a href="https://github.com/remkop/picocli/issues/2443">Default value provider is not called for required options. #2443</a>
	 */
	@Test
	@Disabled //TODO re-enable when picocli Issue #2443 is fixed
	void verifyPicoCliCallsDefaultValueProviderForRequiredGlobalOption() {
		final PicocliIssue2443App picocliIssue2443App = new PicocliIssue2443App();
		final CommandLine commandLine = new CommandLine(picocliIssue2443App);
		final Set<String> argsCallingDefaultValueProvider = new HashSet<>();
		commandLine.setDefaultValueProvider(new IDefaultValueProvider() {
			@Override
			public String defaultValue(final ArgSpec argSpec) throws Exception {
				argsCallingDefaultValueProvider.add(((OptionSpec)argSpec).longestName());
				return "foobar";
			}
		});
		commandLine.parseArgs("do", "something", "--optional-global-code", "abc");
		assertThat(argsCallingDefaultValueProvider, contains("--required-global-code"));
	}

	/** Tests class for <a href="https://github.com/remkop/picocli/issues/2443">picocli Issue #2443</a>. */
	@Command(name = "issue2443", subcommands = {PicocliIssue2443App.DoCommand.class})
	class PicocliIssue2443App implements Runnable {

		@Option(names = "--optional-global-code", scope = ScopeType.INHERIT)
		String optionalGlobalCode;

		@Option(names = "--required-global-code", required = true, scope = ScopeType.INHERIT)
		String requiredGlobalCode;

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
				@Override
				public void run() {
				}
			}
		}
	}

}
