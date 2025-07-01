/*
 * Copyright © 2019 GlobalMentor, Inc. <https://www.globalmentor.com/>
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

import static com.globalmentor.application.Picocli.*;
import static com.globalmentor.io.Filenames.*;
import static com.globalmentor.java.Conditions.*;
import static com.globalmentor.java.OperatingSystem.*;
import static com.globalmentor.java.Strings.*;
import static com.globalmentor.lex.CompoundTokenization.DOT_CASE;
import static com.globalmentor.util.Optionals.*;
import static io.confound.Confound.*;
import static io.confound.config.file.FileSystemConfigurationManager.*;
import static java.lang.String.format;
import static java.nio.file.Files.*;
import static java.util.Objects.*;
import static java.util.stream.Stream.*;
import static org.fusesource.jansi.Ansi.ansi;

import java.io.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.annotation.*;

import org.fusesource.jansi.*;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import com.github.dtmo.jfiglet.*;
import com.globalmentor.java.Characters;
import com.globalmentor.time.Durations;

import io.clogr.*;
import io.confound.config.*;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.*;

/**
 * Base implementation for facilitating creation of a CLI application.
 * <p>A subclass should annotate itself as the main command, e.g.:</p>
 * 
 * <pre>
 * {@code
 * &#64;Command(name = "foobar", description = "FooBar application.")
 * }
 * </pre>
 * <p>In order to return version information with the <code>--version</code> CLI option, this class expects build-related information to be stored in a
 * configuration file with the same name as the concrete application class (the subclass of this class) with a base extension of <code>-build</code> (e.g.
 * <code>FooBarApp-build.properties</code>), loaded via Confound from the resources in the same path as the application class. For more information see
 * {@link Application#loadBuildInfo(Class)}.</p>
 * 
 * <p>By default this class merely prints the command-line usage. This can be overridden for programs with specific functionality, but if the application
 * requires a command then the command methods can be added and annotated separately, with the default {@link #run()} method remaining for displaying an
 * explanation.</p>
 * 
 * <p>This class overrides the default CLI option converter for {@link Duration}, which only accepted input in the form
 * <code><mark>P</mark>7d<mark>T</mark>6h5m4.321s</code>, allowing for more lenient input such as <code>7d6h5m4.321s</code>.</p>
 * 
 * <p>This class sets up the following options:</p>
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
 * @implSpec Using the application slug from {@link #getSlug()} as the identifier, this implementation loads a global configuration from a subdirectory in the
 *           user's home directory in the form <code>~/.my-app/my-app.properties</code>. Local overriding configuration files are discovered in decreasing order
 *           of priority in the current working directory, each parent directory of the working directory, and finally the user home directory; checking in each
 *           directory for a configuration file in the form <code>.my-app.properties</code>. For example <code>.my-app.properties</code> in the current working
 *           directory would override <code>../.my-app.properties</code> and finally <code>~/.my-app.properties</code>. All of these configuration files support
 *           lookup fallback, with the highest priority configuration coming from system properties and then environment variables. Supported configuration file
 *           formats are governed by {@link io.confound.Confound} and its installed configuration file format providers.
 * @implNote Currently fallback of a required global option to the configuration will not work if a subcommand is invoked. This will start working when
 *           <a href="https://github.com/remkop/picocli/issues/2443">picocli Issue #2443: Default value provider is not called for required options</a> is
 *           fixed. For the meantime a workaround is to use only non-required options if fallback to configuration is required, verifying the presence of the
 *           option at runtime.
 * @author Garret Wilson
 * @see <a href="https://tldp.org/LDP/abs/html/standard-options.html">Advanced Bash-Scripting Guide: G.1. Standard Command-Line Options</a>
 */
@Command(versionProvider = BaseCliApplication.MetadataProvider.class, mixinStandardHelpOptions = true)
public abstract class BaseCliApplication extends AbstractApplication {

	/**
	 * The default terminal width if one cannot be determined.
	 * @see <a href="https://stackoverflow.com/q/4651012">Why is the default terminal width 80 characters?</a>
	 * @see <a href="https://richarddingwall.name/2008/05/31/is-the-80-character-line-limit-still-relevant/">Is the 80 character line limit still relevant?</a>
	 */
	public static final int DEFAULT_TERMINAL_WIDTH = 120;

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation retrieves the name from resources for the concrete application class using the resource key {@value #CONFIG_KEY_NAME}.
	 * @throws ConfigurationException if there was an error retrieving the configured name or the name could not be found.
	 * @see #getBuildInfo()
	 * @see #CONFIG_KEY_NAME
	 */
	@Override
	public String getName() {
		return getBuildInfo().getString(CONFIG_KEY_NAME);
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation retrieves the name from resources for the concrete application class using the resource key {@value #CONFIG_KEY_VERSION}.
	 * @throws ConfigurationException if there was an error retrieving the configured name or the name could not be found.
	 * @see #getBuildInfo()
	 * @see #CONFIG_KEY_VERSION
	 */
	@Override
	public String getVersion() {
		return getBuildInfo().getString(CONFIG_KEY_VERSION);
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

	/**
	 * Returns whether quite mode is enabled.
	 * @return Whether quiet output has been requested.
	 */
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

	/**
	 * Returns whether verbose mode is enabled.
	 * @return Whether verbose output has been requested.
	 */
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
	@SuppressWarnings("this-escape") //this is somewhat benign, as `updateLogLevel()` will be called again later if/as the various settings get updated, e.g. by picocli; the sequence could possibly be improved, however
	public BaseCliApplication(@Nonnull final String[] args, final Level defaultLogLevel) {
		super(args);
		this.defaultLogLevel = defaultLogLevel;
		updateLogLevel(); //update the log level based upon the debug setting
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation returns the annotated name from the {@link Command} annotation on the concrete application class, such as
	 *           <code>@Command(name = "myapp", …)</code>. The annotation is queried manually rather than accessing the command line command spec so that this
	 *           value will be available even before application initialization. If the concrete application class has no {@link Command} annotation or does not
	 *           provide a name, the default slug is returned.
	 * @see Command
	 */
	@Override
	public String getSlug() {
		return findAnnotatedCommandName(getClass()).orElseGet(super::getSlug); //if there is no `@Command` annotation, or it doesn't provide a `name`, return the default slug
	}

	/**
	 * The picocli command line instance for the currently executing application.
	 * @implSpec This value is only available after initialization; otherwise <code>null</code>. Once set it is never unset.
	 * @see #execute()
	 */
	@Nullable
	private volatile CommandLine commandLine;

	CommandLine getCommandLine() { //TODO document
		return commandLine;
	}

	private boolean isAnsiEnabled = true;

	/**
	 * Returns whether the application will support ANSI.
	 * @apiNote ANSI support does not mean ANSI has yet been installed; this only happens during system initialization.
	 * @implNote ANSI support uses Jansi.
	 * @return Whether ANSI support is enabled for the application.
	 * @see #initializeSystem()
	 */
	protected boolean isAnsiEnabled() {
		return isAnsiEnabled;
	}

	/**
	 * Turns ANSI support on or off. Changes to this setting must be done before system initialization.
	 * @apiNote Enabling ANSI support does not actually install ANSI support immediately; this is done during system initialization based upon this setting.
	 * @param enabled Whether to enable ANSI support.
	 * @implNote ANSI support uses Jansi.
	 * @throws IllegalStateException if ANSI support is being changed and application has already been initialized.
	 */
	protected void setAnsiEnabled(final boolean enabled) {
		if(enabled == isAnsiEnabled) { //if the ANSI support setting isn't changing, there's nothing to do
			return;
		}
		checkState(isInitialized() == false, "Cannot change whether ANSI is supported after application initialization.");
		isAnsiEnabled = enabled;
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation calls {@link AnsiConsole#systemInstall()} if ANSI is enabled.
	 * @see #isAnsiEnabled()
	 */
	protected void initializeSystem() throws Exception {
		super.initializeSystem();
		if(isAnsiEnabled()) {
			AnsiConsole.systemInstall();
		}
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation configures picocli and creates the parsed command line.
	 */
	@Override
	public void initializeApplication() throws Exception {
		super.initializeApplication();
		final IExecutionExceptionHandler errorHandler = (exception, commandLine, parseResult) -> {
			reportError(exception);
			return EXIT_CODE_SOFTWARE;
		};
		this.commandLine = new CommandLine(this);
		commandLine.setExecutionExceptionHandler(errorHandler);
		commandLine.registerConverter(Duration.class, Durations::parseUserInput);
		commandLine.setDefaultValueProvider(new CommandLine.IDefaultValueProvider() {
			@Override
			public String defaultValue(final ArgSpec argSpec) throws Exception {
				return findDefaultArg(argSpec).orElse(null);
			}
		});
		//Set the picocli width manually because 1) Jansi's detection is faster and maybe more accurate;
		//and 2) we have a different preferred default width. Note that this automatically detects an
		//ANSI input stream, so there is no need to check whether the application ANSI setting is enabled.
		final int detectedTerminalWidth = System.out instanceof AnsiPrintStream ? ((AnsiPrintStream)System.out).getTerminalWidth() : 0;
		commandLine.setUsageHelpWidth(detectedTerminalWidth > 0 ? detectedTerminalWidth : DEFAULT_TERMINAL_WIDTH);
	}

	/**
	 * Looks up a default CLI argument for the given picocli argument.
	 * @apiNote This is a low-level picocli implementation detail which normally should not be overridden unless very custom default CLI argument lookup is
	 *          needed.
	 * @implSpec This implementation only handles options, delegating to {@link #findDefaultOption(OptionSpec)}.
	 * @param argSpec The CLI option or positional parameter.
	 * @return The default argument value, if found.
	 */
	protected Optional<String> findDefaultArg(final ArgSpec argSpec) {
		return argSpec.isOption() ? findDefaultOption((OptionSpec)argSpec) : Optional.empty();
	}

	/**
	 * Looks up a default CLI argument for the given picocli option.
	 * @apiNote This is a low-level picocli implementation detail which normally should not be overridden unless very custom default CLI argument lookup is
	 *          needed.
	 * @implSpec This implementation delegates to {@link #findDefaultOptionConfigurationString(String)} if the option's configuration key is enabled.
	 * @param optionSpec The CLI option.
	 * @return The default argument value, if found.
	 * @see #getConfigurationKey(OptionSpec)
	 * @see #isOptionDefaultConfigurationEnabled(String)
	 */
	protected Optional<String> findDefaultOption(final OptionSpec optionSpec) {
		final String configurationKey = getConfigurationKey(optionSpec);
		if(isOptionDefaultConfigurationEnabled(configurationKey)) {
			return findDefaultOptionConfigurationString(configurationKey);
		}
		return Optional.empty(); //configuration lookup was not enabled for this option  
	}

	/**
	 * Looks up a default CLI argument for the given picocli option.
	 * @apiNote This is a low-level picocli implementation detail which normally should not be overridden unless very custom default CLI argument lookup is
	 *          needed.
	 * @implSpec This implementation calls {@link Configuration#findString(String)}.
	 * @implNote This implementation currently only supports string-based configuration values. A future improved version may detect the option type and look up
	 *           the correct type and convert to a string in a more appropriate way.
	 * @param configurationKey The configuration key determined to be appropriate for an option.
	 * @return The default argument value, if found in the configuration.
	 * @see #getConfiguration()
	 */
	protected Optional<String> findDefaultOptionConfigurationString(@Nonnull final String configurationKey) {
		return getConfiguration().findString(configurationKey);
	}

	/** The character used one or more times to prefix an option. */
	private static final Characters OPTION_PREFIXES = Characters.of('-');

	/** A function for stripping the <code>'-'</code> and <code>"--"</code> prefixes from an option name. */
	private static final Function<String, String> STRIP_OPTION_NAME_PREFIXES = optionName -> trim(optionName, OPTION_PREFIXES); //TODO switch to a simpler, more efficient utility

	/**
	 * Determines the configuration key to look up the given option.
	 * @implSpec This implementation delegates to {@link #getQualifiedCommandNamesOptionConfigurationKey(Stream, CharSequence)}, passing the full, qualified
	 *           command sequence and longest option name with prefixes removed.
	 * @param optionSpec The picocli specification of the option.
	 * @return The configuration key for looking up a fallback configuration for the identified option.
	 */
	protected final String getConfigurationKey(final OptionSpec optionSpec) {
		return getQualifiedCommandNamesOptionConfigurationKey(qualifiedCommandSpecs(optionSpec.command()).map(CommandSpec::name),
				STRIP_OPTION_NAME_PREFIXES.apply(optionSpec.longestName()));
	}

	/**
	 * Determines the configuration key to look up an option given a sequence of command names and option name.
	 * @implSpec This implementation joins the qualified command names and option name using <code>dot.case</code>.
	 * @param qualifiedCommandNames The full sequence of command names, starting with the top-level application command.
	 * @param optionName The name of the option, with no prefixes.
	 * @return The configuration key for looking up a fallback configuration for the identified option.
	 */
	protected String getQualifiedCommandNamesOptionConfigurationKey(final Stream<? extends CharSequence> qualifiedCommandNames, final CharSequence optionName) {
		final Stream<CharSequence> configurationKeySegments = concat(qualifiedCommandNames, Stream.of(requireNonNull(optionName)));
		return DOT_CASE.join(configurationKeySegments::iterator);
	}

	/**
	 * Determines the configuration key to look up an option given a sequence of command classes, <em>relative to the application command</em>; and option name.
	 * For example, if this application is annotated with {@link Command} name <code>"my-cli"</code>; and a command sequence of <code>"do"</code> and
	 * <code>"something"</code> are passed with an option of <code>"name-suffix"</code>, this method returns <code>"my-cli.do.something.name-suffix"</code> with
	 * the default implementation. If the application itself has no annotated command name, it is simply left out.
	 * @implSpec This implementation delegates to {@link #getQualifiedCommandNamesOptionConfigurationKey(Stream, CharSequence)}, passing the this application's
	 *           command name, the annotated command names from the given classes, and the given option name.
	 * @param relativeQualifiedCommandClasses The full sequence of command classes <em>relative</em> to (i.e. not including) the top-level application command.
	 * @param optionName The name of the option, with no prefixes.
	 * @return The configuration key for looking up a fallback configuration for the identified option.
	 * @throws IllegalArgumentException if any of the given command classes are not annotated with {@link Command}.
	 */
	protected final String getRelativeQualifiedCommandClassesOptionConfigurationKey(final Stream<Class<?>> relativeQualifiedCommandClasses,
			final CharSequence optionName) {
		final Stream<String> qualifiedCommandNames = concat(findAnnotatedCommandName(getClass()).stream(),
				relativeQualifiedCommandClasses.map(commandClass -> findAnnotatedCommandName(commandClass).orElseThrow(() -> new IllegalArgumentException(
						"Command class `%s` not annotated with the `@%s` annotation.".formatted(commandClass.getName(), Command.class.getSimpleName())))));
		return getQualifiedCommandNamesOptionConfigurationKey(qualifiedCommandNames, optionName);
	}

	private final Set<String> optionDefaultConfigurationKeys = new HashSet<>();

	/**
	 * Indicates whether a missing command option value has been opted in to fall back to the configuration for the given configuration key.
	 * @param configurationKey The configuration key to allow option default value lookup.
	 * @return <code>true</code> if the command option corresponding to the given configuration key has default fallback to the configuration enabled.
	 * @see #enableOptionDefaultConfiguration(String).
	 */
	final boolean isOptionDefaultConfigurationEnabled(@Nonnull final String configurationKey) {
		return optionDefaultConfigurationKeys.contains(requireNonNull(configurationKey));
	}

	/**
	 * Opts in to looking up a missing command option value from the configuration. The conversion of a qualified command and its subcommand is deterministic,
	 * allowing opt-in via that known configuration key.
	 * @param configurationKey The configuration key to allow option default value lookup, in the form <code>command-name.subcommand-name.option-name</code> using
	 *          the fully-qualified command sequence and the longest option name without the CLI option delimiter prefix. For example if the <code>aws</code> CLI
	 *          allowed a default configuration for <code>aws s3 ls --bucket-name-prefix</code>, the configuration key would be
	 *          <code>aws.s3.ls.bucket-name-prefix</code>.
	 */
	protected void enableOptionDefaultConfiguration(final String configurationKey) {
		optionDefaultConfigurationKeys.add(requireNonNull(configurationKey));
	}

	/**
	 * Opts in to looking up a missing command option value from the configuration.
	 * @apiNote This is a convenience command that provides more compile-time type safety by providing the {@link Command} annotated command classes themselves.
	 *          This method will not work with method-annotated commands.
	 * @implNote This implementation uses {@link #getRelativeQualifiedCommandClassesOptionConfigurationKey(Stream, CharSequence)} to determine the configuration
	 *           key, and then delegates to {@link #enableOptionDefaultConfiguration(String)}.
	 * @param optionName The name of the option, with no prefixes.
	 * @param relativeQualifiedCommandClasses The full sequence of command classes <em>relative</em> to (i.e. not including) the top-level application command.
	 * @throws IllegalArgumentException if any of the given command classes are not annotated with {@link Command}.
	 */
	protected void enableOptionDefaultConfigurationForRelativeCommandClasses(final CharSequence optionName, final Class<?>... relativeQualifiedCommandClasses) {
		enableOptionDefaultConfiguration(getRelativeQualifiedCommandClassesOptionConfigurationKey(Stream.of(relativeQualifiedCommandClasses), optionName));
	}

	/**
	 * Returns the directory to initiate the search for a local configuration. The directory may not exist.
	 * @implSpec The default implementation returns the current working directory.
	 * @return The start of the directory hierarchy for discovering a local configuration.
	 */
	protected Path getLocalConfigurationDirectory() {
		return getWorkingDirectory();
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation loads the global configuration if any using {@link #loadFoundGlobalConfiguration()}, and then loads any local configuration
	 *           overrides. The local configuration files are discovered in decreasing order of priority in the current working directory, each parent directory
	 *           of the working directory, and finally the global confirmation home {@link #getGlobalConfigurationHomeDirectory()}; checking in each directory for
	 *           a configuration file with the base name of {@link #getSlug()} as a dotfile. For example, for an application with a slug <code>my-app</code>, a
	 *           configuration file <code>.my-app.properties</code> in the current working directory would override <code>../.my-app.properties</code> and finally
	 *           <code>~/.my-app.properties</code>. The implementation then adds a configurations with higher priority for environment variables and system
	 *           properties.
	 * @implNote Supported configuration file formats are governed by {@link io.confound.Confound} and its installed configuration file format providers.
	 * @return The configuration information, if found and loaded successfully.
	 * @throws IOException if there was an I/O error loading the configuration.
	 * @see #loadFoundGlobalConfiguration()
	 * @see #getGlobalConfigurationHomeDirectory()
	 * @see #getLocalConfigurationDirectory()
	 */
	@Override
	protected Configuration loadConfiguration() throws IOException {
		//3. global configuration
		final Optional<Configuration> foundGlobalConfig = loadFoundGlobalConfiguration();
		final String localConfigBaseFilename = DOTFILE_PREFIX + getSlug(); //e.g. `.my-app`
		//2. local configuration in global configuration home
		final Path globalConfigHomeDirectory = getGlobalConfigurationHomeDirectory();
		final Optional<Configuration> foundGlobalConfigHomeLocalConfig = isDirectory(globalConfigHomeDirectory) //TODO remove check when CONFOUND-35 is fixed
				? loadConfigurationForBaseFilename(globalConfigHomeDirectory, localConfigBaseFilename)
				: Optional.empty();
		//1. local configurations in local configuration directory hierarchy
		final Set<Path> skippedLocalConfigDiretories = Set.of(
				//Don't search the global config home directory, because it was already checked about, and would result in duplicate configs in the chain.
				globalConfigHomeDirectory,
				//Don't search the temp directory, because that directory potentially contains an enormous number of files and unacceptably bogs down
				//the search. Moreover semantically files in the temporary directory are not intended for direct consumption based upon their location;
				//the temporary directory is intended as a location _outside_ the normal hierarchy. The temporary directory normally wouldn't appear
				//in the local config search hierarchy anyway; however it does appear in the hierarchy for integration tests, and that is where this
				//setting has the most impact.
				getTempDirectory());
		final Optional<Configuration> foundLocalConfig = findConfigurationChainInHierarchy(getLocalConfigurationDirectory(), localConfigBaseFilename,
				skippedLocalConfigDiretories);
		final Optional<Configuration> fileConfigChain = fold(foundLocalConfig,
				fold(foundGlobalConfigHomeLocalConfig, foundGlobalConfig, Configuration::withFallback), Configuration::withFallback);
		//0. system/environment configuration
		return getSystemConfiguration(fileConfigChain.orElse(null)); //override the file-based config chain, if any, with environment variables and system properties 
	}

	/**
	 * Loads configurations up the given directory hierarchy, chaining fallback configurations in decreasing order of priority, finally falling back to the giving
	 * terminal fallback configuration, if any.
	 * @implSpec This implementation uses recursion to check each higher-level directory.
	 * @implNote This implementation interprets "skip" to mean "don't even check to see if the directory is accessible". Hence it is possible that a accessible
	 *           directories could be found above a skipped directory, even if the skipped directory is inaccessible and would normally terminate the search. This
	 *           is unlikely to happen in practice, and the result if it were to happen is not unreasonable.
	 * @param directory The configuration directory hierarchy to search.
	 * @param baseFilename The base filename to use to find the configuration at each directory level.
	 * @param skippedDirectories The directories that should be skipped in the search, without any checks for a configuration file.
	 * @return A configuration representing the first configuration, if any, found up the hierarchy; chained with fallbacks to all other configurations found up
	 *         the hierarchy.
	 * @throws IOException if an I/O error occurs loading the configuration(s).
	 * @throws ConfigurationException If there is invalid data or invalid state preventing a configuration from being loaded.
	 */
	protected static Optional<Configuration> findConfigurationChainInHierarchy(@Nonnull final Path directory, @Nonnull final String baseFilename,
			@Nonnull final Set<Path> skippedDirectories) throws IOException, ConfigurationException {
		final Optional<Configuration> foundConfig;
		if(!skippedDirectories.contains(directory)) { //if this is not a directory to skip
			//Perform a directory check at every level of the hierarchy in case some OS doesn't allow access to some folders,
			//in which case this check will return `false` instead of throwing an exception. 
			if(!isDirectory(directory)) {
				return Optional.empty(); //recursive base case			
			}
			foundConfig = loadConfigurationForBaseFilename(directory, baseFilename);
		} else { //if we are skipping this directory
			foundConfig = Optional.empty(); //continue as if no config was found at this level
		}
		final Path configParentDirectory = directory.getParent(); //check the next higher level
		if(configParentDirectory == null) { //if there is no parent level, we're at the root
			return foundConfig; //there can be no fallback at the root
		}
		final Optional<Configuration> foundParentConfig = findConfigurationChainInHierarchy(configParentDirectory, baseFilename, skippedDirectories); //get the parent's configuration chain
		return fold(foundConfig, foundParentConfig, Configuration::withFallback);
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation uses picocli to execute the application using {@link CommandLine#execute(String...)}.
	 */
	@Override
	protected int execute() {
		//run the application via picocli instead of using the default version, which will call appropriate command methods as needed
		checkState(commandLine != null, "Missing command-line information; application not properly initialized.");
		return commandLine.execute(getArgs());
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
	 * @implSpec This implementation calls {@link AnsiConsole#systemUninstall()} if ANSI is enabled.
	 * @see #isAnsiEnabled()
	 */
	@Override
	protected void cleanupSystem() {
		if(isAnsiEnabled()) {
			AnsiConsole.systemUninstall();
		}
		super.cleanupSystem();
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This version logs an information message if the application was in the shutdown process already.
	 */
	@Override
	protected void onExit() {
		super.onExit();
		if(isShuttingDown()) {
			getLogger().info("Application shut down gracefully after termination.");
		}
	}

	/**
	 * Logs startup app information, including application banner, name, and version.
	 * @see #getLogger()
	 * @see Level#INFO
	 * @throws ConfigurationException if some configuration information isn't present.
	 */
	protected void logAppInfo() {
		final FigletRenderer figletRenderer;
		try {
			figletRenderer = new FigletRenderer(FigFontResources.loadFigFontResource(FigFontResources.BIG_FLF));
		} catch(final IOException ioException) {
			throw new ConfigurationException(ioException);
		}
		final Configuration buildInfo = getBuildInfo();
		final String appName = buildInfo.getString(CONFIG_KEY_NAME);
		final String appVersion = buildInfo.getString(CONFIG_KEY_VERSION);
		final Logger logger = getLogger();
		logger.info("\n{}{}{}", ansi().bold().fg(Ansi.Color.GREEN), figletRenderer.renderText(appName), ansi().reset());
		logger.info("{} {}\n", appName, appVersion);
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
	 * Strategy for retrieving the application name and version from the configuration. For more information on build information storage see
	 * {@link Application#loadBuildInfo(Class)}. The {@value Application#CONFIG_KEY_NAME} and {@value Application#CONFIG_KEY_VERSION} properties are required. The
	 * properties {@value Application#CONFIG_KEY_NAME} <code>name</code> and <code>version</code> properties are required. The
	 * {@value Application#CONFIG_KEY_BUILT_AT} and {@value Application#CONFIG_KEY_COPYRIGHT} properties are optional.
	 * @implSpec This class only works in an environment such as picocli that injects a {@link CommandSpec} with a {@link CommandSpec#userObject()} that is an
	 *           instance of {@link AbstractApplication}.
	 * @author Garret Wilson
	 */
	protected static class MetadataProvider implements IVersionProvider {

		/** No-args constructor. */
		public MetadataProvider() {
		}

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
		 * @implSpec This implementation delegates to {@link AbstractApplication#getBuildInfo()} to load build information from the injected
		 *           {@link CommandSpec#userObject()}.
		 * @see BaseCliApplication#CONFIG_KEY_NAME
		 * @see BaseCliApplication#CONFIG_KEY_VERSION
		 * @see BaseCliApplication#CONFIG_KEY_BUILT_AT
		 * @see BaseCliApplication#CONFIG_KEY_COPYRIGHT
		 * @throws ConfigurationException if there was an error retrieving the build information or the build information contained no name and/or version
		 *           information.
		 */
		@Override
		public String[] getVersion() throws Exception {
			final Configuration buildInfo = ((AbstractApplication)commandSpec.userObject()).getBuildInfo();
			final List<String> versionLines = new ArrayList<>();
			versionLines.add(format("%s %s", buildInfo.getString(CONFIG_KEY_NAME), buildInfo.getString(CONFIG_KEY_VERSION))); //e.g. "FooBar 1.2.3"
			buildInfo.findString(CONFIG_KEY_BUILT_AT).ifPresent(builtAt -> versionLines.add(format("Built at %s.", builtAt))); //e.g. "Built at 2022-10-26T12:34:56Z."
			final String javaVendor = System.getProperty("java.vendor");
			versionLines.add(format("Java (%s) %s", javaVendor, Runtime.version())); //e.g. "Java (Vendor) 17.x.x"
			buildInfo.findString(CONFIG_KEY_COPYRIGHT).ifPresent(versionLines::add); //e.g. "Copyright © 2022 Acme Company"
			return versionLines.toArray(String[]::new);
		}

	}

}
