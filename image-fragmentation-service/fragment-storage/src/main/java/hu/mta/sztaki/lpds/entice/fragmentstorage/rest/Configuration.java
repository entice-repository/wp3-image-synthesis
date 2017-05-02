package hu.mta.sztaki.lpds.entice.fragmentstorage.rest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration {
	
	private static final Logger log = LoggerFactory.getLogger(FragmentStorage.class);
	public final static String PROPERTIES_FILE_NAME = "fragment-storage.properties";
	
	static String version = "0.1";
	static String fragmentStorageToken;
	static String fragmentStoragePath;

	static {
		Properties props = new Properties();
		InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(PROPERTIES_FILE_NAME); // emits no exception but null returned
		if (in == null) { log.warn("The system cannot find the file specified. Using defaults."); } 
		else {
			try {
				props.load(in);
				try { in.close(); } catch (IOException e) {}
				log.debug("Reading " + PROPERTIES_FILE_NAME + "...");
				if (props.getProperty("version") != null) version = props.getProperty("version");
				fragmentStorageToken = props.getProperty("fragmentStorageToken");
				fragmentStoragePath = props.getProperty("fragmentStoragePath");
			} catch (IOException e) { log.error("Cannot read properties file: " + PROPERTIES_FILE_NAME, e); }
		}

		// override properties with system properties
		fragmentStorageToken = getSystemProperty("FRAGMENT_STORAGE_TOKEN", fragmentStorageToken);
		fragmentStoragePath = getSystemProperty("FRAGMENT_STORAGE_PATH", fragmentStoragePath);
		
		// use default fragmentStorageToken if unspecified
		if (fragmentStorageToken == null) {
//			fragmentStorageToken = "entice";
			log.warn("fragmentStorageToken unset");
		}

		// use default fragmentStoragePath if unspecified 
		if (fragmentStoragePath == null) {
			String javaTmpDir = System.getProperty("java.io.tmpdir");  
			if (javaTmpDir != null) {
				fragmentStoragePath = javaTmpDir + "/fragments";
				log.warn("Using default fragmentStoragePath: " + fragmentStoragePath);
				if (!new File(fragmentStoragePath).mkdirs()) log.error("Cannot create fragments dir: " + fragmentStoragePath); 
			}
		}
		
		log.info("fragmentStorageToken: " + fragmentStorageToken);
		log.info("fragmentStoragePath: " + fragmentStoragePath);
	}
	private static String getSystemProperty(String propertyName, String defaultValue) {
		return System.getProperty(propertyName) != null ? System.getProperty(propertyName) : 
			(System.getenv(propertyName) != null ? System.getenv(propertyName) : defaultValue); 
	}
}