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
import java.nio.file.NoSuchFileException;
import java.time.LocalDate;
import java.util.*;
import java.util.prefs.Preferences;

import javax.annotation.*;

import com.globalmentor.net.*;

/**
 * An abstract implementation of an application that by default is a console application.
 * @implSpec Errors are written in simple form to {@link System#err}.
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
			reportError("This version of " + getName() + " has expired."); //TODO i18n; improve error handling (should probably report error elsewhere)
			return false;
		}
		return true; //show that everything went OK
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This method first calls {@link #canEnd()} to see if exit can occur.
	 * @implSpec If exit is allowed to occur, this method will exit even if there was an error in calling {@link #exit(int)}.
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
				reportError("Error exiting.", throwable); //report the error TODO i18n
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

	/**
	 * {@inheritDoc}
	 * @implSpec This version delegates to {@link #reportError(String, Throwable)} using the message determined by {@link #toErrorMessage(Throwable)}.
	 */
	@Override
	public void reportError(final Throwable throwable) {
		reportError(toErrorMessage(throwable), throwable);
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation calls {@link #reportError(String)} and then prints a stack trace to {@link System#err}.
	 * @see Throwable#printStackTrace(PrintStream)
	 */
	@Override
	public void reportError(@Nonnull final String message, @Nonnull final Throwable throwable) {
		reportError(message);
		throwable.printStackTrace(System.err);
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation writes the message to {@link System#err}.
	 */
	@Override
	public void reportError(final String message) {
		System.err.println(message); //display the error in the error output
	}

	/**
	 * Constructs a user-presentable error message based on an exception.
	 * @implSpec This version returns constructed messages for exceptions known not to contain useful information. In most cases it returns
	 *           {@link Throwable#getMessage()}.
	 * @param throwable The condition that caused the error.
	 * @return The error message.
	 * @see Throwable#getMessage()
	 */
	protected @Nonnull String toErrorMessage(final Throwable throwable) {
		if(throwable instanceof FileNotFoundException) {
			return "File or directory not found: " + throwable.getMessage(); //TODO i18n
		} else if(throwable instanceof NoSuchFileException) {
			return "No such file or directory: " + throwable.getMessage(); //TODO i18n
		}
		final String message = throwable.getMessage();
		return message != null ? message : throwable.getClass().getName(); //if there is no message, return the simple class name
	}

}
