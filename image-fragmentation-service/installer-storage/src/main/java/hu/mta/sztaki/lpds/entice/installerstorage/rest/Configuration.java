package hu.mta.sztaki.lpds.entice.installerstorage.rest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration {
	
	private static final Logger log = LoggerFactory.getLogger(Installers.class);
	public final static String PROPERTIES_FILE_NAME = "installer-storage.properties";

	public static String version = "0.1";
	static String installerStorageToken;
	public static String installerStoragePath; // must be readable/writable for user tomcat7 

	static {
		Properties prop = new Properties();
		InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(PROPERTIES_FILE_NAME); // emits no exception but null returned
		if (in == null) { log.warn("The system cannot find the file specified. Using defaults."); } 
		else {
			try {
				prop.load(in);
				try { in.close(); } catch (IOException e) {}
				log.info("Reading " + PROPERTIES_FILE_NAME + "...");
				if (prop.getProperty("version") != null) version = prop.getProperty("version");
				installerStorageToken = prop.getProperty("installerStorageToken");
				installerStoragePath = prop.getProperty("installerStoragePath");
			} catch (IOException e) { log.error("Cannot read properties file: " + PROPERTIES_FILE_NAME, e); }
		} 
		
		// override properties with system properties
		installerStorageToken = getSystemProperty("INSTALLER_STORAGE_TOKEN", installerStorageToken);
		installerStoragePath = getSystemProperty("INSTALLER_STORAGE_PATH", installerStoragePath);

		// use default token if unspecified
		if (installerStorageToken == null) {
//			installerStorageToken = "entice";
			log.warn("installerStorageToken unset");
		}

		// use default installerStoragePath dir if unspecified 
		if (installerStoragePath == null) {
			String javaTmpDir = System.getProperty("java.io.tmpdir");  
			if (javaTmpDir != null) {
				installerStoragePath = javaTmpDir + "/installers";
				log.warn("Using default fragmentStoragePath");
				if (!new File(installerStoragePath).mkdirs()) log.error("Cannot create installers dir: " + installerStoragePath); 
			}
		}
		
		log.info("installerStorageToken: " + installerStorageToken);
		log.info("installerStoragePath: " + installerStoragePath);
	}
	private static String getSystemProperty(String propertyName, String defaultValue) {
		return System.getProperty(propertyName) != null ? System.getProperty(propertyName) : 
			(System.getenv(propertyName) != null ? System.getenv(propertyName) : defaultValue); 
	}
}