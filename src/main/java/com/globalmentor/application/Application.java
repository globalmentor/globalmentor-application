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

import java.time.LocalDate;
import java.util.*;
import java.util.prefs.Preferences;

import javax.annotation.*;

import org.fusesource.jansi.AnsiConsole;

import com.globalmentor.model.Named;
import com.globalmentor.net.*;

import io.clogr.Clogged;

/**
 * A general application.
 * <p>
 * To start an application, call the static {@link #start(Application)} method, passing it an application instance.
 * </p>
 * @apiNote Although an application implements {@link Runnable}, it should usually be started using {@link #start()}, which will eventually (depending on the
 *          implementation) call {@link #run()}.
 * @author Garret Wilson
 */
public interface Application extends Runnable, Named<String>, Clogged {

	/** An array containing no arguments. */
	public static final String[] NO_ARGUMENTS = new String[0];

	/** @return The authenticator object used to retrieve client authentication. */
	public Optional<Authenticable> getAuthenticator();

	/** @return The command-line arguments of the application. */
	public String[] getArgs();

	/** @return The application version string . */
	public String getVersion();

	/**
	 * Returns whether debug mode is enabled.
	 * @apiNote Debug mode enables debug level logging and may also enable other debug functionality.
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
	 * @throws Exception if anything goes wrong.
	 */
	public void initialize() throws Exception;

	/**
	 * Starts the application
	 * @implSpec The default implementation delegates to {@link #run()} and returns a status code of <code>0</code>.
	 * @return The application status.
	 */
	public default int start() {
		run();
		return 0;
	}

	/**
	 * Checks requirements, permissions, and expirations before starting.
	 * @return <code>true</code> if the checks succeeded.
	 */
	public boolean canStart();

	/**
	 * Displays an error message to the user for an exception.
	 * @param message The message to display.
	 * @param throwable The condition that caused the error.
	 */
	@Deprecated
	public void displayError(@Nonnull final String message, @Nonnull final Throwable throwable);

	/**
	 * Displays the given error to the user
	 * @param message The error to display.
	 */
	@Deprecated
	public void displayError(final String message);

	/**
	 * Exits the application with no status.
	 * @implSpec The default implementation delegates to {@link #exit(int)} with a value of <code>0</code>.
	 * @see #exit(int)
	 */
	public default void exit() {
		exit(0); //exit with no status		
	}

	/**
	 * Exits the application with the given status. This method first checks to see if exit can occur.
	 * @param status The exit status.
	 */
	public void exit(final int status);

	/**
	 * Starts an application.
	 * @param application The application to start.
	 * @return The application status.
	 */
	public static int start(final Application application) {
		AnsiConsole.systemInstall();
		try {
			int result = 0; //start out assuming a neutral result TODO use a constant and a unique value
			try {
				application.initialize(); //initialize the application
				if(application.canStart()) { //perform the pre-run checks; if everything went OK
					result = application.start(); //run the application
				} else { //if something went wrong
					result = -1; //show that we couldn't start TODO use a constant and a unique value
				}
			} catch(final Throwable throwable) { //if there are any errors
				result = -1; //show that there was an error TODO use a constant and a unique value
				application.displayError("Error starting application.", throwable); //report the error TODO i18n
			}
			if(result < 0) { //if we something went wrong, exit (if everything is going fine, keep running, because we may have a server or frame running)
				try {
					application.exit(result); //exit with the result (we can't just return, because the main frame, if initialized, will probably keep the thread from stopping)
				} catch(final Throwable throwable) { //if there are any errors
					result = -1; //show that there was an error during exit TODO use a constant and a unique value
					application.displayError("Error exiting application.", throwable); //report the error TODO i18n
				} finally {
					System.exit(result); //provide a fail-safe way to exit		
				}
			}
			return result; //always return the result
		} finally {
			AnsiConsole.systemUninstall(); //TODO fix for daemons and GUIs, which remain running after started; maybe put this in some sort of dedicated exit method
		}
	}

}
