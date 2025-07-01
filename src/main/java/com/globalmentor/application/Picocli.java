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

import static com.globalmentor.reflect.AnnotatedElements.*;
import static java.util.stream.Stream.*;

import java.util.Optional;
import java.util.stream.Stream;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.*;

/**
 * Utilities for working with Picocli.
 * @apiNote This class is not part of the public API.
 * @author Garret Wilson
 */
class Picocli {

	/**
	 * Find the annotated command name for a given class.
	 * @param commandClass The class supposedly annotated with the {@link Command} annotation.
	 * @return The annotated command name of the given class, if any.
	 * @see Command#name()
	 */
	public static Optional<String> findAnnotatedCommandName(final Class<?> commandClass) {
		return findAnnotation(commandClass, Command.class).map(Command::name);
	}

	/**
	 * Retrieves the sequence of command specs, including all the ancestor commands, for the given command or subcommand. For example for the <code>ls</code>
	 * subcommand of <code>aws s3 ls</code> for listing S3 buckets in S3, this method would return a stream of specs for commands <code>aws</code>,
	 * <code>s3</code>, and <code>ls</code>.
	 * @apiNote This is similar to {@link CommandSpec#qualifiedName(String)}, but gives more flexibility for later processing by returning a stream of the command
	 *          specs themselves.
	 * @param commandSpec The command or subcommand spec for which to produce a sequence of command specs.
	 * @return The sequence of command specs to arrive at this command spec.
	 * @see CommandSpec#qualifiedName(String)
	 */
	public static Stream<CommandSpec> qualifiedCommandSpecs(final CommandSpec commandSpec) {
		final Optional<Stream<CommandSpec>> foundParentQualifiedCommandSpecs = Optional.ofNullable(commandSpec.parent()).map(Picocli::qualifiedCommandSpecs);
		final Stream<CommandSpec> unqualifiedCommandSpec = Stream.of(commandSpec);
		return foundParentQualifiedCommandSpecs.map(parentCommandSequence -> concat(parentCommandSequence, unqualifiedCommandSpec)).orElse(unqualifiedCommandSpec);
	}

}
