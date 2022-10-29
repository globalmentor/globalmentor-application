/*
 * Copyright © 1996-2019 GlobalMentor, Inc. <http://www.globalmentor.com/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.globalmentor.application;

import static java.lang.String.format;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.prefs.Preferences;

import javax.annotation.*;

import com.globalmentor.model.Named;
import com.globalmentor.net.*;

import io.clogr.Clogged;
import io.confound.config.Configuration;
import io.confound.config.ConfigurationException;
import io.confound.config.file.ResourcesConfigurationManager;

/**
 * A general application.
 * <p>
 * To start an application, call the static {@link #start(Application)} method, passing it an application instance.
 * </p>
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
	 * @return The loaded application build information.
	 * @throws NullPointerException if the context class is <code>null</code>.
	 * @throws ConfigurationException If an I/O error occurs, or there is invalid data or invalid state preventing the configuration from being loaded.
	 * @see #CONFIG_CLASSIFIER_BUILD
	 */
	public static Configuration loadBuildInfo(@Nonnull final Class<?> applicationClass) throws ConfigurationException {
		try {
			return ResourcesConfigurationManager.loadConfigurationForClass(applicationClass, CONFIG_CLASSIFIER_BUILD)
					.orElseThrow(() -> new ConfigurationException(format("No build information found for class %s.", applicationClass.getName())));
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

	/** @return The authenticator object used to retrieve client authentication. */
	public Optional<Authenticable> getAuthenticator();

	/** @return The command-line arguments of the application. */
	public String[] getArgs();

	/** @return The application version string. */
	public String getVersion();

	/**
	 * Returns whether debug mode is enabled.
	 * @apiNote Debug mode enables debug level logging and may also enable other debug functionality.
	 * @apiNote Thus <dfn>debug mode</dfn> refers to both a mode setting and a log level. Other settings may influence the log level while leaving debug mode on.
	 * @return The state of debug mode.
	 */
	public boolean isDebug();

	/**
	 * Returns the application user preferences.
	 * @return The default user preferences for this application.
	 * @throws SecurityException if a security manager is present and it denies <code>RuntimePermission("preferences")</code>.
	 */
	public Preferences getPreferences() throws SecurityException;

	/** @return The expiration date of the application, if there is one. */
	public Optional<LocalDate> getExpirationDate();

	/**
	 * Initializes the application. This method is called after construction but before application execution.
	 * @throws IllegalStateException if the application has already been initialized.
	 * @throws Exception if anything goes wrong.
	 * @see #cleanup()
	 */
	public void initialize() throws Exception;

	/**
	 * Starts the application if it can be started.
	 * @implNote This method should eventually delegate to {@link #run()}.
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
	public int start();

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
	public static void start(@Nonnull final Application application) {
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
	 * Ends the application with the given status. This method first checks to see if the program can end. If the status is not {@value #EXIT_CODE_OK}, the
	 * application will then exit immediately.
	 * @apiNote This method normally will never return.
	 * @apiNote To add to exit functionality, {@link #exit(int)} should be overridden rather than this method.
	 * @apiNote This method explicitly does not accept {@value #EXIT_CODE_CONTINUE}, as continuing and ending contradictory concepts.
	 * @implNote This method should eventually delegate to {@link #exit(int)}.
	 * @param status The exit status, which must not be negative.
	 * @see #exit(int)
	 * @throws IllegalArgumentException if the given status is negative.
	 */
	public void end(@Nonnegative final int status);

	/**
	 * Performs cleanup necessary for the application before final shutdown. Under normal circumstances this method is called immediately before final system
	 * exit. It is undefined what will happen if this method is called multiple times.
	 * <p>
	 * This method is only called during <em>normal shutdown</em> of the application. If the application is terminated in response to user input, such as by
	 * <code>Ctrl+C</code>, this method will not be called; any cleanup must be performed manually as needed.
	 * </p>
	 * @apiNote Normally this method performs the complementary operations to those performed in {@link #initialize()}, such as uninstalling something installed
	 *          during initialization.
	 * @implSpec The default version does nothing.
	 * @see #initialize()
	 */
	public default void cleanup() {
	}

	/**
	 * Exits the application immediately with the given status without checking to see if exit should be performed. This method calls {@link #cleanup()} and
	 * delegates to {@link System#exit(int)}.
	 * @apiNote This method normally will never return.
	 * @apiNote Normally this method is never called directly by the application. To end the application, calling {@link #end(int)} is preferred so that cleanup
	 *          can occur.
	 * @param status The exit status.
	 * @throws SecurityException if a security manager exists and its {@link SecurityManager#checkExit(int)} method doesn't allow exit with the specified status.
	 * @see #cleanup()
	 * @see System#exit(int)
	 */
	public void exit(final int status);

	/**
	 * Reports an error condition to the user. A message will be added as appropriate.
	 * @param throwable The condition that caused the error.
	 */
	public void reportError(@Nonnull final Throwable throwable);

	/**
	 * Reports an error message to the user related to an exception.
	 * @param message The message to display.
	 * @param throwable The condition that caused the error.
	 */
	public void reportError(@Nonnull final String message, @Nonnull final Throwable throwable);

	/**
	 * Reports the given error message to the user
	 * @param message The error to display.
	 */
	public void reportError(@Nonnull final String message);

}
