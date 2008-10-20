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
import java.util.ArrayList;
import java.util.List;

import com.globalmentor.util.Debug;

/**Constants and utilities for accessing command line arguments.
@author Garret Wilson
*/
public class CommandLineArguments
{

	/**The hyphen switch character '-'.*/
	public final static String HYPHEN_SWITCH_CHAR="-";
	/**The slash switch character '/'.*/
	public final static String SLASH_SWITCH_CHAR="/";
	/**The characters used to indicate a switch ('-' or '/').*/
	public final static String SWITCH_CHARS=""+HYPHEN_SWITCH_CHAR+SLASH_SWITCH_CHAR;
	/**The debug switch, which turns on debug.
	@see #LOG_SWITCH
	*/
	public final static String DEBUG_SWITCH="debug";
	/**The debug level switch, which is an integer representing the ORed levels of debugging that should be logged;  should be used in conjuction with {@link #DEBUG_SWITCH}.
	@see #DEBUG_SWITCH
	*/
	public final static String DEBUG_LEVEL_SWITCH="level";
	/**The debug visible switch, which turns on visible debugging; should be used in conjuction with {@link #DEBUG_SWITCH}.
	@see #DEBUG_SWITCH
	*/
	public final static String DEBUG_VISIBLE_SWITCH="visible";
	/**The file switch, which specifies an output file.*/
	public final static String FILE_SWITCH="file";
	/**The help switch, which turns on help.*/
	public final static String HELP_SWITCH="help";
	/**The log switch, which turns on debugging; should be used in conjuction with {@link #DEBUG_SWITCH}.
	@see #DEBUG_SWITCH
	*/
	public final static String LOG_SWITCH="log";
	/**The question mark switch, which is a synonym for help.*/
	public final static String QUESTION_SWITCH="?";

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
		Debug.setDebug(isDebug(argumentArray)); //turn debug on or off, based upon the arguments
		final Debug.ReportLevel reportLevel=getDebugLevel(argumentArray);  //see what level is indicated
		if(reportLevel!=null)  //if a valid report level is indicated
			Debug.setMinimumReportLevel(reportLevel); //set the reporting level for debug logging
		Debug.setVisible(isDebugVisible(argumentArray)); //turn debug visibility on or off, based upon the arguments
		final String logFilename=getLogParameter(argumentArray);  //see if there is a log parameter
		if(logFilename!=null) //if there is a log parameter specified, create a new file and specify our output should go there
			Debug.setOutput(new File(logFilename));
	}

	/**Returns whether the debug switch ("debug") is turned on.
	@param argumentArray The array of command line arguments.
	@return <code>true</code> if the "debug" switch is defined.
	@see #DEBUG_SWITCH
	*/
	public static boolean isDebug(final String[] argumentArray)
	{
		return hasParameter(argumentArray, DEBUG_SWITCH);  //return whether the debug switch is defined
	}

	/**Returns the value of the debug level.
	@param argumentArray The array of command line arguments.
	@return The minimum level specified by the debug level switch, or <code>null</code> if the debug
		level is not specified or if it is invalid.
	@see #DEBUG_LEVEL_SWITCH
	*/
	public static Debug.ReportLevel getDebugLevel(final String[] argumentArray)
	{
		final String levelString=getParameter(argumentArray, DEBUG_LEVEL_SWITCH);  //get the level as a string
		if(levelString!=null) //if there is a level
		{
			try
			{
			  return Debug.ReportLevel.valueOf(levelString); //return the value as an integer
			}
			catch(IllegalArgumentException illegalArgumentException) {} //ignore illegal argument errors and just return the default, below
		}
		return null;  //show that we couldn't find a valid level
	}


	/**Returns whether the visible debug switch ("visible") is turned on.
	@param argumentArray The array of command line arguments.
	@return <code>true</code> if the "visible" switch is defined.
	@see #DEBUG_VISIBLE_SWITCH
	*/
	public static boolean isDebugVisible(final String[] argumentArray)
	{
		return hasParameter(argumentArray, DEBUG_VISIBLE_SWITCH);  //return whether the debug visible switch is defined
	}

	/**Returns the parameter of the file switch ("file").
	@param argumentArray The array of command line arguments.
	@return The parameter if the file switch is defined and it has a parameter,
		else <code>null</code> if the switch is not defined or it has no parameter.
	@see #FILE_SWITCH
	@see #getParameter
	*/
	public static String getFileParameter(final String[] argumentArray)
	{
		return getParameter(argumentArray, FILE_SWITCH);  //return the parameter of the file switch
	}

	/**Returns whether the help switch ("help" or "?") is turned on.
	@param argumentArray The array of command line arguments.
	@return <code>true</code> if the "debug" or "?" switch is defined.
	@see #HELP_SWITCH
	@see #QUESTION_SWITCH
	*/
	public static boolean isHelp(final String[] argumentArray)
	{
		return hasParameter(argumentArray, HELP_SWITCH) ||
		  hasParameter(argumentArray, QUESTION_SWITCH);  //return whether one of the debug switches is defined
	}

	/**Returns the parameter of the log switch ("log").
	@param argumentArray The array of command line arguments.
	@return The parameter if the file switch is defined and it has a parameter,
		else <code>null</code> if the switch is not defined or it has no parameter.
	@see #LOG_SWITCH
	@see #getParameter
	*/
	public static String getLogParameter(final String[] argumentArray)
	{
		return getParameter(argumentArray, LOG_SWITCH);  //return the parameter of the log switch
	}

	/**Searches the argument array to see if a particular switch is defined.
		This comparison is case sensitive.
	@param argumentArray The array of command line arguments.
	@param switchName The name of the argument which may be defined.
	@return <code>true</code> if the argument is defined, else <code>false</code>.
	*/
	public static boolean hasParameter(final String[] argumentArray, final String switchName)
	{
		for(int i=0; i<argumentArray.length; ++i)	//look at each of the arguments
		{
			if(isSwitch(argumentArray[i], switchName))  //if this is the requested switch name TODO do we want to trim the argument, first?
				return true;  //show that we found the switch
		}
		return false; //show that the switch wasn't defined
	}

	/**Searches the argument array to find the first parameter of a particular switch.
		A parameter is assumed to be the non-switch argument following a particular switch.
	@param argumentArray The array of command line arguments.
	@param switchName The name of the argument which may have a parameter.
	@return The parameter if the argument is defined and it has a parameter, else
		<code>null</code> if the argument is not defined or it has no parameter.
	@see #isSwitch
	*/
	public static String getParameter(final String[] argumentArray, final String switchName)
	{
		return getParameter(argumentArray, argumentArray.length, switchName);  //get the parameter, examining all the arguments
	}

	/**Searches the argument array to find the first parameter of a particular
		switch, examining the specified number of arguments.
		A parameter is assumed to be the non-switch argument following a particular switch.
	@param argumentArray The array of command line arguments.
	@param argumentCount The number of arguments to check. This can be used so that
		particular arguments are ignored.
	@param switchName The name of the argument which may have a parameter.
	@return The parameter if the argument is defined and it has a parameter, else
		<code>null</code> if the argument is not defined or it has no parameter.
	@see #isSwitch
	*/
	public static String getParameter(final String[] argumentArray, final int argumentCount, final String switchName)
	{
		final List<String> parameterList=getParameters(argumentArray, argumentCount, switchName);  //get all the parameters for this switch
		if(parameterList!=null && parameterList.size()>0) //if there is at least one parameter
			return parameterList.get(0);  //return the first parameter
		else  //if the switch could not be found, or if it had no parameters
			return null; //show that the switch wasn't defined or it didn't have a parameter
	}

	/**Returns all parameters of a particular switch.
		Parameters are assumed to be any non-switch argument following a particular switch.
	@param argumentArray The array of command line arguments.
	@param switchName The name of the argument which may have a parameter.
	@return A non-<code>null</code> list of parameters if the argument is defined,
		or <code>null</code> if the argument is not defined.
	@see #isSwitch
	*/
	public static List<String> getParameters(final String[] argumentArray, final String switchName)
	{
		return getParameters(argumentArray, argumentArray.length, switchName);  //get the parameters, looking at all the arguments
	}

	/**Returns all parameters of a particular switch, examining the specified number of arguments.
		Parameters are assumed to be any non-switch argument following a particular switch.
	@param argumentArray The array of command line arguments.
	@param argumentCount The number of arguments to check. This can be used so that
		particular arguments are ignored.
	@param switchName The name of the argument which may have a parameter.
	@return A non-<code>null</code> list of parameters if the argument is defined,
		or <code>null</code> if the argument is not defined.
	@see #isSwitch
	*/
	public static List<String> getParameters(final String[] argumentArray, final int argumentCount, final String switchName)
	{
		for(int i=0; i<argumentCount; ++i)	//look at each of the arguments
		{
			if(isSwitch(argumentArray[i], switchName))  //if this is the requested switch name TODO do we want to trim the argument, first?
			{
				final List<String> parameterList=new ArrayList<String>(); //create a new list for the parameters
				while(++i<argumentCount) //look at all the arguments after this one
				{
					final String argument=argumentArray[i]; //get a reference to this argument
					if(!argument.startsWith("-")) //if this isn't a switch, it's a parameter (don't use isSwitch(), which will not allow Windows root directory arguments using '\')
						parameterList.add(argument);  //add the argument to the list of parameters
					else  //if this is a switch, we've ran out of parameters
						break;  //stop looking for more parameters
				}
				return parameterList; //return the list of parameters
			}
		}
		return null; //show that the switch wasn't defined
	}

	/**Checks to see if a particular argument is a switch, and if so, whether it
		matches the expected switch name.
	@param argument The argument to check.
	@param expectedSwitch The name with which to compare the argument, if the
		argument is a switch.
	@return <code>true</code> if the argument is a switch and it matches the
		expected switch name.
	*/
	protected static boolean isSwitch(final String argument, final String expectedSwitch)
	{
		if(isSwitch(argument))  //if this argument is a switch
		{
			final String switchName=argument.substring(1);  //remove the switch character
			if(switchName.equals(expectedSwitch)) //if this is the requested switch
				return true;  //show that we found the switch
		}
		return false; //show that this was not a switch, or it wasn't the one we were looking for
	}

	/**Checks to see if a particular argument is a switch.
	@param argument The argument to check.
	@return <code>true</code> if the argument is a switch.
	*/
	public static boolean isSwitch(final String argument)
	{
		return SWITCH_CHARS.indexOf(argument.charAt(0))!=-1; //return true if this argument starts with a switch character
	}

	/**Creates a switch argument in the form <code>-<var>switchString</var></code>.
	@param switchString The string to make into a switch.
	@return A command-line switch.
	*/
	public static String createSwitch(final String switchString)
	{
		return HYPHEN_SWITCH_CHAR+switchString;	//prepend a hyphen to the string
	}



}