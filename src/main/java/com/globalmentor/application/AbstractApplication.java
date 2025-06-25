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
import static io.confound.Confound.*;
import static io.confound.config.file.FileSystemConfigurationManager.*;
import static java.lang.String.format;
import static java.nio.file.Files.*;
import static java.util.Objects.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.*;

import com.globalmentor.net.*;

import io.confound.config.*;

/**
 * An abstract implementation of an application that by default is a console application.
 * @implSpec Errors are written in simple form to {@link System#err}.
 * @implSpec The default preference node is based upon the implementing application class.
 * @author Garret Wilson
 */
public abstract class AbstractApplication implements Application {

	private volatile Configuration buildInfo = null;

	/**
	 * Retrieves build information for an application.
	 * @implSpec This implementation delegates to {@link Application#loadBuildInfo(Class)} passing the current application class. The build information is loaded
	 *           once and cached. If no build information is available, a warning is logged and placeholder information is generated and returned.
	 * @return The loaded application build information.
	 * @throws ConfigurationException If an I/O error occurs, or there is invalid data or invalid state preventing the configuration from being loaded.
	 * @see Application#loadBuildInfo(Class)
	 */
	protected Configuration getBuildInfo() throws ConfigurationException {
		if(buildInfo == null) { //the race condition here is benign; the first time this is called is probably within a single thread anyway
			buildInfo = Application.loadBuildInfo(getClass()).orElseGet(() -> {
				final String applicationClassName = getClass().getSimpleName();
				getLogger().warn(format("Application %s missing build information.", applicationClassName));
				return new ObjectMapConfiguration(Map.of(CONFIG_KEY_NAME, applicationClassName, CONFIG_KEY_VERSION, "0.0.0+unknown"));
			});
		}
		return buildInfo;
	}

	/** The authenticator object used to retrieve client authentication, or <code>null</code> if there is no authenticator. */
	private Authenticable authenticator = null;

	@Override
	public Optional<Authenticable> findAuthenticator() {
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

	/** The expiration date of the application, or <code>null</code> if there is no expiration. */
	private LocalDate expirationDate = null;

	@Override
	public Optional<LocalDate> findExpirationDate() {
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

	private volatile Configuration config = EmptyConfiguration.INSTANCE; //updated during application initialization

	/**
	 * Returns the application configuration information. If the application is not yet initialized, the configuration may be empty.
	 * @return The application configuration information.
	 */
	public Configuration getConfig() {
		return config;
	}

	private AtomicBoolean initialized = new AtomicBoolean(false);

	/**
	 * Indicates whether the application has been initialized.
	 * @return <code>true</code> if the application has been initialized.
	 */
	protected boolean isInitialized() {
		return initialized.get();
	}

	/**
	 * {@inheritDoc}
	 * @apiNote Before overriding this method, see if one of the finer-grained initialization methods such as {@link #initializeApplication()} would be more
	 *          appropriate.
	 * @implSpec Any overridden version must first call this version.
	 * @implSpec This version calls the following other methods in this order:
	 *           <ol>
	 *           <li>{@link #initializeSystem()}</li>
	 *           <li>{@link #initializeApplication()}</li>
	 *           </ol>
	 * @throws IllegalStateException if the application has already been initialized (e.g. if this method is called multiple times).
	 */
	@Override
	public void initialize() throws Exception {
		if(initialized.getAndSet(true)) {
			throw new IllegalStateException("Application already initialized.");
		}
		initializeSystem();
		initializeApplication();
	}

	/**
	 * Initializes things related to the system on which the application will be running.
	 * @implSpec This version sets up the shutdown hook.
	 * @implSpec Any overridden version must first call this version.
	 * @throws Exception if anything goes wrong.
	 * @see Runtime#addShutdownHook(Thread)
	 * @see #cleanupSystem()
	 */
	protected void initializeSystem() throws Exception {
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}

	/**
	 * Initializes the application itself. {@link #initializeSystem()} will already have been called.
	 * @implSpec This implementation loads the application configuration using {@link #loadConfiguration()}.
	 * @throws Exception if anything goes wrong.
	 * @see #cleanupApplication()
	 */
	protected void initializeApplication() throws Exception {
		config = loadConfiguration();
	}

	/**
	 * Loads the global configuration information for the application.
	 * @implSpec This implementation loads the application configuration from {@link #getGlobalConfigDirectory()}, if that directory exists and contains an
	 *           appropriate config file. The configuration file is expected to have a base name of the application slug from {@link #getSlug()}, with an
	 *           appropriate extension corresponding to a supported configuration file. For example a configuration file for an application with a slug
	 *           <code>my-app</code> might be stored in <code>~/.my-app/my-app.properties</code>.
	 * @implNote Supported configuration files are governed by {@link io.confound.Confound} and its installed configuration file format providers.
	 * @return The global configuration information, if found and loaded successfully.
	 * @throws IOException if there was an I/O error loading the configuration.
	 * @see #getGlobalConfigDirectory()
	 * @see #getSlug()
	 * @see #getConfig()
	 */
	protected Optional<Configuration> loadFoundGlobalConfiguration() throws IOException {
		final Path globalConfigDirectory = getGlobalConfigDirectory();
		if(isDirectory(globalConfigDirectory)) { //TODO remove check when CONFOUND-35 is fixed
			final String globalConfigBaseFilename = getSlug(); //e.g. `my-app`
			return loadConfigurationForBaseFilename(globalConfigDirectory, globalConfigBaseFilename);
		}
		return Optional.empty();
	}

	/**
	 * Discovers and loads configuration information for the application.
	 * @implSpec This implementation loads the global configuration if any using {@link #loadFoundGlobalConfiguration()}, and then adds a configurations with
	 *           higher priority for environment variables and system properties.
	 * @return The configuration information, if found and loaded successfully.
	 * @throws IOException if there was an I/O error loading the configuration.
	 * @see #loadFoundGlobalConfiguration()
	 */
	protected Configuration loadConfiguration() throws IOException {
		return getSystemConfiguration(loadFoundGlobalConfiguration().orElse(null)); //return the loaded global configuration, with environment variables and system properties overriding 
	}

	/**
	 * {@inheritDoc}
	 * @apiNote Before overriding this method, see if one of the finer-grained cleanup methods such as {@link #cleanupApplication()} would be more appropriate.
	 * @implSpec Any overridden version must afterward call this version.
	 * @implSpec This version calls the following other methods in this order:
	 *           <ol>
	 *           <li>{@link #cleanupApplication()}</li>
	 *           <li>{@link #cleanupSystem()}</li>
	 *           </ol>
	 * @throws IllegalStateException if the application has already been initialized (e.g. if this method is called multiple times).
	 */
	@Override
	public void cleanup() {
		cleanupApplication();
		cleanupSystem();
	}

	/**
	 * Cleans up things related to the system on which the application was running.
	 * @see #initializeSystem()
	 */
	protected void cleanupSystem() {
	}

	/**
	 * Cleans up the application itself.
	 * @see #initializeApplication()
	 */
	protected void cleanupApplication() {
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
		final boolean isExpired = findExpirationDate().map(expirationDate -> !LocalDate.now().isAfter(expirationDate)).orElse(false);
		if(isExpired) {
			reportError("This version of " + getName() + " has expired."); //TODO i18n; improve error handling (should probably report error elsewhere)
			return false;
		}
		return true; //show that everything went OK
	}

	@Nullable
	private volatile Duration shutdownDelay = null;

	/**
	 * Returns the shutdown delay, if any.
	 * @return The delay before shutting down after shutdown is initiated, either by normal exiting or by user-initiated termination such as <code>Ctrl+C</code>;
	 *         or empty if no shutdown delay has been configured.
	 */
	protected Optional<Duration> findShutdownDelay() {
		return Optional.ofNullable(shutdownDelay);
	}

	/**
	 * Sets the delay before shutting down. This delay is performed <em>after</em> {@link #onShutdown()} is called.
	 * @param shutdownDelay The delay before shutdown.
	 */
	protected void setShutdownDelay(@Nonnull final Duration shutdownDelay) {
		this.shutdownDelay = requireNonNull(shutdownDelay);
	}

	private volatile boolean shuttingDown = false;

	/**
	 * Indicates whether the application is currently shutting down. Once this flag reports <code>true</code>, it will never revert to reporting
	 * <code>false</code> because once started the shutdown process cannot be stopped.
	 * @return <code>true</code> if the application has started the shutdown sequence.
	 */
	protected boolean isShuttingDown() {
		return shuttingDown;
	}

	/**
	 * Shutdown hook installed during initialization.
	 * @implSpec This hook sets the shutting-down flag and calls {@link #onShutdown()}. It also performs any required delay.
	 * @see #findShutdownDelay()
	 */
	@SuppressWarnings("this-escape") //`this` is not used until shutdown
	private final Thread shutdownHook = new Thread(() -> {
		shuttingDown = true;
		try {
			onShutdown();
		} finally {
			findShutdownDelay().ifPresent(delay -> {
				try {
					Thread.sleep(delay.toMillis());
				} catch(final InterruptedException interruptedException) {
					//no point in notifying of interruption during shutdown delay; it could be user-initiated if anything
				}
			});
		}
	});

	/**
	 * Called when the virtual machine is beginning the shutdown process.
	 * @apiNote This method is called both during normal shutdown then the application has already ended normally, and when shutdown is forced such as a user
	 *          pressing <code>Ctrl+C</code> in the terminal. As this method will be called at "a delicate time in the life cycle of a virtual machine" as per the
	 *          documentation to {@link Runtime#addShutdownHook(Thread)}, any implementation should execute quickly, be thread-safe, and not rely on services or
	 *          user interaction.
	 * @implSpec The default version does nothing.
	 */
	protected void onShutdown() {
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This method first calls {@link #canEnd()} to see if exit can occur.
	 * @param status The exit status.
	 * @see #canEnd()
	 */
	@Override
	public final void end(final int status) {
		checkArgumentNotNegative(status);
		if(canEnd()) { //if we can exit
			Application.super.end(status); //exit in the default manner
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
	 * Called just before the application finally exits. When this method is called, {@link #cleanup()} is guaranteed to have been called (although there is no
	 * guarantee that it completed successfully). Once this method returns, successfully or unsuccessfully, the application will exit.
	 * @implSpec The default version does nothing.
	 */
	protected void onExit() {
	}

	/**
	 * {@inheritDoc}
	 * @apiNote This version normally should not be overridden. Any overridden version must either call this version or ensure that it meets the contractual
	 *          requirements of this method.
	 */
	@Override
	public void exit(final int status) {
		try {
			cleanup();
		} catch(final Throwable throwable) {
			System.err.println("Error during application cleanup."); //advise of any errors; otherwise the system will exit and they will be lost
			throwable.printStackTrace(System.err);
		} finally {
			try {
				onExit(); //any errors from onExit() will likely be lost
			} finally {
				System.exit(status); //close the program with the given exit status		
			}
		}
	}

}
