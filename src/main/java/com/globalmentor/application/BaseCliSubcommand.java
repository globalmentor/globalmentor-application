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

import static com.globalmentor.java.Conditions.*;

import picocli.CommandLine.*;

/**
 * Utility abstract base class for a CLI class-based subcommand.
 * @apiNote Use of this class is not required for class-based subcommands—it merely makes their implementation easier, primarily by providing references to the
 *          parent command via {@link #getParentCommand()}.
 * @param <P> The type of parent command.
 * @author Garret Wilson
 */
public abstract class BaseCliSubcommand<P> {

	/** No-args constructor. */
	protected BaseCliSubcommand() {
	}

	/**
	 * Injected parent command.
	 * @implNote Field injection is necessary because of <a href="https://github.com/remkop/picocli/issues/2444">picocli Issue #2444</a>. After that issue is
	 *           fixed, this implementation will change to setter injection.
	 */
	@ParentCommand
	private P parentCommand = null;

	/**
	 * Returns the parent command of this subcommand.
	 * @return This subcommand's parent command.
	 * @throws IllegalStateException if the command has not yet been initialized.
	 */
	protected P getParentCommand() {
		checkState(parentCommand != null, "Subcommand not yet initialized.");
		return parentCommand;
	}

}
