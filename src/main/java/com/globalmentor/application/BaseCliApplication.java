/*
 * Copyright © 2019 GlobalMentor, Inc. <http://www.globalmentor.com/>
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

import javax.annotation.*;

import org.fusesource.jansi.AnsiConsole;
import org.slf4j.event.Level;

import io.clogr.*;
import io.confound.config.ConfigurationException;
import io.confound.config.file.ResourcesConfigurationManager;
import picocli.CommandLine;
import picocli.CommandLine.*;

/**
 * Base implementation for facilitating creation of a CLI application.
 * <p>
 * A concrete application class should create a static <code>MetadataProvider</code> class (an inner class is recommended) extending the abstract metadata
 * provider class, specifying the class of the concrete application itself. This class expects a configuration file with the same name as the concrete
 * application class with a base extension of <code>-config</code>, such as <code>ExampleApp-config.properties</code>, loaded via Confound from the resources in
 * the same path as the application class. For example:
 * </p>
 * 
 * <pre>
 * {@code
 * name=${project.name}
 * version=${project.version}
 * }
 * </pre>
 * <p>
 * The provider class should be specified as the version provider, e.g.:
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;Command(name = "foobar", description = "FooBar application.", versionProvider = MetadataProvider.class, mixinStandardHelpOptions = true)
 * }
 * </pre>
 * 
 * <p>
 * By default this class merely prints the command-line usage. This can be overridden for programs with specific functionality, but if the application requires
 * a command then the command methods can be added and annotated separately, with the default {@link #run()} method remaining for displaying an explanation.
 * </p>
 * 
 * <p>
 * This class sets up the following options:
 * </p>
 * <dl>
 * <dt><code>--debug</code>, <code>-d</code></dt>
 * <dd>Turns on debug level logging.</dd>
 * </dl>
 * @implSpec This implementation adds ANSI support via Jansi.
 * @author Garret Wilson
 */
//@Command(name = "foobar", description = "FooBar application.", versionProvider = MetadataProvider.class, mixinStandardHelpOptions = true)
public abstract class BaseCliApplication extends AbstractApplication {

	/** The configuration key containing the version of the program. */
	public static final String CONFIG_KEY_NAME = "name";

	/** The configuration key containing the version of the program. */
	public static final String CONFIG_KEY_VERSION = "version";

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation retrieves the name from resources for the concrete application class using the resource key {@value #CONFIG_KEY_NAME}.
	 * @see #CONFIG_KEY_NAME
	 * @throws ConfigurationException if there was an error retrieving the configured name or the name could not be found.
	 */
	@Override
	public String getName() {
		try {
			return ResourcesConfigurationManager.loadConfigurationForClass(getClass())
					.orElseThrow(ResourcesConfigurationManager::createConfigurationNotFoundException).getString(CONFIG_KEY_NAME);
		} catch(final IOException ioException) {
			throw new ConfigurationException(ioException);
		}
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation retrieves the name from resources for the concrete application class using the resource key {@value #CONFIG_KEY_VERSION}.
	 * @see #CONFIG_KEY_VERSION
	 * @throws ConfigurationException if there was an error retrieving the configured name or the name could not be found.
	 */
	@Override
	public String getVersion() {
		try {
			return ResourcesConfigurationManager.loadConfigurationForClass(getClass())
					.orElseThrow(ResourcesConfigurationManager::createConfigurationNotFoundException).getString(CONFIG_KEY_VERSION);
		} catch(final IOException ioException) {
			throw new ConfigurationException(ioException);
		}
	}

	private boolean debug;

	@Override
	public boolean isDebug() {
		return debug;
	}

	/**
	 * Enables or disables debug mode, which is disabled by default.
	 * @param debug The new state of debug mode.
	 */
	@Option(names = {"--debug", "-d"}, description = "Turns on debug level logging.")
	protected void setDebug(final boolean debug) {
		this.debug = debug;
		updateLogLevel();
	}

	/** Updates the log level based upon the current debug setting. The current debug setting remains unchanged. */
	protected void updateLogLevel() {
		final Level logLevel = debug ? Level.DEBUG : Level.WARN; //TODO default to INFO level when we provide a log output (e.g. to file) option

		/*TODO determine additional logging configuration, including explicit log level requested, and log file requested; code from legacy CommandLineArguments
		Log.Level logLevel = getOption(arguments, Switch.LOG_LEVEL, Log.Level.class); //get the explicit log level, if any
		if(logLevel == null) { //if no specific log level was specified, see if a verbosity level was specified
			if(hasFlag(arguments, Switch.VERBOSE)) {
				logLevel = Log.Level.DEBUG;
			} else if(hasFlag(arguments, Switch.QUIET)) {
				logLevel = Log.Level.WARN;
			} else {
				logLevel = Log.Level.INFO;
			}
		}
		final String logFileOption = getOption(arguments, Switch.LOG_FILE); //get the log file, if any
		final File logFile = logFileOption != null ? new File(logFileOption) : null;
		Log.setDefaultConfiguration(new DefaultLogConfiguration(logFile, logLevel)); //set the default log configuration
		*/

		Clogr.getLoggingConcern().setLogLevel(logLevel);
	}

	/**
	 * Arguments constructor.
	 * @param args The command line arguments.
	 */
	public BaseCliApplication(@Nonnull final String[] args) {
		super(args);
		updateLogLevel(); //update the log level based upon the debug setting
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation calls {@link AnsiConsole#systemInstall()}.
	 */
	@Override
	public void initialize() throws Exception {
		super.initialize();
		AnsiConsole.systemInstall();
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation uses picocli to execute the application using {@link CommandLine#execute(String...)}.
	 */
	@Override
	protected int execute() {
		return new CommandLine(this).execute(getArgs()); //run the application via picocli instead of using the default version
	}

	/**
	 * {@inheritDoc}
	 * @implSpec The default implementation prints the command-line usage.
	 * @implNote This can be overridden for programs with specific functionality, but if the application requires a command then the command methods can be added
	 *           and annotated separately, with the default {@link #run()} method remaining for displaying an explanation.
	 */
	@Override
	public void run() {
		CommandLine.usage(this, System.out);
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation calls {@link AnsiConsole#systemUninstall()}.
	 */
	@Override
	public void exit(int status) {
		AnsiConsole.systemUninstall();
		super.exit(status);
	}

	/**
	 * Strategy for retrieving the application name and version from the configuration. Each application should extend this class and pass it the concrete
	 * application class in the constructor.
	 * <p>
	 * This class expects a configuration file with the same name as the application class indicated in the constructor with a base extension of
	 * <code>-config</code>, such as <code>ExampleApp-config.properties</code>, loaded via Confound from the resources in the same path as the application class.
	 * For example:
	 * </p>
	 * 
	 * <pre>
	 * {@code
	 * name=${project.name}
	 * version=${project.version}
	 * }
	 * </pre>
	 * 
	 * @author Garret Wilson
	 */
	protected static abstract class AbstractMetadataProvider implements IVersionProvider {

		private final Class<? extends Application> applicationClass;

		/**
		 * {@inheritDoc}
		 * @implSpec This implementation retrieves the name from resources for the concrete application class using the resource key
		 *           {@value BaseCliApplication#CONFIG_KEY_VERSION}.
		 * @see BaseCliApplication#CONFIG_KEY_VERSION
		 * @throws ConfigurationException if there was an error retrieving the configured name or the name could not be found.
		 */
		@Override
		public String[] getVersion() throws Exception {
			return new String[] {ResourcesConfigurationManager.loadConfigurationForClass(applicationClass)
					.orElseThrow(ResourcesConfigurationManager::createConfigurationNotFoundException).getString(CONFIG_KEY_VERSION)};
		}

		/**
		 * Application class constructor.
		 * @param applicationClass The given application class for relative look up of configuration resources.
		 */
		public AbstractMetadataProvider(@Nonnull final Class<? extends Application> applicationClass) {
			this.applicationClass = requireNonNull(applicationClass);
		}

	}

}
