package com.garretwilson.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.prefs.Preferences;
import com.garretwilson.rdf.*;
import com.garretwilson.rdf.dublincore.DCUtilities;

/**An application that by default is a console application.
<p>Every application provides a default preference node based upon the
	implementing application class.</p>
<p>If a configuration is provided via <code>setConfiguration()</code>, that
	configuration is automatically loaded and saved.</p>
@author Garret Wilson
*/
public abstract class Application extends DefaultRDFResource 
{

	/**The command-line arguments of the application.*/
	private final String[] args;

		/**@return The command-line arguments of the application.*/
		protected String[] getArgs() {return args;}

	/**@return The default user preferences for this frame.
	@exception SecurityException Thrown if a security manager is present and
		it denies <code>RuntimePermission("preferences")</code>.
	*/
	public Preferences getPreferences()
	{
		return Preferences.userNodeForPackage(getClass());	//return the user preferences node for whatever class extends this one 
	}

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

	/**The application configuration, or <code>null</code> if there is no configuration.*/
	private Configuration configuration=null;

		/**@return The application configuration, or <code>null</code> if there is no configuration.*/
		public Configuration getConfiguration() {return configuration;}

		/**Sets the application configuration.
		@param config The application configuration, or <code>null</code> if there
			should be no configuration.
		*/
		protected void setConfiguration(final Configuration config) {configuration=config;}

	/**Reference URI constructor.
	@param referenceURI The reference URI of the application.
	*/
	public Application(final URI referenceURI)
	{
		this(referenceURI, new String[]{});	//construct the class with no arguments
	}

	/**Reference URI and arguments constructor.
	@param referenceURI The reference URI of the application.
	@param args The command line arguments.
	*/
	public Application(final URI referenceURI, final String[] args)
	{
		super(referenceURI);	//construct the parent class
		this.args=args;	//save the arguments
	}

	/**Initializes the application.
	This method is called after construction but before application execution.
	This version loads the configuration information, if it exists.
	@exception Exception Thrown if anything goes wrong.
	*/
	public void initialize() throws Exception	//TODO create a flag that only allows initialization once
	{
		if(getConfiguration()!=null && getConfiguration().exists())	//if there is application configuration information
		{
			getConfiguration().retrieve();	//retrieve the configuration information
		}		
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

	/**Stores the configuration if it has been modified.
	If there is no configuration, no action is taken.
	If an error occurs, the user is notified.
	@return <code>false</code> if there was an error storing the configuration,
		else <code>true</code>.
	*/
/*G***del if not needed
	public boolean storeConfiguration()
	{
			//if there is configuration information and it has been modified
		if(getConfiguration()!=null && getConfiguration().isModified())
		{
			try
			{
				getConfiguration().store();	//try to store the configuration information
			}
			catch(IOException ioException)	//if there is an error saving the configuration
			{
				displayError(this, ioException);	//alert the user of the error
					//ask if we can close even though we can't save the configuration information
				canClose=JOptionPane.showConfirmDialog(this,
					"Unable to save configuration information; are you sure you want to close?",
					"Unable to save configuration", JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;	//G***i18n
			}
		}

		try
		{
			getMarmotConfiguration().store();	//store the configuration information
			return true;	//show that saving was successful
		}
		catch(IOException ioException)	//if there is an error storing the configuration
		{
			Debug.error(ioException);	//G***fix
			return false;	//show that we were unable to save the configuration
		}
	}
*/

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
/*TODO this throws a security exception with Java WebStart; see if it's even needed anymore
		else if(throwable instanceof sun.io.MalformedInputException)	//if there was an error converting characters; G***put this elsewhere, fix for non-Sun JVMs
		{
			return "Invalid character encountered for file encoding.";	//G***i18n
		}
*/
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
			application.initialize();	//initialize the application
			if(!application.canStart())	//perform the pre-run checks; if something went wrong, exit
				return -1;	//show that there was a problem
			return application.main();	//run the application
		}
		catch(Throwable throwable)  //if there are any errors
		{
			application.displayError(throwable);	//report the error
			return -1;	//show that there was an error
		}
	}

	/**Initializes the environment for the application.
	@param application The application to start. 
	@param args The command line arguments.
	@exception Exception Thrown if anything goes wrong.
	*/
	protected static void initialize(final Application application, final String[] args) throws Exception
	{
		CommandLineArgumentUtilities.configureDebug(args, Debug.getDebug()); //configure debugging based upon the command line arguments
	}

	/**Responds to a throwable error.
	@param throwable The throwable object that caused the error 
	*/
/*G***del if not needed
	protected static void error(final Throwable throwable)
	{
		Debug.error(throwable); //report the error
	}
*/

	/**Application configuration information.*/
	public interface Configuration extends Modifiable
	{

		/**Checks to see if the configuration information exists.
		@return <code>true</code> if the configuration information exists,
			else <code>false</code> if configuration information is not available.
		@exception IOException Thrown if there is a problem determining if the information exists.
		*/
		public boolean exists() throws IOException;

		/**Stores the configuration.
		<p>After this method, the <code>modified</code> bound property will be
			<code>false</code>.</p>
		@exception IOException Thrown if there is a problem storing the information.
		*/
		public void store() throws IOException;

		/**Retrieves the configuration.
		<p>After this method, the <code>modified</code> bound property will be
			<code>false</code>.</p>
		@exception IOException Thrown if there is a problem retrieving the information.
		*/
		public void retrieve() throws IOException;

	}
}
