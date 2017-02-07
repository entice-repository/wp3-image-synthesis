package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.fco;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.extl.jade.user.Disk;
import com.extl.jade.user.ExtilityException;
import com.extl.jade.user.Job;
import com.extl.jade.user.NetworkType;
import com.extl.jade.user.Nic;
import com.extl.jade.user.ResourceType;
import com.extl.jade.user.Server;
import com.extl.jade.user.UserAPI;
import com.extl.jade.user.UserService;
import com.extl.jade.user.VirtualizationType;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.VM;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.rest.Configuration;

public class FCOVM extends VM {
	private static final Logger log = LoggerFactory.getLogger(FCOVM.class);
	public static final String CLOUD_INTERFACE = "fco";
	private static ExecutorService executor = Executors.newFixedThreadPool(10);

	static {
		try {
			System.setProperty("jsse.enableSNIExtension", "false");
			// Create a trust manager that does not validate certificate chains
			TrustManager[] trustAllCerts = new TrustManager[] { 
			  new X509TrustManager() {
			    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
			    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
			    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
			}};
			// Ignore differences between given hostname and certificate hostname
			HostnameVerifier hv = new HostnameVerifier() {
			  public boolean verify(String hostname, SSLSession session) { return true; }
			};
			// Install the all-trusting trust manager
			try {
			  SSLContext sc = SSLContext.getInstance("SSL");
			  sc.init(null, trustAllCerts, new SecureRandom());
			  HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			  HttpsURLConnection.setDefaultHostnameVerifier(hv);
			} catch (Exception e) {}
		} catch (Throwable x) {
			log.error("Error at turning off hostname verification " + x.getMessage());
		}
	}
	
	// configuration-defined fixed-parameters (optimization task-invariant)
	private final String serverName = "Optimizer VM " + UUID.randomUUID(); 
	private static final int cpuSize = 2;
	private static final int ramSize = 2048;
	private static final String nicResourceName = "Nic-Card-1"; 
	
	// user-defined required parameters (must present in request json)
	private final String endpoint;
	private final String userEmailAddress;
	private final String password;
	private final String customerUUID;
	private final String clusterUUID;
	private final String networkUUID; 
	private final String diskProductOfferUUID;
	private final String vdcUUID;
	private final String serverProductOfferUUID;
	
	// user-defined optional parameters (defaults set in properties files)
	private final String imageUUID;
	private final int diskSize;

	// VM status 
	UserService service;
	private final Server server;
	private String serverUUID;
	private String instanceId;
	private String privateDnsName;
	private String status = UNKNOWN;
	
	public static class Builder {
		// required parameters
		private final String userEmailAddress;
		private final String password;
		private final String customerUUID;
		private final String clusterUUID;
		private final String networkUUID; 
		private final String diskProductOfferUUID;
		private final String vdcUUID;
		private final String serverProductOfferUUID;
		// optional parameters
		private String endpoint = Configuration.localEc2Endpoint;
		private String imageUUID = Configuration.optimizerImageId;
		private int diskSize = 100; // MB?
		
		public Builder(String userEmailAddress, String password, String customerUUID, String clusterUUID, String networkUUID, String diskProductOfferUUID, String vdcUUID, String serverProductOfferUUID) {
			this.userEmailAddress = userEmailAddress;
			this.password = password;
			this.customerUUID = customerUUID;
			this.clusterUUID = clusterUUID;
			this.networkUUID = networkUUID; 
			this.diskProductOfferUUID = diskProductOfferUUID;
			this.vdcUUID = vdcUUID;
			this.serverProductOfferUUID = serverProductOfferUUID;
		}
		
		public Builder withEndpoint(String endpoint) {
			this.endpoint = endpoint;
			return this;
		}

		public Builder withImageUUID(String imageUUID) {
			this.imageUUID = imageUUID;
			return this;
		}

		public Builder withDiskSize(int diskSize) {
			this.diskSize = diskSize;
			return this;
		}
		public FCOVM build() {
			return new FCOVM(this);
		}
	}
	
	private FCOVM(Builder builder) {
		userEmailAddress = builder.userEmailAddress;
		password = builder.password;
		customerUUID = builder.customerUUID;
		clusterUUID = builder.clusterUUID;
		networkUUID = builder.networkUUID; 
		diskProductOfferUUID = builder.diskProductOfferUUID;
		vdcUUID = builder.vdcUUID;
		serverProductOfferUUID = builder.serverProductOfferUUID;
		
		endpoint = builder.endpoint;
		imageUUID = builder.imageUUID;
		diskSize = builder.diskSize;
		
	    // create a server resource using Standard server product offer and set basic settings
		server = new Server();
		Disk disk = new Disk();
		disk.setClusterUUID(clusterUUID);
	    disk.setProductOfferUUID(diskProductOfferUUID);
	    disk.setIso(true);
	    disk.setResourceName(serverName);
	    disk.setResourceType(ResourceType.DISK);
	    disk.setSize(diskSize);
	    disk.setVdcUUID(vdcUUID);
	    server.setClusterUUID(clusterUUID);
	    server.setImageUUID(imageUUID);
	    server.setProductOfferUUID(serverProductOfferUUID);
	    server.setCpu(cpuSize);
	    server.setRam(ramSize);
	    server.getDisks().add(disk);
	    server.setResourceName(serverName);
	    server.setResourceType(ResourceType.SERVER);
		server.setVdcUUID(vdcUUID);
		server.setVirtualizationType(VirtualizationType.VIRTUAL_MACHINE);
		// add NIC
		Nic nicCard = new Nic();
		nicCard.setClusterUUID(clusterUUID);
		nicCard.setNetworkUUID(networkUUID);
		nicCard.setNetworkType(NetworkType.IP);
		nicCard.setResourceName(nicResourceName);
		nicCard.setResourceType(ResourceType.NIC);
		nicCard.setVdcUUID(vdcUUID);
		server.getNics().add(nicCard);
	}
	
	@Override public void run(Map<String, String> parameters) throws Exception {
		log.info("Launching optimizer VM in FCO...");
		if (service == null) service = getService(endpoint, userEmailAddress, customerUUID, password);
		
//        DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();
		try {
			Job job = service.createServer(server, null, null, null);
			serverUUID = job.getResourceUUID();
//			job.setStartTime(datatypeFactory.newXMLGregorianCalendar(new GregorianCalendar()));
            Runnable worker = new VMCreatedPollerThread(this, serverUUID);
            executor.execute(worker);
//			Job response = service.waitForJob(job.getResourceUUID(), true);
//			serverUUID = response.getItemUUID();
//			if (response.getErrorCode() == null) {
//				log.info("Server created successfully");
//			} else {
//				log.error(response.getErrorCode());
//				throw new Exception("ERROR: failed to create server: " + response.getErrorCode());
//			}
	    } 
		catch (ExtilityException e) { log.error("ExtilityException", e); throw e; }
		finally {}
	}
	
	public class VMCreatedPollerThread implements Runnable {
		private final static int timeout = 5 * 60; // seconds
		private final static int interval = 10; // seconds
		private final FCOVM vm;
		private final String jobUUID;
		VMCreatedPollerThread(FCOVM vm, String jobUUID) {
			this.vm = vm;
			this.jobUUID = jobUUID;
		}
		
		// poll for at most 5 minutes every 10 seconds  
		@Override public void run() {
			int repeats = timeout / interval;
			Exception x = null;
			String errorCode = "";
			while (repeats > 0) {
				log.debug("Polling createServer status: " + jobUUID);
				try { 
					Job response = service.waitForJob(jobUUID, true);
					if (response.getErrorCode() == null) break;
					else {
						errorCode = response.getErrorCode();
						log.debug("Poll failed due to error code: " + errorCode);
					}
				} catch (ExtilityException e) { 
					x = e;
					log.debug("Poll failed due to exception: " + e.getMessage());
				}
				try { Thread.sleep(interval * 1000l); } catch (InterruptedException e) {}
				repeats--;
			}
			if (repeats == 0) {
				log.error("Cannot create server: " + errorCode + " " + (x != null ? x.getMessage() : ""));
				vm.status = VM.TERMINATED;
			}
		}
	}
	
	private UserService getService(String endpoint, String userEmailAddress, String customerUUID, String password) throws MalformedURLException {
	    URL url = new URL(com.extl.jade.user.UserAPI.class.getResource("."), endpoint);
	    UserAPI api = new UserAPI(url, new QName("http://extility.flexiant.net", "UserAPI"));
	    UserService service = api.getUserServicePort();
	    // Get the binding provider
	    BindingProvider portBP = (BindingProvider) service;
	    // and set the service endpoint
	    portBP.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint);
	    // and the caller's authentication details and password
	    portBP.getRequestContext().put(BindingProvider.USERNAME_PROPERTY, userEmailAddress + "/" + customerUUID);
	    portBP.getRequestContext().put(BindingProvider.PASSWORD_PROPERTY, password);
	    return service;
	} 
	
	// getters and setters
	@Override public String getInstanceId() { return instanceId; }
	@Override public String getStatus() { return status; }
	@Override public String getIP() { return privateDnsName; }

	@Override public void describeInstance() throws Exception {
		// TODO Auto-generated method stub

	}

	@Override public void terminate() throws Exception {
		// TODO Auto-generated method stub
		DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();
		Job deleteJob = service.deleteResource(serverUUID, true, datatypeFactory.newXMLGregorianCalendar(new GregorianCalendar()));
		Job response = service.waitForJob(deleteJob.getResourceUUID(), true);
		if (response.getErrorCode() == null) {
			log.info("Server deleted successfully");
		} else {
			log.error(response.getErrorCode());
			throw new Exception("ERROR: failed to delete server: " + response.getErrorCode());
		}		
	}

	@Override public void discard() {
		// no resources to discard
	}
	
	public static void main(String [] args) throws Exception {
		String endpoint = "https://cp.sd1.flexiant.net/soap/user/current/?wsdl";
		String optimizerImageId = "4599ad51-33cd-386e-b074-76e18a2ebc18";

		String userEmailAddress = "username";
		String password = "password";
		String customerUUID = "uuid"; 
		String clusterUUID = "uuid";
		String networkUUID = "uuid"; 
		String diskProductOfferUUID = "uuid"; 
		String vdcUUID = "uuid";
		String serverProductOfferUUID = "uuid";
		
		VM vm = new FCOVM.Builder(userEmailAddress, password, customerUUID, clusterUUID, networkUUID, diskProductOfferUUID, vdcUUID, serverProductOfferUUID)
				.withEndpoint(endpoint)
				.withImageUUID(optimizerImageId)
//				.withDiskSize(5000)
				.build();  
		vm.run(null);
	}
}
