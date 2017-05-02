package hu.mta.sztaki.lpds.entice.virtualimagedecomposer.rest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration {
	
	private static final Logger log = LoggerFactory.getLogger(VirtualImageDecomposer.class);
	public final static String PROPERTIES_FILE_NAME = "virtual-image-decomposer.properties";
	
	static String version = "0.1";
	static String virtualImageDecomposerToken;
	static String virtualImageDecomposerPath; // must be readable/writable for user tomcat7 
	static String scriptsDir; // must be readable/writable for user tomcat7 
	static String fragmentStorageUrl;
	static String fragmentStorageToken;
	static String installerStorageUrl; 
	static String virtualImageComposerRestUrl; 
	static boolean testMode = false; // do not allow deletion of working dirs 
	static String s3Endpoint;
	static String s3BucketName;
	static String s3AccessKey;
	static String s3SecretKey;
	
	public static final String MAIN_SCRIPT = "main.sh";

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
				virtualImageDecomposerToken = prop.getProperty("virtualImageDecomposerToken");
				virtualImageDecomposerPath = prop.getProperty("virtualImageDecomposerPath");
				fragmentStorageUrl = prop.getProperty("fragmentStorageUrl");
				fragmentStorageToken = prop.getProperty("fragmentStorageToken");
				installerStorageUrl = prop.getProperty("installerStorageUrl");
				virtualImageComposerRestUrl = prop.getProperty("virtualImageComposerRestUrl");
				testMode = "true".equals(prop.getProperty("testMode"));
				s3Endpoint = prop.getProperty("s3Endpoint");
				s3BucketName = prop.getProperty("s3BucketName");
				s3AccessKey = prop.getProperty("s3AccessKey");
				s3SecretKey = prop.getProperty("s3SecretKey");
			} catch (IOException e) { log.error("Cannot read properties file: " + PROPERTIES_FILE_NAME, e); }
		} 
		
		virtualImageDecomposerToken = getSystemProperty("VIRTUAL_IMAGE_DECOMPOSER_TOKEN", virtualImageDecomposerToken);
		virtualImageDecomposerPath = getSystemProperty("VIRTUAL_IMAGE_DECOMPOSER_PATH", virtualImageDecomposerPath);
		fragmentStorageUrl = getSystemProperty("FRAGMENT_STORAGE_URL", fragmentStorageUrl);
		fragmentStorageToken = getSystemProperty("FRAGMENT_STORAGE_TOKEN", fragmentStorageToken);
		installerStorageUrl = getSystemProperty("INSTALLER_STORAGE_URL", installerStorageUrl);
		virtualImageComposerRestUrl = getSystemProperty("VIRTUAL_IMAGE_COMPOSER_REST_URL", virtualImageComposerRestUrl);
		s3Endpoint = getSystemProperty("S3_ENDPOINT", s3Endpoint);
		s3BucketName = getSystemProperty("S3_BUCKET_NAME", s3BucketName);
		s3AccessKey = getSystemProperty("AWS_ACCESS_KEY_ID", s3AccessKey);
		s3SecretKey = getSystemProperty("AWS_SECRET_ACCESS_KEY", s3SecretKey);

		// default s3 storage configuration
		if (s3Endpoint == null) s3Endpoint = "";
		if (s3BucketName == null) s3BucketName = "";
		if (s3AccessKey == null) s3AccessKey = "";
		if (s3SecretKey == null) s3SecretKey = "";
		
		// determine scripts dir
		try {
			scriptsDir = Thread.currentThread().getContextClassLoader().getResource("scripts/" + MAIN_SCRIPT).toString();
			if (scriptsDir != null) { 
				if (scriptsDir.startsWith("file:\\")) scriptsDir = scriptsDir.substring("file:\\".length());
				else if (scriptsDir.startsWith("file:/")) scriptsDir = scriptsDir.substring("file:".length());
			} else log.error("Main script resource not found: " + "scripts/" + MAIN_SCRIPT + "");
			scriptsDir = scriptsDir.replaceAll("\\\\", "/"); // convert backslashes to slashes
			scriptsDir = scriptsDir.contains("/") ? scriptsDir.substring(0, scriptsDir.lastIndexOf("/")) : scriptsDir;
		} catch (Throwable x) { // this is a fatal error
			log.error("Cannot determine scripts directory", x);
		}
		
		// use default fragmentStorageToken if unspecified
		if (virtualImageDecomposerToken == null) {
//			virtualImageDecomposerToken = "entice";
			log.warn("virtualImageDecomposerToken unset");
		}

		// use default workingDir if unspecified 
		if (virtualImageDecomposerPath == null) {
			String javaTmpDir = System.getProperty("java.io.tmpdir");  
			if (javaTmpDir != null) {
				virtualImageDecomposerPath = javaTmpDir + "/decomposer";
				log.warn("Using default virtualImageDecomposerPath");
				if (!new File(virtualImageDecomposerPath).mkdirs()) log.error("Cannot create workingDir: " + virtualImageDecomposerPath); 
			}
		}
		if (fragmentStorageUrl == null) { // NOTE: this is a fatal error, launched VMs will not access "localhost" for fragments download
			try { // we assume that fragmentStorage is on the same host
				fragmentStorageUrl = "http://" + InetAddress.getLocalHost().getHostAddress() + ":8080/fragment-storage/rest/fragments";
				log.warn("Using default fragmentStorageUrl");
			} catch (Throwable e) {
				log.error("Cannot determine fragmentStorageUrl", e);
			}
		}
		
		if (fragmentStorageToken == null) {
			log.warn("fragmentStorageToken unset");
		}
		
		if (installerStorageUrl == null) {
			installerStorageUrl = "http://localhost:8080/installer-storage/rest/installers";
			log.warn("Using default installerStorageUrl");
		}
		
		if (virtualImageComposerRestUrl == null) {
			virtualImageComposerRestUrl = "http://localhost:8080/virtual-image-composer/rest";
			log.warn("Using default virtualImageComposerRestUrl");
		}
		
		log.info("virtualImageDecomposerToken: " + virtualImageDecomposerToken);
		log.info("virtualImageDecomposerPath: " + virtualImageDecomposerPath);
		log.info("scriptsDir: " + scriptsDir);
		log.info("mainScript: " + MAIN_SCRIPT);
		log.info("virtualImageComposerRestUrl: " + virtualImageComposerRestUrl);
		log.info("installerStorageUrl: " + installerStorageUrl);
		log.info("fragmentStorageUrl: " + fragmentStorageUrl);
		log.info("fragmentStorageToken: " + fragmentStorageToken);
		log.info("s3Endpoint: " + s3Endpoint);
		log.info("s3BucketName: " + s3BucketName);

	}
	private static String getSystemProperty(String propertyName, String defaultValue) {
		return System.getProperty(propertyName) != null ? System.getProperty(propertyName) : 
			(System.getenv(propertyName) != null ? System.getenv(propertyName) : defaultValue); 
	}
}