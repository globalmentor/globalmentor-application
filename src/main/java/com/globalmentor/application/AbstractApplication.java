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

import static java.util.Objects.*;

import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.prefs.Preferences;

import javax.annotation.*;

import com.globalmentor.net.*;

import io.clogr.Clogged;

/**
 * An abstract implementation of an application that by default is a console application.
 * @implSpec The default preference node is based upon the implementing application class.
 * @author Garret Wilson
 */
public abstract class AbstractApplication implements Application, Clogged {

	private final String name;

	@Override
	public String getName() {
		return name;
	}

	/** The authenticator object used to retrieve client authentication, or <code>null</code> if there is no authenticator. */
	private Authenticable authenticator = null;

	@Override
	public Optional<Authenticable> getAuthenticator() {
		return Optional.ofNullable(authenticator);
	}

	/**
	 * Sets the authenticator object used to retrieve client authentication.
	 * @param authenticable The object to retrieve authentication information regarding a client.
	 */
	protected void setAuthenticator(@Nullable final Authenticable authenticable) {
		if(authenticator != authenticable) { //if the authenticator is really changing
			authenticator = authenticable; //update the authenticator
			//TODO do we need to set the network authenticator in some general way? HTTPClient.getInstance().setAuthenticator(authenticable); //update the authenticator for HTTP connections on the default HTTP client			
		}
	}

	/** The command-line arguments of the application. */
	private final String[] args;

	/** @return The command-line arguments of the application. */
	public String[] getArgs() {
		return args;
	}

	@Override
	public Preferences getPreferences() throws SecurityException {
		return Preferences.userNodeForPackage(getClass()); //return the user preferences node for whatever class extends this one 
	}

	/** The expiration date of the application, or <code>null</code> if there is no expiration. */
	private LocalDate expirationDate = null;

	@Override
	public Optional<LocalDate> getExpirationDate() {
		return Optional.ofNullable(expirationDate);
	}

	/**
	 * Sets the expiration date of the application.
	 * @param newExpirationDate The new expiration date, or <code>null</code> if there is no expiration.
	 */
	protected void setExpiration(@Nullable final LocalDate newExpirationDate) {
		expirationDate = newExpirationDate;
	}

	/**
	 * Name constructor.
	 * @param name The name of the application.
	 */
	public AbstractApplication(@Nonnull final String name) {
		this(name, NO_ARGUMENTS);
	}

	/**
	 * Name arguments constructor.
	 * @param name The name of the application.
	 * @param args The command line arguments.
	 */
	public AbstractApplication(@Nonnull final String name, @Nonnull final String[] args) {
		this.name = requireNonNull(name);
		this.args = requireNonNull(args);
	}

	/**
	 * Initializes the application. This method is called after construction but before application execution. This version does nothing.
	 * @throws Exception Thrown if anything goes wrong.
	 */
	public void initialize() throws Exception { //TODO create a flag that only allows initialization once
	}

	/**
	 * Checks requirements, permissions, and expirations before starting.
	 * @return <code>true</code> if the checks succeeded.
	 */
	public boolean canStart() {
		final boolean isExpired = getExpirationDate().map(expirationDate -> !LocalDate.now().isAfter(expirationDate)).orElse(false);
		if(isExpired) {
			displayError("This version of " + getName() + " has expired."); //TODO i18n
			return false;
		}
		return true; //show that everything went OK
	}

	/**
	 * Displays an error message to the user for an exception.
	 * @param message The message to display.
	 * @param throwable The condition that caused the error.
	 */
	public void displayError(@Nonnull final String message, @Nonnull final Throwable throwable) {
		getLogger().error(message, throwable);
		displayError(getDisplayErrorMessage(throwable)); //display an error to the user for the throwable
	}

	/**
	 * Displays the given error to the user
	 * @param message The error to display.
	 */
	public void displayError(final String message) {
		System.err.println(message); //display the error in the error output
	}

	/**
	 * Constructs a user-presentable error message based on an exception. In most cases this is {@link Throwable#getMessage()}.
	 * @param throwable The condition that caused the error.
	 * @return The error message.
	 * @see Throwable#getMessage()
	 */
	protected static String getDisplayErrorMessage(final Throwable throwable) {
		if(throwable instanceof FileNotFoundException) { //if a file was not found
			return "File not found: " + throwable.getMessage(); //create a message for a file not found TODO i18n
		} else { //for any another error
			return throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getName(); //get the throwable message or, on last resource, the name of the class
		}
	}

	/**
	 * Starts an application.
	 * @param application The application to start.
	 * @param args The command line arguments.
	 * @return The application status.
	 */
	public static int run(final Application application, final String[] args) {
		int result = 0; //start out assuming a neutral result TODO use a constant and a unique value
		try {
			initialize(application, args); //initialize the environment
			application.initialize(); //initialize the application
			if(application.canStart()) { //perform the pre-run checks; if everything went OK
				result = application.main(); //run the application
			} else { //if something went wrong
				result = -1; //show that we couldn't start TODO use a constant and a unique value
			}
		} catch(final Throwable throwable) { //if there are any errors
			result = -1; //show that there was an error TODO use a constant and a unique value
			application.displayError(throwable); //report the error
		}
		if(result < 0) { //if we something went wrong, exit (if everything is going fine, keep running, because we may have a server or frame running)
			try {
				application.exit(result); //exit with the result (we can't just return, because the main frame, if initialized, will probably keep the thread from stopping)
			} catch(final Throwable throwable) { //if there are any errors
				result = -1; //show that there was an error during exit TODO use a constant and a unique value
				application.displayError(throwable); //report the error
			} finally {
				System.exit(result); //provide a fail-safe way to exit		
			}
		}
		return result; //always return the result		
	}

	/**
	 * Initializes the environment for the application.
	 * @param application The application to start.
	 * @param args The command line arguments.
	 * @throws Exception Thrown if anything goes wrong.
	 */
	protected static void initialize(final Application application, final String[] args) throws Exception {
		CommandLineArguments.configureLog(args); //configure debugging based upon the command line arguments
	}

	/**
	 * Determines whether the application can exit. This method may query the user. If the application has been modified, the configuration is saved if possible.
	 * @return <code>true</code> if the application can exit, else <code>false</code>.
	 */
	protected boolean canExit() {
		return true; //show that we can exit
	}

	/**
	 * Exits the application with the given status. This method first checks to see if exit can occur.
	 * @apiNote To add to exit functionality, {@link #performExit(int)} should be overridden rather than this method.
	 * @param status The exit status.
	 * @see #canExit()
	 * @see #performExit(int)
	 */
	public final void exit(final int status) {
		if(canExit()) { //if we can exit
			try {
				performExit(status); //perform the exit
			} catch(final Throwable throwable) { //if there are any errors
				displayError(throwable); //report the error
			}
			System.exit(-1); //provide a fail-safe way to exit, indicating an error occurred		
		}
	}

	/**
	 * Exits the application with the given status without checking to see if exit should be performed.
	 * @param status The exit status.
	 * @throws Exception Thrown if anything goes wrong.
	 */
	protected void performExit(final int status) throws Exception {
		System.exit(status); //close the program with the given exit status		
	}

}
