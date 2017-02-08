package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.rest;

import java.io.IOException;
import java.io.InputStream;
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
	public final static String PROPERTIES_FILE_NAME = "image-optimizer-service.properties";
	
	public final static String SERVICE_SSH_KEY_PUBLIC_PART = "image-optimizer-service_pub.rsa";
	public final static String SERVICE_SSH_KEY_PRIVATE_PART = "image-optimizer-service_priv.rsa";
	
	public static String version = "0.1";
	public static String localEc2Endpoint;
	public static String optimizerImageId; 
	public static String optimizerInstanceType;
	public static String workerInstanceType;
	
	public static String cloudInterface;
	public static String knowledgeBaseURL;
	
	public static String rankerToUse;
	public static String grouperToUse;
	public static String maxUsableCPUs;
	public static String parallelVMNum;
	public static String vmFactory;
	public static String scriptPrefix;
	
	public static String optimizerRootLogin = "root"; // login name for optimizer VM instances
	public static String workerVMRootLogin = "root"; // default login name for worker VM instances (image under optimization) 
	
	public static String clusterUUID;
	public static String networkUUID; 
	public static String diskProductOfferUUID;
	public static String vdcUUID;
	public static String serverProductOfferUUID;

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
	
	static {
		Properties prop = new Properties();
		InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(PROPERTIES_FILE_NAME); // emits no exception but null returned
		if (in == null) { log.warn("The system cannot find the file specified. Using defaults."); } 
		else {
			try {
				prop.load(in);
				try { in.close(); } catch (IOException e) {}

				version = prop.getProperty("version") != null ? prop.getProperty("version") : "0.1";
				if (prop.getProperty("localEc2Endpoint") == null) log.warn("No default EC2 endpoint defined");
				localEc2Endpoint = prop.getProperty("localEc2Endpoint") != null ? prop.getProperty("localEc2Endpoint") : "http://cfe2.lpds.sztaki.hu:4567";
				if (prop.getProperty("localEc2Endpoint") == null) log.warn("No optimizer image id defined");
				optimizerImageId = prop.getProperty("optimizerImageId") != null ? prop.getProperty("optimizerImageId") : "ami-00001553";
				optimizerInstanceType = prop.getProperty("optimizerInstanceType") != null ? prop.getProperty("optimizerInstanceType") : "m1.medium";
				workerInstanceType = prop.getProperty("workerInstanceType") != null ? prop.getProperty("workerInstanceType") : "m1.small";
				
				cloudInterface = prop.getProperty("cloudInterface") != null ? prop.getProperty("cloudInterface") : "ec2";

				if (prop.getProperty("hostnameVerification") != null && prop.getProperty("hostnameVerification").startsWith("disable")) disableHostnameVerification();
				
				knowledgeBaseURL = prop.getProperty("knowledgeBaseURL");
				
				// misc optimizer options
				rankerToUse = prop.getProperty("rankerToUse") != null ? prop.getProperty("rankerToUse") : "hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ranking.GroupFactorBasedRanker";
				grouperToUse = prop.getProperty("grouperToUse") != null ? prop.getProperty("grouperToUse") : "hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.DirectoryGroupManager";
				maxUsableCPUs = prop.getProperty("maxUsableCPUs") != null ? prop.getProperty("maxUsableCPUs") : "8";
				parallelVMNum = prop.getProperty("parallelVMNum") != null ? prop.getProperty("parallelVMNum") : "8";
				vmFactory = prop.getProperty("vmFactory") != null ? prop.getProperty("vmFactory") : "hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.amazontarget.EC2";
				scriptPrefix = prop.getProperty("scriptPrefix") != null ? prop.getProperty("scriptPrefix") : "/root/";
				
				optimizerRootLogin = prop.getProperty("optimizerRootLogin") != null ? prop.getProperty("optimizerRootLogin") : "root";
				
				// fco
				clusterUUID = prop.getProperty("clusterUUID");
				networkUUID = prop.getProperty("networkUUID"); 
				diskProductOfferUUID = prop.getProperty("diskProductOfferUUID");
				vdcUUID = prop.getProperty("vdcUUID");
				serverProductOfferUUID = prop.getProperty("serverProductOfferUUID");
				
				log.info(PROPERTIES_FILE_NAME + " loaded");
			} catch (IOException e) { log.error("Cannot read properties file: " + PROPERTIES_FILE_NAME, e); }
		} 
	}
}