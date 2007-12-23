package com.garretwilson.util;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.prefs.Preferences;
import com.garretwilson.io.*;
import com.garretwilson.lang.*;
import static com.garretwilson.lang.SystemUtilities.*;
import com.garretwilson.net.Authenticable;
import com.garretwilson.net.http.HTTPClient;
import com.garretwilson.rdf.*;
import com.garretwilson.rdf.dublincore.DCUtilities;

/**An application that by default is a console application.
<p>Every application provides a default preference node based upon the
	implementing application class.</p>
<p>If a configuration is provided via <code>setConfiguration()</code>, that
	configuration is automatically loaded and saved.</p>
@param <C> The type of configuration object.
@author Garret Wilson
*/
public abstract class Application<C> extends DefaultRDFResource implements Modifiable 
{

	/**An array containing no arguments.*/
	protected final static String[] NO_ARGUMENTS=new String[0];
	
	/**The authenticator object used to retrieve client authentication.*/
	private Authenticable authenticator=null;

		/**@return The authenticator object used to retrieve client authentication.*/
		public Authenticable getAuthenticator() {return authenticator;}
	
		/**Sets the authenticator object used to retrieve client authentication.
		This version updates the authenticator of the default HTTP client.
		@param authenticable The object to retrieve authentication information regarding a client.
		@see com.garretwilson.net.http.HTTPClient
		*/
		public void setAuthenticator(final Authenticable authenticable)
		{
			if(authenticator!=authenticable)	//if the authenticator is really changing
			{
				authenticator=authenticable;	//update the authenticator
				HTTPClient.getInstance().setAuthenticator(authenticable);	//update the authenticator for HTTP connections on the default HTTP client			
			}
		}
	
	/**Whether the object has been modified; the default is not modified.*/
	private boolean modified=false;

	/**The filename of the configuration file.*/
	public final static String CONFIGURATION_FILENAME="configuration.rdf";

	/**The command-line arguments of the application.*/
	private final String[] args;

		/**@return The command-line arguments of the application.*/
		public String[] getArgs() {return args;}

	/**@return The default user preferences for this frame.
	@exception SecurityException Thrown if a security manager is present and
		it denies <code>RuntimePermission("preferences")</code>.
	*/
	public Preferences getPreferences() throws SecurityException
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

	/**The name of the configuration directory, or <code>null</code> if the default should be used.*/
	private String configurationDirectoryName=null;

		/**@return The name of the configuration directory. If no configuration
		 * directory has been assigned, a default is returned constructed from the
		 * local name of the application class in lowercase, preceded by a full
		 * stop character ('.').*/
		protected String getConfigurationDirectoryName()
		{
			return configurationDirectoryName!=null	//if a configuration directory name has been assigned
					? configurationDirectoryName	//return the configuration directory name
					: String.valueOf(FileConstants.EXTENSION_SEPARATOR)+ClassUtilities.getLocalName(getClass()).toLowerCase();	//otherwise, return ".applicationname"
		}
		
		/**Sets the name of the configuration directory.
		@param configurationDirectoryName The name of the configuration directory,
			or <code>null</code> if the default should be used.
		*/
		protected void setConfigurationDirectoryName(final String configurationDirectoryName) {this.configurationDirectoryName=configurationDirectoryName;}

	/**@return The configuration directory for the application.
	@exception SecurityException if a security manager exists and its <code>checkPropertyAccess</code> method doesn't allow
		access to the user home system property.
	@see SystemUtilities#getUserHomeDirectory()
	@see #getConfigurationDirectoryName()
	*/
	public File getConfigurationDirectory() throws SecurityException
	{
		return new File(getUserHomeDirectory(), getConfigurationDirectoryName());	//return the configuration directory inside the user home directory
	}

	/**@return An object representing the file containing the configuration information.
	@exception SecurityException if a security manager exists and its <code>checkPropertyAccess</code> method doesn't allow
		access to the user home system property.
	@see #getConfigurationDirectory()
	*/
	public File getConfigurationFile() throws SecurityException
	{
		return new File(getConfigurationDirectory(), CONFIGURATION_FILENAME);	//return the configuration file inside the configuration directory		
	}
	
	/**The application configuration, or <code>null</code> if there is no configuration.*/
//G***del when works	private Configuration configuration=null;

		/**@return The application configuration, or <code>null</code> if there is no configuration.*/
//G***del when works		public Configuration getConfiguration() {return configuration;}

		/**Sets the application configuration.
		@param config The application configuration, or <code>null</code> if there
			should be no configuration.
		*/
//G***del when works		protected void setConfiguration(final Configuration config) {configuration=config;}

	/**The application configuration, or <code>null</code> if there is no configuration.*/
	private C configuration=null;

		/**@return The application configuration, or <code>null</code> if there is no configuration.*/
		public C getConfiguration() {return configuration;}

		/**Sets the application configuration.
		@param config The application configuration, or <code>null</code> if there
			should be no configuration.
		*/
		private void setConfiguration(final C config) {configuration=config;}

	/**The configuration strategy, or <code>null</code> if there is no configuration storage.*/
//G***del	private ConfigurationStrategy configurationStrategy=null;

		/**@return The configuration strategy, or <code>null</code> if there is no configuration storage.*/
//G***del		public ConfigurationStrategy getConfigurationStrategy() {return configurationStrategy;}

		/**Sets the configuration strategy.
		@param strategy The configuration strategy, or <code>null</code> if
			there should be no configuration.
		*/
//G***del		protected void setConfigurationStrategy(final ConfigurationStrategy strategy) {configurationStrategy=strategy;}

	/**The configuration storage I/O kit, or <code>null</code> if there is no configuration storage.*/
	private IOKit<C> configurationIOKit=null;

		/**@return The configuration storage I/O kit, or <code>null</code> if there is no configuration storage.*/
		public IOKit<C> getConfigurationIOKit() {return configurationIOKit;}

		/**Sets the configuration storage I/O kit.
		@param ioKit The configuration storage I/O kit, or <code>null</code> if
			there should be no configuration.
		*/
		protected void setConfigurationIOKit(final IOKit<C> ioKit) {configurationIOKit=ioKit;}

	/**The configuration storage strategy, or <code>null</code> if there is no configuration storage.*/
//G***del	private ModelStorage configurationStorage=null;

		/**@return The configuration storage strategy, or <code>null</code> if there is no configuration storage.*/
//G***del		public ModelStorage getConfigurationStorage() {return configurationStorage;}

		/**Sets the configuration storage strategy.
		@param storage The configuration storage strategy, or <code>null</code> if
			there should be no configuration.
		*/
//G***del		protected void setConfigurationStorage(final ModelStorage storage) {configurationStorage=storage;}

		/**The name of the properties file, or <code>null</code> if the default should be used.*/
//TODO del		private String propertiesFileName=null;

			/**@return The name of the properties file. If no properties file
			 	name has been assigned, a default is returned constructed from the
			 	local name of the application class in lowercase with an extension of "properties".
			*/
/*TODO del
			protected String getPropertiesFileName()
			{
				return propertiesFileName!=null	//if a properties file name has been assigned
						? propertiesFileName	//return the properties file name
						: addExtension(ClassUtilities.getLocalName(getClass()).toLowerCase(), PROPERTIES_EXTENSION);	//otherwise, return "applicationname.properties"
			}
*/
			
			/**Sets the name of the properties file.
			@param propertiesFileName The name of the properties file,
				or <code>null</code> if the default should be used.
			*/
//TODO del			protected void setPropertiesFileName(final String propertiesFileName) {this.propertiesFileName=propertiesFileName;}

	/**@return An object representing the file containing the properties information.
	@exception SecurityException if a security manager exists and its <code>checkPropertyAccess</code> method doesn't allow
		access to the user home system property.
	@see #getConfigurationDirectory()
	@see #getPropertiesFileName()
	*/
/*TODO del
	public File getPropertiesFile() throws SecurityException
	{
		return new File(getConfigurationDirectory(), getPropertiesFileName());	//return the properties file inside the configuration directory		
	}
*/

	/**Reference URI constructor.
	@param referenceURI The reference URI of the application.
	*/
	public Application(final URI referenceURI)
	{
		this(referenceURI, NO_ARGUMENTS);	//construct the class with no arguments
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
		loadConfiguration();	//load the configuration
		if(getConfiguration()==null)	//if we were unable to load the configuration
		{
			final C configuration=createDefaultConfiguration();	//create a default configuration
			if(configuration!=null)	//if we created a default configuration
			{
				setConfiguration(configuration);	//set the configuration
			}
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

	/**Creates a default configuration if one cannot be loaded.
	This version returns <code>null</code>.
	@return A default configuration, or <code>null</code> if the application
		does not need a configuration.
	*/
	protected C createDefaultConfiguration()
	{
		return null;	//this version doesn't create a configuration
	}

	/**Loads configuration information.
	@throws IOException if there is an error loading the configuration information.
	*/
	protected void loadConfiguration() throws IOException
	{
		final IOKit<C> configurationIOKit=getConfigurationIOKit();	//see if we can access the configuration
		if(configurationIOKit!=null)	//if we can load application configuration information
		{
			C configuration=null;	//we'll try to get the configuration from somewhere
			try
			{
				final File configurationFile=getConfigurationFile();	//get the configuration file
				if(Files.checkExists(configurationFile))	//if there is a configuration file (or a backup configuration file)
				{
					configuration=configurationIOKit.load(configurationFile.toURI());	//ask the I/O kit to load the configuration file
				}
			}
			catch(SecurityException securityException)	//if we can't access the configuration file
			{
				Debug.warn(securityException);	//warn of the security problem			
			}
			setConfiguration(configuration);	//set the configuration to whatever we found
			setModified(false);	//the application has not been modified, as its configuration has just been loaded
		}
	}

	/**Saves the configuration.
	@throws IOException if there is an error saving the configuration information.
	*/
	public void saveConfiguration() throws IOException
	{
		final IOKit<C> configurationIOKit=getConfigurationIOKit();	//see if we can access the configuration
		final C configuration=getConfiguration();	//get the configuration
		if(configurationIOKit!=null && configuration!=null)	//if we can save application configuration information, and there is configuration information to save
		{
			try
			{
				final File configurationFile=getConfigurationFile();	//get the configuration file
				final File configurationDirectory=configurationFile.getParentFile();	//get the directory of the file
				if(!configurationDirectory.exists() || !configurationDirectory.isDirectory())	//if the directory doesn't exist as a directory
				{
					Files.mkdirs(configurationDirectory);	//create the directory
				}
				final File tempFile=Files.getTempFile(configurationFile);  //get a temporary file to write to

				final File backupFile=Files.getBackupFile(configurationFile);  //get a backup file
				configurationIOKit.save(configuration, tempFile.toURI());	//ask the I/O kit to save the configuration to the temporary file
				Files.moveFile(tempFile, configurationFile, backupFile); //move the temp file to the normal file, creating a backup if necessary
				setModified(false);	//the application has not been modified, as its configuration has just been saved
			}
			catch(SecurityException securityException)	//if we can't access the configuration file
			{
				Debug.warn(securityException);	//warn of the security problem			
			}
		}
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
	public static <C> int run(final Application<C> application, final String[] args)
	{
		int result=0;	//start out assuming a neutral result TODO use a constant and a unique value
		try
		{
			initialize(application, args);	//initialize the environment
			application.initialize();	//initialize the application
			if(application.canStart())	//perform the pre-run checks; if everything went OK
			{
				result=application.main();	//run the application
			}
			else	//if something went wrong
			{
				result=-1;	//show that we couldn't start TODO use a constant and a unique value
			}
		}
		catch(final Throwable throwable)  //if there are any errors
		{
			result=-1;	//show that there was an error TODO use a constant and a unique value
			application.displayError(throwable);	//report the error
		}
		if(result<0)	//if we something went wrong, exit (if everything is going fine, keep running, because we may have a server or frame running)
		{
			try
			{
				application.exit(result);	//exit with the result (we can't just return, because the main frame, if initialized, will probably keep the thread from stopping)
			}
			catch(final Throwable throwable)  //if there are any errors
			{
				result=-1;	//show that there was an error during exit TODO use a constant and a unique value
				application.displayError(throwable);	//report the error
			}
			finally
			{
				System.exit(result);	//provide a fail-safe way to exit		
			}
		}
		return result;	//always return the result		
	}

	/**Initializes the environment for the application.
	@param application The application to start. 
	@param args The command line arguments.
	@exception Exception Thrown if anything goes wrong.
	*/
	protected static void initialize(final Application application, final String[] args) throws Exception
	{
		CommandLineArgumentUtilities.configureDebug(args); //configure debugging based upon the command line arguments
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

	
	/**Determines whether the application can exit.
	This method may query the user.
	If the application has been modified, the configuration is saved if possible.
	If there is no configuration I/O kit, no action is taken.
	If an error occurs, the user is notified.
	@return <code>true</code> if the application can exit, else <code>false</code>.
	*/
	protected boolean canExit()
	{
			//if there is configuration information and it has been modified
		if(isModified())	//if the application has been modified
		{
			try
			{
				saveConfiguration();	//save the configuration
			}
			catch(IOException ioException)	//if there is an error saving the configuration
			{
				displayError(ioException);	//alert the user of the error
/*TODO fix for Swing
					//ask if we can close even though we can't save the configuration information
				canClose=JOptionPane.showConfirmDialog(this,
					"Unable to save configuration information; are you sure you want to close?",
					"Unable to save configuration", JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;	//G***i18n
*/
			}
		}
		return true;	//show that we can exit
	}

	/**Exits the application with no status.
	Convenience method which calls <code>exit(int)</code>.
	@see #exit(int)
	*/
	public final void exit()
	{
		exit(0);	//exit with no status
	}
	
	/**Exits the application with the given status.
	This method first checks to see if exit can occur.
	To add to exit functionality, <code>performExit()</code> should be overridden rather than this method.
	@param status The exit status.
	@see #canExit()
	@see #performExit(int)
	*/
	public final void exit(final int status)
	{
		if(canExit())	//if we can exit
		{
			try
			{
				performExit(status);	//perform the exit
			}
			catch(final Throwable throwable)  //if there are any errors
			{
				displayError(throwable);	//report the error
			}			
			System.exit(-1);	//provide a fail-safe way to exit, indicating an error occurred		
		}
	}

	/**Exits the application with the given status without checking to see if exit should be performed.
	@param status The exit status.	
	@exception Exception Thrown if anything goes wrong.
	*/
	protected void performExit(final int status) throws Exception
	{
		System.exit(status);	//close the program with the given exit status		
	}

	/**@return Whether the object been modified.*/
	public boolean isModified() {return modified;}

	/**Sets whether the object has been modified.
	This is a bound property.
	@param newModified The new modification status.
	*/
	public void setModified(final boolean newModified)
	{
		final boolean oldModified=modified; //get the old modified value
		if(oldModified!=newModified)  //if the value is really changing
		{
			modified=newModified; //update the value
				//show that the modified property has changed
			firePropertyChange(MODIFIED_PROPERTY, Boolean.valueOf(oldModified), Boolean.valueOf(newModified));
		}
	}

}
