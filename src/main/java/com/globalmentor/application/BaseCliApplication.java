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

import static com.globalmentor.java.Conditions.*;
import static org.fusesource.jansi.Ansi.ansi;

import java.io.*;
import java.util.OptionalInt;

import javax.annotation.*;

import org.fusesource.jansi.*;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import com.github.dtmo.jfiglet.*;

import io.clogr.*;
import io.confound.config.*;
import io.confound.config.file.ResourcesConfigurationManager;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Base implementation for facilitating creation of a CLI application.
 * <p>
 * A subclass should annotate itself as the main command, e.g.:
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;Command(name = "foobar", description = "FooBar application.")
 * }
 * </pre>
 * <p>
 * This class expects a configuration file with the same name as the concrete application class (the subclass of this class) with a base extension of
 * <code>-config</code>, loaded via Confound from the resources in the same path as the application class. For example the following might be stored as
 * <code>ExampleApp-config.properties</code>:
 * </p>
 * 
 * <pre>
 * {@code
 * name=${project.name}
 * version=${project.version}
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
 * <dd>Turns on debug mode and enables debug level logging.</dd>
 * <dt><code>--trace</code></dt>
 * <dd>Enables trace level logging.</dd>
 * <dt><code>--quiet</code>, <code>-q</code></dt>
 * <dd>Reduces or eliminates output of unnecessary information.</dd>
 * <dt><code>--verbose</code>, <code>-v</code></dt>
 * <dd>Provides additional output information.</dd>
 * </dl>
 * @implSpec This implementation adds ANSI support via Jansi.
 * @author Garret Wilson
 * @see <a href="https://tldp.org/LDP/abs/html/standard-options.html">Advanced Bash-Scripting Guide: G.1. Standard Command-Line Options</a>
 */
@Command(versionProvider = BaseCliApplication.MetadataProvider.class, mixinStandardHelpOptions = true)
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

	private final Level defaultLogLevel;

	private boolean debug;

	@Override
	public boolean isDebug() {
		return debug;
	}

	/**
	 * Enables or disables debug mode and debug level logging, which is disabled by default.
	 * @param debug The new state of debug mode.
	 */
	@Option(names = {"--debug", "-d"}, description = "Turns on debug mode and enables debug level logging.", scope = ScopeType.INHERIT)
	protected void setDebug(final boolean debug) {
		this.debug = debug;
		updateLogLevel();
	}

	private boolean trace;

	/** @return Whether trace-level logging has been requested. */
	private boolean isTrace() {
		return trace;
	}

	/**
	 * Enables or disables trace level logging, which is disabled by default.
	 * @apiNote This method does not turn on debug mode, so if debug mode is desired along with trace logging, be sure and call {@link #setDebug(boolean)} as
	 *          well.
	 * @param trace The new state of trace mode.
	 */
	@Option(names = {"--trace"}, description = "Enables trace level logging.", scope = ScopeType.INHERIT)
	protected void setTrace(final boolean trace) {
		this.trace = trace;
		updateLogLevel();
	}

	/**
	 * Updates the log level based upon the current debug setting. The current debug setting remains unchanged.
	 * @implSpec If {@link #isQuiet()} is enabled, it takes priority and {@link Level#WARN} is used. Otherwise {@link #isTrace()} results in {@link Level#TRACE},
	 *           and {@link #isDebug()} results in {@link Level#DEBUG}. If no log level-related options are indicated the {@link #defaultLogLevel} is used.
	 * @see #isQuiet()
	 * @see #isDebug()
	 * @see #isTrace()
	 */
	protected void updateLogLevel() {
		final Level logLevel;
		if(isQuiet()) {
			logLevel = Level.WARN;
		} else if(isTrace()) {
			logLevel = Level.TRACE;
		} else if(isDebug()) {
			logLevel = Level.DEBUG;
		} else {
			logLevel = defaultLogLevel;
		}
		Clogr.getLoggingConcern().setLogLevel(logLevel);
	}

	private boolean quiet = false;

	/** @return Whether quiet output has been requested. */
	protected boolean isQuiet() {
		return quiet;
	}

	/**
	 * Enables or disables quiet output. Mutually exclusive with {@link #setVerbose(boolean)}.
	 * @param quiet The new state of quietness.
	 */
	@Option(names = {"--quiet",
			"-q"}, description = "Reduces or eliminates output of unnecessary information. Mutually exclusive with the verbose option.", scope = ScopeType.INHERIT)
	protected void setQuiet(final boolean quiet) {
		checkState(!isVerbose(), "Quiet and verbose options are mutually exclusive.");
		this.quiet = quiet;
		updateLogLevel();
	}

	private boolean verbose = false;

	/** @return Whether verbose output has been requested. */
	protected boolean isVerbose() {
		return verbose;
	}

	/**
	 * Enables or disables verbose output. Mutually exclusive with {@link #setQuiet(boolean)}.
	 * @param verbose <code>true</code> if additional information should be output.
	 */
	@Option(names = {"--verbose",
			"-v"}, description = "Provides additional output information. Mutually exclusive with the quiet option.", scope = ScopeType.INHERIT)
	protected void setVerbose(final boolean verbose) {
		checkState(!isQuiet(), "Quiet and verbose options are mutually exclusive.");
		this.verbose = verbose;
	}

	/**
	 * Arguments constructor.
	 * @implSpec The {@link Level#WARN} log level is used by default if no other log level-related options are indicated.
	 * @param args The command line arguments.
	 */
	public BaseCliApplication(@Nonnull final String[] args) {
		this(args, Level.WARN);
	}

	/**
	 * Arguments constructor.
	 * @param args The command line arguments.
	 * @param defaultLogLevel The default log level to use if no other log level-related options are indicated.
	 */
	public BaseCliApplication(@Nonnull final String[] args, final Level defaultLogLevel) {
		super(args);
		this.defaultLogLevel = defaultLogLevel;
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
	 * The picocli command line instance for the currently executing application. Only available while the program is executing; otherwise <code>null</code>.
	 * @see #execute()
	 */
	@Nullable
	private volatile CommandLine commandLine;

	/**
	 * The suggested default terminal width.
	 * @implSpec This is currently set to the default CLI usage message width for consistency.
	 */
	protected static final int DEFAULT_TERMINAL_WIDTH = CommandLine.Model.UsageMessageSpec.DEFAULT_USAGE_WIDTH;

	/**
	 * Returns the width of the terminal if known.
	 * @apiNote It is recommended that if the terminal width is not known, {@link #DEFAULT_TERMINAL_WIDTH} be used as the default.
	 * @implNote Currently this method always returns a value if the application is running because picocli internally uses a fallback value. This may change in
	 *           the future to indicate whether the terminal width could not be detected. See <a href="https://github.com/remkop/picocli/issues/1756">picocli
	 *           issue #1756</a> for more discussion.
	 * @return The detected terminal width, or empty if the application is not executing or the terminal width could not be detected.
	 * @see #DEFAULT_TERMINAL_WIDTH
	 */
	protected OptionalInt findTerminalWidth() {
		final CommandLine commandLine = this.commandLine;
		return commandLine != null ? OptionalInt.of(commandLine.getCommandSpec().usageMessage().width()) : OptionalInt.empty();
	}

	/**
	 * Returns the width of the terminal or a default.
	 * @implSpec This implementation uses {@link #DEFAULT_TERMINAL_WIDTH} as the default terminal width if it cannot be detected.
	 * @return The terminal width.
	 */
	protected int getTerminalWidth() {
		return findTerminalWidth().orElse(DEFAULT_TERMINAL_WIDTH);
	}

	/**
	 * Creates a status appropriate for this application.
	 * @apiNote This factory method is particularly important to ensure the status uses the detected terminal width.
	 * @param <W> The type identifier of work to be represented by the status.
	 * @return An application status.
	 * @see #getTerminalWidth()
	 */
	protected <W> CliStatus<W> createStatus() {
		return new CliStatus<>(getTerminalWidth());
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation uses picocli to execute the application using {@link CommandLine#execute(String...)}.
	 */
	@Override
	protected int execute() {
		final IExecutionExceptionHandler errorHandler = (exception, commandLine, parseResult) -> {
			reportError(exception);
			return EXIT_CODE_SOFTWARE;
		};
		//run the application via picocli instead of using the default version, which will call appropriate command methods as needed
		this.commandLine = new CommandLine(this);
		try {
			commandLine.setExecutionExceptionHandler(errorHandler);
			commandLine.getCommandSpec().usageMessage().autoWidth(true); //turn on autodetection of terminal width
			return commandLine.execute(getArgs());
		} finally {
			commandLine = null;
		}
	}

	/**
	 * {@inheritDoc}
	 * @implSpec The default implementation prints the command-line usage.
	 * @implNote This can be overridden for programs with specific functionality, but if the application requires a command then the command methods can be added
	 *           and annotated separately, with the default {@link #run()} method remaining for displaying an explanation.
	 */
	@Override
	public void run() {
		commandLine.usage(System.out);
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
	 * Logs startup app information, including application banner, name, and version.
	 * @see #getLogger()
	 * @see Level#INFO
	 * @throws ConfigurationException if some configuration information isn't present.
	 */
	protected void logAppInfo() {
		final FigletRenderer figletRenderer;
		final Configuration appConfiguration;
		try {
			appConfiguration = ResourcesConfigurationManager.loadConfigurationForClass(getClass())
					.orElseThrow(ResourcesConfigurationManager::createConfigurationNotFoundException);
			figletRenderer = new FigletRenderer(FigFontResources.loadFigFontResource(FigFontResources.BIG_FLF));
		} catch(final IOException ioException) {
			throw new ConfigurationException(ioException);
		}
		final String appName = appConfiguration.getString(CONFIG_KEY_NAME);
		final String appVersion = appConfiguration.getString(CONFIG_KEY_VERSION);
		final Logger logger = getLogger();
		logger.info("\n{}{}{}", ansi().bold().fg(Ansi.Color.GREEN), figletRenderer.renderText(appName), ansi().reset());
		logger.info("{} {}\n", appName, appVersion);
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
	 * @implSpec This implementation calls {@link #reportError(String)}, and then logs both the error and exception using {@link Logger#debug(String)}.
	 * @implNote Double logging allows the message to be presented to the user at {@link Level#INFO} level, while still providing a stack trace at
	 *           {@link Level#DEBUG} level, which is likely only enabled in debug mode.
	 * @see Throwable#printStackTrace(PrintStream)
	 */
	@Override
	public void reportError(@Nonnull final String message, @Nonnull final Throwable throwable) {
		reportError(message);
		getLogger().debug("{}", message, throwable);
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation logs the error using Logger#error(String).
	 */
	@Override
	public void reportError(final String message) {
		getLogger().error("{}", message);
	}

	/**
	 * Strategy for retrieving the application name and version from the configuration.
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
	protected static class MetadataProvider implements IVersionProvider {

		/**
		 * Information on the command with which this provider is associated.
		 * @apiNote Injected by Picocli.
		 * @implNote This implementation uses the user object associated with the command for looking up resources.
		 * @see CommandSpec#userObject()
		 */
		@Spec
		private CommandSpec commandSpec;

		/**
		 * {@inheritDoc}
		 * @implSpec This implementation retrieves the name from resources for the concrete application class using the resource key
		 *           {@value BaseCliApplication#CONFIG_KEY_VERSION}.
		 * @see BaseCliApplication#CONFIG_KEY_VERSION
		 * @throws ConfigurationException if there was an error retrieving the configured name or the name could not be found.
		 */
		@Override
		public String[] getVersion() throws Exception {
			return new String[] {ResourcesConfigurationManager.loadConfigurationForClass(commandSpec.userObject().getClass())
					.orElseThrow(ResourcesConfigurationManager::createConfigurationNotFoundException).getString(CONFIG_KEY_VERSION)};
		}

	}

}
