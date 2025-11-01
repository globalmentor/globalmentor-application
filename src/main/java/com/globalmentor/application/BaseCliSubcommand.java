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

import com.globalmentor.function.LazySupplier;

import picocli.CommandLine;
import picocli.CommandLine.*;

/**
 * Utility abstract base class for a CLI class-based subcommand.
 * <p>This class must only be used with an application that implements {@link PicocliApplication}.</p>
 * @apiNote Use of this class is not required for class-based subcommands—it merely makes their implementation easier, primarily by providing references to the
 *          parent command via {@link #getParentCommand()}.
 * @param <A> The type of CLI application.
 * @param <P> The type of parent command.
 * @author Garret Wilson
 * @see PicocliApplication
 */
@Command(mixinStandardHelpOptions = true)
public abstract class BaseCliSubcommand<A extends PicocliApplication, P> {

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
	public P getParentCommand() {
		checkState(parentCommand != null, "Subcommand not yet initialized.");
		return parentCommand;
	}

	// Retrieve the application lazily so as not to attempt to find it before it is requested, i.e. before the application is initialized.
	@SuppressWarnings("this-escape") //this supplier is not invoked until after the class has finished construction
	private final LazySupplier<A> applicationSupplier = LazySupplier.ofAtomic(() -> {
		Object command = BaseCliSubcommand.this;
		while(!(command instanceof PicocliApplication)) { //this allows for the subcommand to be the application, which shouldn't be common, but there's no obvious reason to prevent it
			//TODO probably improve with a `CliSubcommand` interface
			checkState(command instanceof BaseCliSubcommand<?, ?>, "Application not found in command hierarchy."); //if this is not a subcommand, we can look no further
			command = ((BaseCliSubcommand<?, ?>)command).getParentCommand(); //check the parent command
		}
		@SuppressWarnings("unchecked")
		final A application = (A)command;
		return application;

	});

	/**
	 * Determines the application associated with this subcommand. Must only be called after the application has been initialized
	 * @implSpec This implementation navigates the command hierarchy using {@link #getParentCommand()}. The application is retrieved using a lazy supplier and
	 *           cached so that subsequent lookups are efficient.
	 * @implNote This implementation does not make any checks to ensure that the encountered {@link PicocliException} is in fact an instance of the generic
	 *           application type specified. Thus if this subcommand is used with a different type of application, use of the returned application can result in a
	 *           {@link ClassCastException} that is thrown after this method has completed.
	 * @return The associated application.
	 * @throws IllegalStateException if this method is called before the application is initialized.
	 * @throws IllegalStateException If none of the parent commands are instances of {@link PicocliApplication}.
	 * @see #getParentCommand()
	 */
	protected A getApplication() {
		return applicationSupplier.get();
	}

	/**
	 * Returns the picocli command line instance. This method must never be called before application initialization.
	 * @implSpec This implementation delegates to {@link #getApplication()} to retrieve the application.
	 * @apiNote This is useful for accessing the color scheme and for constructing an instance of {@link ParameterException}.
	 * @return The picocli command line instance.
	 * @throws IllegalStateException if this method is called before the application is initialized.
	 * @throws IllegalStateException If none of the parent commands are instances of {@link PicocliApplication}.
	 * 
	 */
	protected CommandLine getCommandLine() {
		return getApplication().getCommandLine();
	}

}
