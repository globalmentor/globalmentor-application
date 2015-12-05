/*
 * Copyright © 1996-2008 GlobalMentor, Inc. <http://www.globalmentor.com/>
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

import java.io.*;
import java.util.*;
import java.util.regex.*;

import com.globalmentor.java.Enums;
import static com.globalmentor.java.Enums.*;

import com.globalmentor.lex.Identifier;
import com.globalmentor.log.*;

/**
 * Constants and utilities for accessing command-line arguments.
 * <p>
 * This implementation recognizes two types of command-line <dfn>switches</dfn>: <dfn>options</dfn>, which have arguments, and <dfn>flags</dfn>, which do not.
 * </p>
 * <p>
 * This implementation only recognizes long switches prefixed with {@value #LONG_SWITCH_DELIMITER}.
 * </p>
 * <p>
 * Because MS-DOS batch files substitute a space for the equals sign, this implementation accepts the colon character (':') as a replacement for the equals sign
 * ('=') in options, although the equals sign is preferred when possible.
 * </p>
 * @author Garret Wilson
 */
public class CommandLineArguments {

	/** Common command-line parameters. */
	public enum Switch implements Identifier {
		/** Verbose output. */
		VERBOSE,
		/** Quiet output. */
		QUIET,
		/** The switch for help. */
		HELP,
		/** The file to use for logging. */
		LOG_FILE,
		/** The logging level. */
		LOG_LEVEL;
	}

	/** The long delimiter that introduces a switch. */
	public static final String LONG_SWITCH_DELIMITER = "--";

	/** The pattern for matching switches in general. */
	public static final Pattern SWITCH_PATTERN = Pattern.compile("--([\\w-&&[^=:]]+)(?:=(.+)?)");

	/** The pattern for matching flags. */
	public static final Pattern FLAG_PATTERN = Pattern.compile("--([\\w-&&[^=:]]+)");

	/** The pattern for matching options. */
	public static final Pattern OPTION_PATTERN = Pattern.compile("--([\\w-&&[^=:]]+)[=:](.+)");

	/** This class can only be instantiated if a class is derived from it. */
	protected CommandLineArguments() {
	}

	/**
	 * Sets default program debugging based upon the presence of the "log" flag and the logging switches.
	 * @param arguments The array of command line arguments.
	 * @see Switch#LOG_LEVEL
	 * @see Switch#VERBOSE
	 * @see Switch#QUIET
	 * @see Switch#LOG_FILE
	 */
	public static void configureLog(final String[] arguments) {
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
	}

	/**
	 * Returns whether the help flag is turned on.
	 * @param arguments The array of command line arguments.
	 * @return <code>true</code> if the help flag is defined.
	 * @see Switch#HELP
	 */
	public static boolean hasHelpFlag(final String[] arguments) {
		return hasFlag(arguments, Switch.HELP); //return whether the help flag is defined
	}

	/**
	 * Searches the argument array to see if a particular flag is defined.
	 * <p>
	 * This implementation delegates to {@link #getFlag(String[], String)} using the serialization form of the given enum.
	 * </p>
	 * @param <F> The type of flag.
	 * @param arguments The array of command line arguments.
	 * @param flag The name of the flag which may be defined.
	 * @return <code>true</code> if the flag is defined, else <code>false</code>.
	 * @see Enums#getSerializationName(Enum)
	 * @see #hasFlag(String[], String)
	 */
	public static <F extends Enum<F> & Identifier> boolean hasFlag(final String[] arguments, final F flag) {
		return hasFlag(arguments, getSerializationName(flag));
	}

	/**
	 * Searches the argument array to see if a particular flag is defined.
	 * @param arguments The array of command line arguments.
	 * @param flagName The name of the flag which may be defined.
	 * @return <code>true</code> if the flag is defined, else <code>false</code>.
	 * @see #FLAG_PATTERN
	 */
	public static boolean hasFlag(final String[] arguments, final String flagName) {
		for(int i = 0; i < arguments.length; ++i) { //look at each of the arguments
			final Matcher flagMatcher = FLAG_PATTERN.matcher(arguments[i]); //try to match against this argument
			if(flagMatcher.matches() && flagName.equals(flagMatcher.group(1))) { //if this is a flag with the correct name
				return true; //we found the flag
			}
		}
		return false; //show that the flag wasn't defined
	}

	/**
	 * Searches the given arguments for the last occurrence of a particular option. Using the last occurrence allows options to be appended to an existing batch
	 * or shell file on the command line and override the defaults.
	 * <p>
	 * This implementation delegates to {@link #getOption(String[], String, Class)} using the serialization form of the given enum.
	 * </p>
	 * @param <O> The type of option.
	 * @param <V> The type of value.
	 * @param arguments The array of command line arguments.
	 * @param option The option.
	 * @param valueType The type of value to expect.
	 * @return The argument of the last occurrence of the given option, or <code>null</code> if the option is not defined.
	 * @throws IllegalArgumentException if the given value is not valid for the expected type.
	 * @see Enums#getSerializationName(Enum)
	 * @see Enums#getSerializedEnum(Class, String)
	 * @see #getOption(String[], String, Class)
	 */
	public static <O extends Enum<O> & Identifier, V extends Enum<V> & Identifier> V getOption(final String[] arguments, final O option, final Class<V> valueType) {
		return getOption(arguments, getSerializationName(option), valueType);
	}

	/**
	 * Searches the given arguments for the last occurrence of a particular option. Using the last occurrence allows options to be appended to an existing batch
	 * or shell file on the command line and override the defaults.
	 * <p>
	 * This implementation delegates to {@link #getOption(String[], String)} using the serialization form of the given enum.
	 * </p>
	 * @param <O> The type of option.
	 * @param arguments The array of command line arguments.
	 * @param option The option.
	 * @return The argument of the last occurrence of the given option, or <code>null</code> if the option is not defined.
	 * @see Enums#getSerializationName(Enum)
	 * @see #getOption(String[], String)
	 */
	public static <O extends Enum<O> & Identifier> String getOption(final String[] arguments, final O option) {
		return getOption(arguments, getSerializationName(option));
	}

	/**
	 * Searches the given arguments for the last occurrence of a particular option. Using the last occurrence allows options to be appended to an existing batch
	 * or shell file on the command line and override the defaults.
	 * <p>
	 * This implementation converts the value, if any, to the given value type, interpreting the value as the serialized form of an enum.
	 * </p>
	 * @param <V> The type of value.
	 * @param arguments The array of command line arguments.
	 * @param optionName The name of the option.
	 * @param valueType The type of value to expect.
	 * @return The argument of the last occurrence of the given option, or <code>null</code> if the option is not defined.
	 * @throws IllegalArgumentException if the given value is not valid for the expected type.
	 * @see #OPTION_PATTERN
	 * @see Enums#getSerializedEnum(Class, String)
	 */
	public static <V extends Enum<V> & Identifier> V getOption(final String[] arguments, final String optionName, final Class<V> valueType) {
		final String optionValue = getOption(arguments, optionName); //get the string form of the option
		return optionValue != null ? getSerializedEnum(valueType, optionValue) : null;
	}

	/**
	 * Searches the given arguments for the last occurrence of a particular option. Using the last occurrence allows options to be appended to an existing batch
	 * or shell file on the command line and override the defaults.
	 * @param arguments The array of command line arguments.
	 * @param optionName The name of the option.
	 * @return The argument of the last occurrence of the given option, or <code>null</code> if the option is not defined.
	 * @see #OPTION_PATTERN
	 */
	public static String getOption(final String[] arguments, final String optionName) {
		for(int i = arguments.length - 1; i >= 0; --i) { //look at each of the arguments in reverse order
			final Matcher optionMatcher = OPTION_PATTERN.matcher(arguments[i]); //try to match against this argument
			if(optionMatcher.matches()) { //if this is an argument
				if(optionName.equals(optionMatcher.group(1))) { //if this is the correct option
					return optionMatcher.group(2); //return this option
				}
			}
		}
		return null; //show that the option wasn't defined
	}

	/**
	 * Returns the given arguments for all occurrences a particular option.
	 * <p>
	 * This implementation delegates to {@link #getOptions(String[], String)} using the serialization form of the given enum.
	 * </p>
	 * @param arguments The array of command line arguments.
	 * @param option The option.
	 * @return A non-<code>null</code> list of arguments of the defined options, if any, in the order encountered.
	 * @see Enums#getSerializationName(Enum)
	 * @see #getOptions(String[], String)
	 */
	public static <O extends Enum<O> & Identifier> List<String> getOptions(final String[] arguments, final O option) {
		return getOptions(arguments, getSerializationName(option));
	}

	/**
	 * Returns the given arguments for all occurrences a particular option.
	 * @param arguments The array of command line arguments.
	 * @param optionName The name of the option.
	 * @return A non-<code>null</code> list of arguments of the defined options, if any, in the order encountered.
	 * @see #OPTION_PATTERN
	 */
	public static List<String> getOptions(final String[] arguments, final String optionName) {
		final int argumentCount = arguments.length;
		List<String> options = null;
		for(int i = 0; i < argumentCount; ++i) { //look at each of the arguments
			final Matcher optionMatcher = OPTION_PATTERN.matcher(arguments[i]); //try to match against this argument
			if(optionMatcher.matches()) { //if this is an argument
				if(optionName.equals(optionMatcher.group(1))) { //if this is the correct option
					if(options == null) { //if we don't yet have a list of options
						options = new ArrayList<String>(); //create a new list for the parameters
					}
					options.add(optionMatcher.group(2)); //add this option
				}
			}
		}
		return options != null ? options : java.util.Collections.<String> emptyList(); //show that the switch wasn't defined
	}

	/**
	 * Checks to see if a particular argument is a switch, without regard to whether it is an option or a flag.
	 * @param argument The argument to check.
	 * @return <code>true</code> if the argument is a switch.
	 * @see #SWITCH_PATTERN
	 */
	public static boolean isSwitch(final String argument) {
		return SWITCH_PATTERN.matcher(argument).matches(); //see if the argument matches the switch pattern
	}

	/**
	 * Creates a switch argument in the form <code>-<var>switchString</var></code>.
	 * @param switchString The string to make into a switch.
	 * @return A command-line switch.
	 */
	@Deprecated
	public static String createSwitch(final String switchString) {
		return LONG_SWITCH_DELIMITER + switchString;
	}

}