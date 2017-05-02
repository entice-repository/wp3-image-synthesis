package hu.mta.sztaki.lpds.entice.virtualimagecomposer.rest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration {
	
	private static final Logger log = LoggerFactory.getLogger(VirtualImageComposer.class);
	public final static String PROPERTIES_FILE_NAME = "virtual-image-composer.properties";
	
	public static String version = "0.1";
	public static String virtualImageManagerRestUrl;

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
				virtualImageManagerRestUrl = prop.getProperty("virtualImageManagerRestUrl");
			} catch (IOException e) { log.error("Cannot read properties file: " + PROPERTIES_FILE_NAME, e); }
		}
		
		// override properties with system properties
		virtualImageManagerRestUrl = getSystemProperty("VIRTUAL_IMAGE_MANAGER_REST_URL", virtualImageManagerRestUrl);

		// use default virtualImageManagerRestUrl dir if unspecified 
		if (virtualImageManagerRestUrl == null) {
			virtualImageManagerRestUrl = "http://localhost:8080/virtual-image-manager/rest";
			log.warn("Using default virtualImageManagerRestUrl");
		}
		
		log.info("virtualImageManagerRestUrl: " + virtualImageManagerRestUrl);
	}
	private static String getSystemProperty(String propertyName, String defaultValue) {
		return System.getProperty(propertyName) != null ? System.getProperty(propertyName) : 
			(System.getenv(propertyName) != null ? System.getenv(propertyName) : defaultValue); 
	}
}