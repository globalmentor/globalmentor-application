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

package com.globalmentor.util;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import com.globalmentor.util.Debug;

/**Constants and utilities for accessing command-line arguments.
<p>This implementation recognizes two types of command-line <dfn>switches</dfn>: <dfn>options</dfn>, which have arguments, and <dfn>flags</dfn>, which do not.</p>
<p>This implementation only recognizes long switches prefixed with {@value #LONG_SWITCH_DELIMITER}.</p>
@author Garret Wilson
*/
public class CommandLineArguments
{

	/**The long delimiter that introduces a switch.*/
	public final static String LONG_SWITCH_DELIMITER="--";
	
	/**The pattern for matching switches in general.*/
	public final static Pattern SWITCH_PATTERN=Pattern.compile("--([\\w-&&[^=]]+)(?:=(.+)?)");

	/**The pattern for matching flags.*/
	public final static Pattern FLAG_PATTERN=Pattern.compile("--([\\w-&&[^=]]+)");

	/**The pattern for matching options.*/
	public final static Pattern OPTION_PATTERN=Pattern.compile("--([\\w-&&[^=]]+)=(.+)");

	/**The debug flag, which turns on debug.
	@see #LOG_OPTION
	*/
	public final static String DEBUG_FLAG="debug";

	/**The debug level option, which is an integer representing the ORed levels of debugging that should be logged; should be used in conjuction with {@link #DEBUG_FLAG}.
	@see #DEBUG_FLAG
	*/
	public final static String DEBUG_LEVEL_OPTION="level";

	/**The debug visible flag, which turns on visible debugging; should be used in conjuction with {@link #DEBUG_FLAG}.
	@see #DEBUG_FLAG
	*/
	public final static String DEBUG_VISIBLE_FLAG="visible";

	/**The file option, which specifies an output file.*/
	public final static String FILE_OPTION="file";

	/**The help flag, which turns on help.*/
	public final static String HELP_FLAG="help";

	/**The log option, specifies a log file; should be used in conjuction with {@link #DEBUG_FLAG}.
	@see #DEBUG_FLAG
	*/
	public final static String LOG_OPTION="log";

	/**This class can only be instantiated if a class is derived from it.*/
	protected CommandLineArguments() {}

	/**Sets default program debugging based upon the presence of the "debug"
		switch and the debug logging parameters.
	@param argumentArray The array of command line arguments.
	@exception IOException Thrown if a log file was specified but cannot be created or cannot be accessed.
	@see #isDebug
	@see #getLogParameter
	*/
	public static void configureDebug(final String[] argumentArray) throws IOException
	{
		Debug.setDebug(hasDebugFlag(argumentArray)); //turn debug on or off, based upon the arguments
		final Debug.ReportLevel reportLevel=getDebugLevelOption(argumentArray);  //see what level is indicated
		if(reportLevel!=null)  //if a valid report level is indicated
			Debug.setMinimumReportLevel(reportLevel); //set the reporting level for debug logging
		Debug.setVisible(hasDebugVisibleFlag(argumentArray)); //turn debug visibility on or off, based upon the arguments
		final String logFilename=getLogOption(argumentArray);  //see if there is a log parameter
		if(logFilename!=null) //if there is a log parameter specified, create a new file and specify our output should go there
			Debug.setOutput(new File(logFilename));
	}

	/**Returns whether the debug flag {@link #DEBUG_FLAG} is turned on.
	@param argumentArray The array of command line arguments.
	@return <code>true</code> if the debug flag is defined.
	@see #DEBUG_FLAG
	*/
	public static boolean hasDebugFlag(final String[] argumentArray)
	{
		return hasFlag(argumentArray, DEBUG_FLAG);  //return whether the debug switch is defined
	}

	/**Returns the value of the debug level option.
	@param arguments The array of command line arguments.
	@return The minimum level specified by the debug level option, or <code>null</code> if the debug level is not specified.
	@throws IllegalArgumentException if the given debug level option is invalid.
	@see #DEBUG_LEVEL_OPTION
	*/
	public static Debug.ReportLevel getDebugLevelOption(final String[] arguments)
	{
		final String levelString=getOption(arguments, DEBUG_LEVEL_OPTION);  //get the level as a string
		if(levelString!=null) //if there is a level
		{
		  return Debug.ReportLevel.valueOf(levelString); //return the value
		}
		return null;  //show that we couldn't find a valid level
	}

	/**Returns whether the visible debug switch {@value #DEBUG_VISIBLE_FLAG} is turned on.
	@param arguments The array of command line arguments.
	@return <code>true</code> if the visible switch is defined.
	@see #DEBUG_VISIBLE_FLAG
	*/
	public static boolean hasDebugVisibleFlag(final String[] arguments)
	{
		return hasFlag(arguments, DEBUG_VISIBLE_FLAG);  //return whether the debug visible flag is defined
	}

	/**Returns the argument of the file option {@value #FILE_OPTION}.
	@param arguments The array of command line arguments.
	@return The argument if the file option is defined, else <code>null</code> if the option is not defined.
	@see #FILE_OPTION
	*/
	public static String getFileOption(final String[] arguments)
	{
		return getOption(arguments, FILE_OPTION);  //return the argument of the file option
	}

	/**Returns whether the help flag {@value #HELP_FLAG} is turned on.
	@param arguments The array of command line arguments.
	@return <code>true</code> if the help flag is defined.
	@see #HELP_FLAG
	*/
	public static boolean hasHelpFlag(final String[] arguments)
	{
		return hasFlag(arguments, HELP_FLAG);  //return whether the help flag is defined
	}

	/**Returns the argument of the log option {@value #LOG_OPTION}.
	@param arguments The array of command line arguments.
	@return The argument if the log option is defined, else <code>null</code> if the option is not defined.
	@see #LOG_OPTION
	*/
	public static String getLogOption(final String[] arguments)
	{
		return getOption(arguments, LOG_OPTION);  //return the argument of the log option
	}

	/**Searches the argument array to see if a particular flag is defined.
	@param arguments The array of command line arguments.
	@param flagName The name of the flag which may be defined.
	@return <code>true</code> if the flag is defined, else <code>false</code>.
	@see #FLAG_PATTERN
	*/
	public static boolean hasFlag(final String[] arguments, final String flagName)
	{
		for(int i=0; i<arguments.length; ++i)	//look at each of the arguments
		{
			final Matcher flagMatcher=FLAG_PATTERN.matcher(arguments[i]);	//try to match against this argumnet
			if(flagMatcher.matches() && flagName.equals(flagMatcher.group(1)))	//if this is a flag with the correct name
			{
				return true;	//we found the flag
			}
		}
		return false; //show that the flag wasn't defined
	}

	/**Searches the given arguments for the last occurrence of a particular option.
	Using the last occurrence allows options to be appended to an existing batch or shell file on the command line and override the defaults.
	@param arguments The array of command line arguments.
	@param optionName The name of the option.
	@return The argument of the last occurrence of the given option, or <code>null</code> if the option is not defined.
	@see #OPTION_PATTERN
	*/
	public static String getOption(final String[] arguments, final String optionName)
	{
		for(int i=arguments.length-1; i>=0; --i)	//look at each of the arguments in reverse order
		{
			final Matcher optionMatcher=OPTION_PATTERN.matcher(arguments[i]);	//try to match against this argumnet
			if(optionMatcher.matches())	//if this is an argument
			{
				if(optionName.equals(optionMatcher.group(1)))	//if this is the correct option
				{
					return optionMatcher.group(2);	//return this option
				}
			}
		}
		return null; //show that the option wasn't defined
	}

	/**Returns the given arguments for all occurrences a particular option.
	@param arguments The array of command line arguments.
	@param optionName The name of the option.
	@return A non-<code>null</code> list of arguments of the defined options, if any, in the order encountered.
	@see #OPTION_PATTERN
	*/
	public static List<String> getOptions(final String[] arguments, final String optionName)
	{
		final int argumentCount=arguments.length;
		List<String> options=null;
		for(int i=0; i<argumentCount; ++i)	//look at each of the arguments
		{
			final Matcher optionMatcher=OPTION_PATTERN.matcher(arguments[i]);	//try to match against this argumnet
			if(optionMatcher.matches())	//if this is an argument
			{
				if(optionName.equals(optionMatcher.group(1)))	//if this is the correct option
				{
					if(options==null)	//if we don't yet have a list of options
					{
						options=new ArrayList<String>(); //create a new list for the parameters
					}
					options.add(optionMatcher.group(2));	//add this option
				}
			}
		}
		return options!=null ? options : java.util.Collections.<String>emptyList(); //show that the switch wasn't defined
	}

	/**Checks to see if a particular argument is a switch, without regard to whether it is an option or a flag.
	@param argument The argument to check.
	@return <code>true</code> if the argument is a switch.
	@see #SWITCH_PATTERN
	*/
	public static boolean isSwitch(final String argument)
	{
		return SWITCH_PATTERN.matcher(argument).matches();	//see if the argument matches the switch pattern
	}

	/**Creates a switch argument in the form <code>-<var>switchString</var></code>.
	@param switchString The string to make into a switch.
	@return A command-line switch.
	@deprecated
	*/
	public static String createSwitch(final String switchString)
	{
		return LONG_SWITCH_DELIMITER+switchString;
	}

}