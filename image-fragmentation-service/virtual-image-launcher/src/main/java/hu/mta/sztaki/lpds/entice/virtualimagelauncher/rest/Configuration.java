package hu.mta.sztaki.lpds.entice.virtualimagelauncher.rest;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Properties;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration {
	
	private static final Logger log = LoggerFactory.getLogger(Configuration.class);
	public final static String PROPERTIES_FILE_NAME = "virtual-image-launcher.properties";
	
	public static String version = "0.1";
	public static String virtualImageManagerRestURL;
	public static String virtualImageComposerRestURL;

	// fco
	public static String clusterUUID;
	public static String networkUUID; 
	public static String diskProductOfferUUID;
	public static String vdcUUID;
	public static String serverProductOfferUUID;
	public static String rootLogin = "root";
	public static String sshKeyPath;
	public static String sshPubPath;
	public static boolean hostnameVerification = true;
	
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
				virtualImageManagerRestURL = prop.getProperty("virtualImageManagerRestURL");
				virtualImageComposerRestURL = prop.getProperty("virtualImageComposerRestURL");
				
				// fco
				clusterUUID = prop.getProperty("clusterUUID");
				networkUUID = prop.getProperty("networkUUID"); 
				diskProductOfferUUID = prop.getProperty("diskProductOfferUUID");
				vdcUUID = prop.getProperty("vdcUUID");
				serverProductOfferUUID = prop.getProperty("serverProductOfferUUID");
				sshKeyPath = prop.getProperty("sshKeyPath");
				sshPubPath = prop.getProperty("sshPubPath");
				if (prop.getProperty("hostnameVerification") != null && prop.getProperty("hostnameVerification").startsWith("disable")) {
					hostnameVerification = false;
					disableHostnameVerification();
				}

			} catch (IOException e) { log.error("Cannot read properties file: " + PROPERTIES_FILE_NAME, e); }
		}
		
		virtualImageManagerRestURL = getSystemProperty("VIRTUAL_IMAGE_MANAGER_REST_URL", virtualImageManagerRestURL);
		virtualImageComposerRestURL = getSystemProperty("PUBLIC_VIRTUAL_IMAGE_COMPOSER_REST_URL", virtualImageComposerRestURL);

		// use default virtualImageManagerRestURL if unspecified
		if (virtualImageManagerRestURL == null) {
			virtualImageManagerRestURL = "http://localhost:8080/virtual-image-manager/rest";
			log.warn("Using default virtualImageManagerRestURL");
		}
		
		// use default virtualImageComposerRestURL if unspecified
		if (virtualImageComposerRestURL == null) {
			try { // we assume that virtual-image-composer is on the same host
				virtualImageComposerRestURL = "http://" + InetAddress.getLocalHost().getHostAddress() + ":8080/virtual-image-composer/rest";
				log.warn("Using default virtualImageComposerRestURL");
			} catch (Throwable e) {
				log.error("Cannot determine virtualImageComposerRestURL", e);
			}
		}

		// if not set, try to locate SSH key
		if (sshKeyPath == null) {
			try {
				log.warn("Using default sshKeyPath");
				sshKeyPath = Thread.currentThread().getContextClassLoader().getResource("id.rsa").toString();
				if (sshKeyPath != null) { 
					if (sshKeyPath.startsWith("file:\\")) sshKeyPath = sshKeyPath.substring("file:\\".length());
					else if (sshKeyPath.startsWith("file:/")) sshKeyPath = sshKeyPath.substring("file:".length());
				} else log.error("Private SSH key not found for launched VMs: " + "id.rsa" + "");
			} catch (Throwable x) {}
		}

		// if not set, try to locate SSH key public part
		if (sshPubPath == null) {
			try {
				log.warn("Using default sshPubPath");
				sshPubPath = Thread.currentThread().getContextClassLoader().getResource("pub.rsa").toString();
				if (sshPubPath != null) { 
					if (sshPubPath.startsWith("file:\\")) sshPubPath = sshPubPath.substring("file:\\".length());
					else if (sshPubPath.startsWith("file:/")) sshPubPath = sshPubPath.substring("file:".length());
				} else log.error("Public SSH key not found for launched VMs: " + "pub.rsa" + "");
			} catch (Throwable x) {}
		}
		
		log.info("virtualImageManagerRestURL: " + virtualImageManagerRestURL);
		log.info("virtualImageComposerRestURL: " + virtualImageComposerRestURL);
	}
	private static String getSystemProperty(String propertyName, String defaultValue) {
		return System.getProperty(propertyName) != null ? System.getProperty(propertyName) : 
			(System.getenv(propertyName) != null ? System.getenv(propertyName) : defaultValue); 
	}
	private static void disableHostnameVerification() {
		try {
			System.setProperty("jsse.enableSNIExtension", "false");
			// create a trust manager that does not validate certificate chains
			TrustManager[] trustAllCerts = new TrustManager[] { 
			  new X509TrustManager() {
			    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
			    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
			    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
			}};
			// ignore differences between given hostname and certificate hostname
			HostnameVerifier hv = new HostnameVerifier() {
			  public boolean verify(String hostname, SSLSession session) { return true; }
			};
			// install the all-trusting trust manager
			try {
			  SSLContext sc = SSLContext.getInstance("SSL");
			  sc.init(null, trustAllCerts, new SecureRandom());
			  HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			  HttpsURLConnection.setDefaultHostnameVerifier(hv);
			} catch (Exception e) {}
			log.warn("Hostname verification disabled");
		} catch (Throwable x) {
			log.error("Error at turning off hostname verification " + x.getMessage());
		}
	}
}