package com.garretwilson.util;

import java.io.FileNotFoundException;
import java.net.URI;
import java.util.*;
import com.garretwilson.rdf.*;
import com.garretwilson.rdf.dublincore.DCUtilities;

/**An application that by default is a console application.
@author Garret Wilson
*/
public class Application extends DefaultRDFResource 
{

	/**@return The title of the application, or <code>null</code> if there is no title.*/
	public RDFObject getTitle()
	{
		return DCUtilities.getTitle(this);	//return the title object if there is one
	}

	/**The expiration date of the application, or <code>null</code> if there is
		no expiration.
	*/
	private Calendar expiration=null;

		/**The expiration date of the application, or <code>null</code> if there is
			no expiration.
		*/
		public Calendar getExpiration() {return expiration;}

		/**Sets the expiration of the application.
		@param newExpiration The new expiration date, or <code>null</code> if there
			is no expiration.
		*/
		public void setExpiration(final Calendar newExpiration) {expiration=newExpiration;}

	/**The RDF data model of the application, lazily created.*/
	private RDF rdf=null;

	/**@return The RDF data model of the application, lazily created.*/
	public RDF getRDF()
	{
		if(rdf==null)	//if there is no RDF data model
		{
			rdf=new RDF();	//create a new RDF data model
		}
		return rdf;	//return the RDF data model
	}

	/**Reference URI constructor.
	@param referenceURI The reference URI of the application.
	*/
	public Application(final URI referenceURI)
	{
		super(referenceURI);	//construct the parent class
	}

	/**The main application method.
	@return The application status.
	*/ 
	public int main()
	{
		return 0;	//default to returning no error
	}

	/**Checks requirements, permissions, and expirations before starting.
	@return <code>true</code> if the checks succeeded.	
	*/
	public boolean canStart()
	{
			//check the expiration
		if(getExpiration()!=null)	//if there is an expiration date
		{
			final Calendar now=Calendar.getInstance();	//get the current date
			if(now.after(getExpiration()))	//if the application has expired
			{
					//TODO get the web site from dc:source to
					//TODO display a title
				displayError("This version of "+(getTitle()!=null ? getTitle().toString() : "")+" has expired.\nPlease visit http://www.globalmentor.com/software/marmot/\nto download a new copy.");	//G***i18n
				return false;
			}
		}
		return true;	//show that everything went OK
	}

	/**Displays an error message to the user for an exception.
	@param throwable The condition that caused the error.
	*/
	public void displayError(final Throwable throwable)
	{
		Debug.trace(throwable);	//log the error
		displayError(getDisplayErrorMessage(throwable));	//display an error to the user for the throwable
	}
	
	/**Displays the given error to the user
	@param message The error to display. 
	*/
	public void displayError(final String message)
	{
		System.err.println(message);	//display the error in the error output
	}

	/**Constructs a user-presentable error message based on an exception.
	In most cases this is <code>Throwable.getMessage()</code>.
	@param throwable The condition that caused the error.
	@see Throwable#getMessage()
	*/
	public static String getDisplayErrorMessage(final Throwable throwable)
	{
		if(throwable instanceof FileNotFoundException)	//if a file was not found
		{
			return "File not found: "+throwable.getMessage();	//create a message for a file not found G***i18n
		}
		else if(throwable instanceof sun.io.MalformedInputException)	//if there was an error converting characters; G***put this elsewhere, fix for non-Sun JVMs
		{
			return "Invalid character encountered for file encoding.";	//G***i18n
		}
		else  //for any another error
		{
			return throwable.getMessage()!=null ? throwable.getMessage() : throwable.getClass().getName();  //get the throwable message or, on last resource, the name of the class
		}
	}

	/**Starts an application.
	@param application The application to start. 
	@param args The command line arguments.
	@return The application status.
	*/
	public static int run(final Application application, final String[] args)
	{
		try
		{
			initialize(application, args);	//initialize the environment
			if(!application.canStart())	//perform the pre-run checks; if something went wrong, exit
				return -1;	//show that there was a problem
			return application.main();	//run the application
		}
		catch(Throwable throwable)  //if there are any errors
		{
			error(throwable);	//report the error
			return -1;	//show that there was an error
		}
	}

	/**Initializes the environment for the application.
	@param application The application to start. 
	@param args The command line arguments.
	@exception Thrown if anything goes wrong.
	*/
	protected static void initialize(final Application application, final String[] args) throws Exception
	{
		CommandLineArgumentUtilities.configureDebug(args, Debug.getDebug()); //configure debugging based upon the command line arguments
	}

	/**Responds to a throwable error.
	@param throwable The throwable object that caused the error 
	 */
	protected static void error(final Throwable throwable)
	{
		Debug.error(throwable); //report the error TODO fix with better reporting
	}
	
}
