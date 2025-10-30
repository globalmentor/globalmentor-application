/*
 * Copyright © 1996-2019 GlobalMentor, Inc. <https://www.globalmentor.com/>
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
import static com.globalmentor.lex.CompoundTokenization.*;

import java.io.*;
import java.nio.file.NoSuchFileException;
import java.time.LocalDate;
import java.util.*;
import java.util.prefs.Preferences;

import org.jspecify.annotations.*;

import com.globalmentor.model.Named;
import com.globalmentor.net.*;

import io.clogr.Clogged;
import io.confound.config.Configuration;
import io.confound.config.ConfigurationException;
import io.confound.config.file.ResourcesConfigurationManager;

/**
 * A general application.
 * <p>To start an application, call the static {@link #start(Application)} method, passing it an application instance.</p>
 * @apiNote Although an application implements {@link Runnable}, it should usually be started using {@link #start()}, which will eventually (depending on the
 *          implementation) call {@link #run()}. The {@link #start(Application)} takes care of calling the correct entry point.
 * @author Garret Wilson
 */
public interface Application extends Runnable, Named<String>, Clogged {

	/** The classifier to use for determining a build information configuration resource for this class. */
	public static final String CONFIG_CLASSIFIER_BUILD = "build";

	/** The configuration key containing the version of the program. */
	public static final String CONFIG_KEY_NAME = "name";

	/** The configuration key containing the version of the program. */
	public static final String CONFIG_KEY_VERSION = "version";

	/** The optional configuration key containing the ISO 8601 build timestamp of the program. */
	public static final String CONFIG_KEY_BUILT_AT = "builtAt";

	/** The optional configuration key containing the copyright notice of the program. */
	public static final String CONFIG_KEY_COPYRIGHT = "copyright";

	/** Exit code indicating a successful termination. */
	public static final int EXIT_CODE_OK = 0;

	/**
	 * Loads build information for an application.
	 * @param applicationClass The application class providing the resource context for loading.
	 * @implSpec This implementation loads build-related information using Confound, stored in a configuration file with the same name as the concrete application
	 *           class with a {@value #CONFIG_CLASSIFIER_BUILD} classifier. Confound by default recognizes both <code>FooBarApp-build.properties</code> and
	 *           <code>FooBarApp-build.xml.properties</code> forms for loading the information from {@link Properties}, but other formats may be plugged in using
	 *           the Confound service provider mechanism. For example the following might be stored as <code>FooBarApp-build.properties</code>:<pre>
	 * {@code
	 * name=FooBar Application
	 * version=1.2.3
	 * }
	 * </pre>
	 * @return The loaded application build information, which will not be present if no appropriate configuration was found.
	 * @throws NullPointerException if the context class is <code>null</code>.
	 * @throws ConfigurationException If an I/O error occurs, or there is invalid data or invalid state preventing the configuration from being loaded.
	 * @see #CONFIG_CLASSIFIER_BUILD
	 */
	public static Optional<Configuration> loadBuildInfo(@NonNull final Class<?> applicationClass) throws ConfigurationException {
		try {
			return ResourcesConfigurationManager.loadConfigurationForClass(applicationClass, CONFIG_CLASSIFIER_BUILD);
		} catch(final IOException ioException) {
			throw new ConfigurationException(ioException);
		}
	}

	/**
	 * Exit code indicating execution failure.
	 * @see <a href="https://picocli.info/#_exception_exit_codes">picocli § 9.3. Exception Exit Codes</a>
	 * @see <a href="https://stackoverflow.com/a/40484670/421049">note on error codes in practice</a>
	 */
	public static final int EXIT_CODE_SOFTWARE = 1;

	/**
	 * Exit code indicating incorrect command-line usage.
	 * @see <a href="https://picocli.info/#_exception_exit_codes">picocli § 9.3. Exception Exit Codes</a>
	 * @see <a href="https://stackoverflow.com/a/40484670/421049">note on error codes in practice</a>
	 */
	public static final int EXIT_CODE_USAGE = 2;

	/** Pseudo exit code indicating that the application should not exit immediately, e.g. for a GUI or daemon application. */
	public static final int EXIT_CODE_CONTINUE = -1;

	/** An array containing no arguments. */
	public static final String[] NO_ARGUMENTS = new String[0];

	/**
	 * Retrieves the application authenticator.
	 * @implSpec The default implementation returns empty.
	 * @return The authenticator object, if any, used to retrieve client authentication.
	 */
	public default Optional<Authenticable> findAuthenticator() {
		return Optional.empty();
	}

	/**
	 * Retrieves the application arguments.
	 * @implSpec The default implementation returns no arguments.
	 * @return The command-line arguments of the application.
	 */
	public default String[] getArgs() {
		return NO_ARGUMENTS;
	}

	/**
	 * {@inheritDoc}
	 * @implSpec The default implementation returns the simple class name of the concrete application class.
	 */
	@Override
	public default String getName() {
		return getClass().getSimpleName();
	}

	/**
	 * Returns the application version.
	 * @return The application version string.
	 */
	public String getVersion();

	/**
	 * Returns a <dfn>slug</dfn> for the application: a single computer-consumable token used to identify the application, such as as a path segment or in a URL
	 * The slug should have no whitespace and ideally be in lowercase. It is recommended that a slug be in <code>kebab-case</code>. For example the "FooBar"
	 * application might use a slug of <code>foo-bar</code>.
	 * @implSpec The default implementation returns the <code>kebab-case</code> form of the application name. For example for an application named
	 *           <code>MyApp</code>, the default implementation would return <code>my-app</code>.
	 * @return A slug for the application.
	 * @see <a href="https://en.wikipedia.org/wiki/Clean_URL#Slug">Slug (web_publishing)</a>
	 * @see #getName()
	 */
	public default String getSlug() {
		return CAMEL_CASE.to(KEBAB_CASE, getName());
	}

	/**
	 * Returns the application user preferences.
	 * @implSpec The default implementation returns the user preferences node for the concrete subclass.
	 * @return The default user preferences for this application.
	 */
	public default Preferences getPreferences() {
		return Preferences.userNodeForPackage(getClass());
	}

	/**
	 * Returns whether debug mode is enabled.
	 * @implSpec The default implementation returns <code>false</code>.
	 * @apiNote Debug mode enables debug level logging and may also enable other debug functionality.
	 * @apiNote Thus <dfn>debug mode</dfn> refers to both a mode setting and a log level. Other settings may influence the log level while leaving debug mode on.
	 * @return The state of debug mode.
	 */
	public default boolean isDebug() {
		return false;
	}

	/**
	 * Retrieves the application expiration date.
	 * @implSpec The default implementation returns empty.
	 * @return The expiration date of the application, if there is one.
	 */
	public default Optional<LocalDate> findExpirationDate() {
		return Optional.empty();
	}

	/**
	 * Initializes the application. This method is called after construction but before application execution.
	 * @implSpec The default implementation does nothing.
	 * @throws IllegalStateException if the application has already been initialized.
	 * @throws Exception if anything goes wrong.
	 * @see #cleanup()
	 */
	public default void initialize() throws Exception {
	}

	/**
	 * Starts the application if it can be started.
	 * @implSpec The default implementation calls {@link #run()} and returns {@link #EXIT_CODE_OK}.
	 * @implNote Any implementation of this method should eventually delegate to {@link #run()}.
	 * @return The application status:
	 *         <dl>
	 *         <dt>{@value #EXIT_CODE_OK}</dt>
	 *         <dd>Success.</dd>
	 *         <dt>Any positive exit code.</dt>
	 *         <dd>There was an error and the application should exit.</dd>
	 *         <dt>{@value #EXIT_CODE_CONTINUE}</dt>
	 *         <dd>The application should not exit but continue running, such as for a GUI or daemon application.</dd>
	 *         </dl>
	 */
	public default int start() {
		run();
		return EXIT_CODE_OK;
	}

	/**
	 * Starts an application. If this method returns, the program is still running.
	 * <ol>
	 * <li>Calls {@link #initialize()}.</li>
	 * <li>Calls {@link #start()}, which eventually calls {@link #run()}. If a non-zero exit code is returned, the application will end. An exit code of
	 * <code>-1</code> indicates that the application should not exit after running.</li>
	 * <li>Normally {@link #start()} delegates to {@link #run()} for default functionality, but may delegate directly to other methods, e.g. representing CLI
	 * commands.</li>
	 * <li>If a positive exit code was given, calls {@link #end(int)} to exit the application. (A status of {@value #EXIT_CODE_CONTINUE} indicates that the
	 * program continues to run.)</li>
	 * </ol>
	 * @apiNote Except for GUI programs and daemons, this method will never return, as it will eventually call {@link #end(int)} which will immediately exit the
	 *          program. If the program chooses to continue running, it should call {@link #end(int)} at some point when it is ready to stop so that all needed
	 *          shutdown activities will occur.
	 * @param application The application to start.
	 */
	public static void start(@NonNull final Application application) {
		int result = EXIT_CODE_OK; //start out assuming a neutral result
		try {
			try {
				application.initialize(); //initialize the application
				result = application.start();
			} catch(final Throwable throwable) { //if there are any errors
				result = EXIT_CODE_SOFTWARE; //show that there was an error
				application.reportError("Error starting application.", throwable); //report the error TODO i18n
			}
		} finally {
			if(result >= 0) { //if we should not continue running (e.g. the application does not have a main frame showing or a daemon running)
				application.end(result);
			}
		}
	}

	/**
	 * Ends the application with a status of success.
	 * @implSpec The default implementation delegates to {@link #end(int)} with a value of {@value #EXIT_CODE_OK}.
	 * @implNote This method should eventually delegate to {@link #exit(int)}.
	 * @see #end(int)
	 */
	public default void end() {
		end(EXIT_CODE_OK); //exit with no status		
	}

	/**
	 * Requests to end the application with the given status. This method may perform checks to see if an application can truly end, such as checking for unsaved
	 * files. In other words this method may be canceled and not complete exiting.
	 * @apiNote This method normally will never return.
	 * @apiNote To add to exit functionality, {@link #exit(int)} should be overridden rather than this method.
	 * @apiNote This method explicitly does not accept {@value #EXIT_CODE_CONTINUE}, as continuing and ending are contradictory concepts.
	 * @implSpec The default implementation calls {@link #exit(int)}.
	 * @implNote This method should eventually delegate to {@link #exit(int)}.
	 * @param status The exit status, which must not be negative.
	 * @throws IllegalArgumentException if the given status is negative.
	 * @see #exit(int)
	 */
	public default void end(final int status) {
		checkArgumentNotNegative(status);
		exit(status); //perform the exit
	}

	/**
	 * Performs cleanup necessary for the application before final shutdown. Under normal circumstances this method is called immediately before final system
	 * exit. It is undefined what will happen if this method is called multiple times.
	 * <p>This method is only called during <em>normal shutdown</em> of the application. If the application is terminated in response to user input, such as by
	 * <code>Ctrl+C</code>, this method will not be called; any cleanup must be performed manually as needed.</p>
	 * @apiNote Normally this method performs the complementary operations to those performed in {@link #initialize()}, such as uninstalling something installed
	 *          during initialization.
	 * @implSpec The default version does nothing.
	 * @see #initialize()
	 */
	public default void cleanup() {
	}

	/**
	 * Exits the application immediately with the given status without checking to see if exit should be performed. This method calls {@link #cleanup()} and
	 * delegates to {@link System#exit(int)}. This method will always exit and never return, even if errors occur during cleaning.
	 * @apiNote This method will never return.
	 * @apiNote Normally this method is never called directly by the application. To end the application, calling {@link #end(int)} is preferred so that any end
	 *          checks may be called.
	 * @implSpec The default implementation calls {@link #cleanup()} and then delegates to {@link System#exit(int)}.
	 * @param status The exit status.
	 * @see #cleanup()
	 * @see System#exit(int)
	 */
	public default void exit(final int status) {
		try {
			cleanup();
		} catch(final Throwable throwable) {
			reportError("Error during application cleanup.", throwable); //report any errors; the application is exiting, so the errors cannot be propagated
		} finally {
			System.exit(status); //close the program with the given exit status		
		}
	}

	/**
	 * Reports an error condition to the user. A message will be added as appropriate.
	 * @implSpec The default version delegates to {@link #reportError(String, Throwable)} using the message determined by {@link #toErrorMessage(Throwable)}.
	 * @param throwable The condition that caused the error.
	 */
	public default void reportError(@NonNull final Throwable throwable) {
		reportError(toErrorMessage(throwable), throwable);
	}

	/**
	 * Reports an error message to the user related to an exception.
	 * @implSpec The default implementation calls {@link #reportError(String)} and then prints a stack trace to {@link System#err}.
	 * @param message The message to display.
	 * @param throwable The condition that caused the error.
	 * @see Throwable#printStackTrace(PrintStream)
	 */
	public default void reportError(@NonNull final String message, @NonNull final Throwable throwable) {
		reportError(message);
		throwable.printStackTrace(System.err);
	}

	/**
	 * Reports the given error message to the user
	 * @implSpec The default implementation writes the message to {@link System#err}.
	 * @param message The error to display.
	 */
	public default void reportError(@NonNull final String message) {
		System.err.println(message); //display the error in the error output
	}

	/**
	 * Constructs a user-presentable error message based on an exception.
	 * @implSpec The default version returns constructed messages for exceptions known not to contain useful information. In most cases it returns
	 *           {@link Throwable#getMessage()}.
	 * @param throwable The condition that caused the error.
	 * @return The error message.
	 * @see Throwable#getMessage()
	 */
	public default @NonNull String toErrorMessage(final Throwable throwable) {
		if(throwable instanceof FileNotFoundException) {
			return "File or directory not found: " + throwable.getMessage(); //TODO i18n
		} else if(throwable instanceof NoSuchFileException) {
			return "No such file or directory: " + throwable.getMessage(); //TODO i18n
		}
		final String message = throwable.getMessage();
		return message != null ? message : throwable.getClass().getName(); //if there is no message, return the simple class name
	}

}
