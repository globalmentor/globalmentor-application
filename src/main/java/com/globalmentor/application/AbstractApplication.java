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

import static com.globalmentor.java.Conditions.*;
import static java.util.Objects.*;

import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.prefs.Preferences;

import javax.annotation.*;

import com.globalmentor.net.*;

/**
 * An abstract implementation of an application that by default is a console application.
 * @implSpec The default preference node is based upon the implementing application class.
 * @author Garret Wilson
 */
public abstract class AbstractApplication implements Application {

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

	@Override
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
	protected void setExpirationDate(@Nullable final LocalDate newExpirationDate) {
		expirationDate = newExpirationDate;
	}

	/** No-arguments constructor. */
	public AbstractApplication() {
		this(NO_ARGUMENTS);
	}

	/**
	 * Arguments constructor.
	 * @param args The command line arguments.
	 */
	public AbstractApplication(@Nonnull final String[] args) {
		this.args = requireNonNull(args);
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This version does nothing.
	 */
	@Override
	public void initialize() throws Exception { //TODO create a flag that only allows initialization once
	}

	/**
	 * {@inheritDoc}
	 * @apiNote To change how the main application is executed, normally {@link #execute()} should be overridden and not this method.
	 * @implSpec The default implementation delegates calls {@link #canStart()} and, if it returns <code>true</code>, delegates to {@link #execute()}.
	 * @see #canStart()
	 * @see #execute()
	 */
	@Override
	public int start() {
		return canStart() ? execute() : EXIT_CODE_SOFTWARE;
	}

	/**
	 * Main execution implementation.
	 * @implSpec The default implementation delegates to {@link #run()} and returns a status code of {@value #EXIT_CODE_OK}.
	 * @implNote Normally this method delegates to {@link #run()} for default functionality, but may delegate directly to other methods, e.g. representing CLI
	 *           commands.
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
	protected int execute() {
		run();
		return EXIT_CODE_OK;
	}

	/**
	 * Checks requirements, permissions, and expirations before starting.
	 * @return <code>true</code> if the checks succeeded.
	 */
	protected boolean canStart() {
		final boolean isExpired = getExpirationDate().map(expirationDate -> !LocalDate.now().isAfter(expirationDate)).orElse(false);
		if(isExpired) {
			displayError("This version of " + getName() + " has expired."); //TODO i18n
			return false;
		}
		return true; //show that everything went OK
	}

	@Override
	public void displayError(@Nonnull final String message, @Nonnull final Throwable throwable) {
		getLogger().error(message, throwable);
		displayError(getDisplayErrorMessage(throwable)); //display an error to the user for the throwable
	}

	@Override
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
	 * {@inheritDoc}
	 * @implSpec This method first calls {@link #canEnd()} to see if exit can occur.
	 * @param status The exit status.
	 * @see #canEnd()
	 * @see #exit(int)
	 */
	public final void end(final int status) {
		checkArgumentNotNegative(status);
		if(canEnd()) { //if we can exit
			try {
				exit(status); //perform the exit
			} catch(final Throwable throwable) { //if there are any errors
				displayError("Error exiting.", throwable); //report the error TODO i18n
			}
			System.exit(-1); //provide a fail-safe way to exit, indicating an error occurred		
		}
	}

	/**
	 * Determines whether the application can end. This method may query the user. If the application has been modified, the configuration is saved if possible.
	 * @return <code>true</code> if the application can end, else <code>false</code>.
	 */
	protected boolean canEnd() {
		return true; //show that we can exit
	}

}
