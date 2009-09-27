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

import java.util.*;
import java.util.prefs.Preferences;

import com.globalmentor.net.*;
import com.globalmentor.net.http.HTTPClient;
import com.globalmentor.urf.URFResource;

/**An application that by default is a console application.
<p>Every application provides a default preference node based upon the implementing application class.</p>
@author Garret Wilson
*/
public interface Application extends URFResource
{

	/**An array containing no arguments.*/
	public final static String[] NO_ARGUMENTS=new String[0];
	
	/**@return The authenticator object used to retrieve client authentication.*/
	public Authenticable getAuthenticator();
	
	/**Sets the authenticator object used to retrieve client authentication.
	@param authenticable The object to retrieve authentication information regarding a client.
	@see HTTPClient
	*/
	public void setAuthenticator(final Authenticable authenticable);

	/**@return The command-line arguments of the application.*/
	public String[] getArgs();

	/**@return The default user preferences for this application.
	@throws SecurityException Thrown if a security manager is present and it denies <code>RuntimePermission("preferences")</code>.
	*/
	public Preferences getPreferences() throws SecurityException;

	/**@return The expiration date of the application, or <code>null</code> if there is no expiration.*/
	public Date getExpirationDate();

	/**Initializes the application.
	This method is called after construction but before application execution.
	@throws Exception Thrown if anything goes wrong.
	*/
	public void initialize() throws Exception;

	/**The main application method.
	@return The application status.
	*/ 
	public int main();

	/**Checks requirements, permissions, and expirations before starting.
	@return <code>true</code> if the checks succeeded.	
	*/
	public boolean canStart();

	/**Displays an error message to the user for an exception.
	@param throwable The condition that caused the error.
	*/
	public void displayError(final Throwable throwable);
	
	/**Displays the given error to the user
	@param message The error to display. 
	*/
	public void displayError(final String message);

	/**Exits the application with no status.
	Convenience method which calls {@link #exit(int)}.
	@see #exit(int)
	*/
	public void exit();
	
	/**Exits the application with the given status.
	This method first checks to see if exit can occur.
	@param status The exit status.
	@see #canExit()
	@see #performExit(int)
	*/
	public void exit(final int status);

}
