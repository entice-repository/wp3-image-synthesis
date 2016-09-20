package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.rest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration {
	
	private static final Logger log = LoggerFactory.getLogger(Configuration.class);
	public final static String PROPERTIES_FILE_NAME = "image-optimizer-service.properties";
	
	public final static String SERVICE_SSH_KEY_PUBLIC_PART = "image-optimizer-service_pub.rsa";
	public final static String SERVICE_SSH_KEY_PRIVATE_PART = "image-optimizer-service_priv.rsa";
	
	public static String version = "0.1";
	public static String localEc2Endpoint;
	public static String optimizerImageId; 
	public static String optimizerInstanceType;
	public static String workerInstanceType;
	
	public static String rankerToUse;
	public static String grouperToUse;
	public static String maxUsableCPUs;
	public static String parallelVMNum;
	public static String vmFactory;
	public static String scriptPrefix;
	
	public static String optimizerRootLogin = "root"; // login name for optimizer VM instances
	public static String workerVMRootLogin = "root"; // default login name for worker VM instances (image under optimization) 
	
	static {
		Properties prop = new Properties();
		InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(PROPERTIES_FILE_NAME); // emits no exception but null returned
		if (in == null) { log.warn("The system cannot find the file specified. Using defaults."); } 
		else {
			try {
				prop.load(in);
				try { in.close(); } catch (IOException e) {}

				version = prop.getProperty("version");

				localEc2Endpoint = prop.getProperty("localEc2Endpoint");
				optimizerImageId = prop.getProperty("optimizerImageId");
				optimizerInstanceType = prop.getProperty("optimizerInstanceType");
				workerInstanceType = prop.getProperty("workerInstanceType");
				
				// misc optimizer options
				rankerToUse = prop.getProperty("rankerToUse");
				grouperToUse = prop.getProperty("grouperToUse");
				maxUsableCPUs = prop.getProperty("maxUsableCPUs");
				parallelVMNum = prop.getProperty("parallelVMNum");
				vmFactory = prop.getProperty("vmFactory");
				scriptPrefix = prop.getProperty("scriptPrefix");
				
				optimizerRootLogin = prop.getProperty("optimizerRootLogin");
				
				log.info(PROPERTIES_FILE_NAME + " loaded");
			} catch (IOException e) { log.error("Cannot read properties file: " + PROPERTIES_FILE_NAME, e); }
		} 
	}
}