package hu.mta.sztaki.lpds.entice.virtualimagemanager.rest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration {
	
	private static final Logger log = LoggerFactory.getLogger(VirtualImages.class);
	public final static String PROPERTIES_FILE_NAME = "virtual-image-manager.properties";
	
	public static String version = "0.1";
	public static String virtualImageManagerToken;
	public static String virtualImageDecomposerToken;
	public static String virtualImageDecomposerRestURL;
	public static String installerStorageURL;

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
				virtualImageManagerToken = prop.getProperty("virtualImageManagerToken");
				virtualImageDecomposerToken = prop.getProperty("virtualImageDecomposerToken");
				virtualImageDecomposerRestURL = prop.getProperty("virtualImageDecomposerRestURL");
				installerStorageURL = prop.getProperty("installerStorageURL");
			} catch (IOException e) { log.error("Cannot read properties file: " + PROPERTIES_FILE_NAME, e); }
		} 
		
		// override properties with system properties
		virtualImageManagerToken = getSystemProperty("VIRTUAL_IMAGE_MANAGER_TOKEN", virtualImageManagerToken);
		virtualImageDecomposerToken = getSystemProperty("VIRTUAL_IMAGE_DECOMPOSER_TOKEN", virtualImageDecomposerToken);
		virtualImageDecomposerRestURL = getSystemProperty("VIRTUAL_IMAGE_DECOMPOSER_REST_URL", virtualImageDecomposerRestURL);
		installerStorageURL = getSystemProperty("INSTALLER_STORAGE_URL", installerStorageURL);
		
		// use default virtualImageManagerToken if unspecified
		if (virtualImageManagerToken == null) {
//			virtualImageManagerToken = "entice";
			log.warn("virtualImageManagerToken unset");
		}

		// use default virtualImageDecomposerToken if unspecified
		if (virtualImageDecomposerToken == null) {
			virtualImageDecomposerToken = "entice";
			log.warn("Using default virtualImageDecomposerToken");
		}
		
		// use default virtualImageDecomposerRestURL if unspecified
		if (virtualImageDecomposerRestURL == null) {
			virtualImageDecomposerRestURL = "http://localhost:8080/virtual-image-decomposer/rest";
			log.warn("Using default virtualImageDecomposerRestURL");
		}

		// use default installerStorageURL if unspecified
		if (installerStorageURL == null) {
			installerStorageURL = "http://localhost:8080/installer-storage/rest";
			log.warn("Using default installerStorageURL");
		}

		log.info("virtualImageManagerToken: " + virtualImageManagerToken);
		log.info("virtualImageDecomposerRestURL: " + virtualImageDecomposerRestURL);
		log.info("virtualImageDecomposerToken: " + virtualImageDecomposerToken);
		log.info("installerStorageURL: " + installerStorageURL);
	}
	private static String getSystemProperty(String propertyName, String defaultValue) {
		return System.getProperty(propertyName) != null ? System.getProperty(propertyName) : 
			(System.getenv(propertyName) != null ? System.getenv(propertyName) : defaultValue); 
	}
}