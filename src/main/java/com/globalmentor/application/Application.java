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

import com.globalmentor.model.Named;
import com.globalmentor.net.*;

/**
 * A general application.
 * @author Garret Wilson
 */
public interface Application extends Resource, Named<String> {

	/** An array containing no arguments. */
	public static final String[] NO_ARGUMENTS = new String[0];

	/** @return The authenticator object used to retrieve client authentication. */
	public Optional<Authenticable> getAuthenticator();

	/** @return The command-line arguments of the application. */
	public String[] getArgs();

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
	 * The main application method.
	 * @return The application status.
	 */
	public int main();

	/**
	 * Checks requirements, permissions, and expirations before starting.
	 * @return <code>true</code> if the checks succeeded.
	 */
	public boolean canStart();

	/**
	 * Displays an error message to the user for an exception.
	 * @param throwable The condition that caused the error.
	 */
	@Deprecated
	public void displayError(final Throwable throwable);

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

}
