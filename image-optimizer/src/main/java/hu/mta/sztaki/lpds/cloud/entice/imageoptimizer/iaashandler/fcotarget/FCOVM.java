package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.fcotarget;

import static hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VirtualMachine.VMState.VMREADY;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;

import com.extl.jade.user.Condition;
import com.extl.jade.user.Disk;
import com.extl.jade.user.ExtilityException;
import com.extl.jade.user.FilterCondition;
import com.extl.jade.user.Job;
import com.extl.jade.user.ListResult;
import com.extl.jade.user.NetworkType;
import com.extl.jade.user.Nic;
import com.extl.jade.user.QueryLimit;
import com.extl.jade.user.ResourceType;
import com.extl.jade.user.SearchFilter;
import com.extl.jade.user.Server;
import com.extl.jade.user.ServerStatus;
import com.extl.jade.user.UserAPI;
import com.extl.jade.user.UserService;
import com.extl.jade.user.VirtualizationType;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMManagementException;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VirtualMachine;

public class FCOVM extends VirtualMachine {
	private static Logger log = Shrinker.myLogger;
	private static final int totalReqLimit = Integer.parseInt(System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxUsableCPUs"));
	private static AtomicInteger reqCounter = new AtomicInteger();
	
	public static final String ACCESS_KEY = "accessKey";
	public static final String SECRET_KEY = "secretKey";
	public static final String ENDPOINT = "endpoint";
	public static final String INSTANCE_TYPE = "instanceType";
	
	public static final String UNKNOWN = "unknown";
	public static final String PENDING = "pending";
	public static final String BOOTING = "booting";
	public static final String RUNNING = "running";
	public static final String SHUTDOWN = "shutting-down";
	public static final String STOPPING = "stopping";
	public static final String STOPPED = "stopped";
	public static final String TERMINATED = "terminated";
	public static final String ERROR = "error";

	private static final String PROPERTIES_FILE_NAME = "fco.properties";

	private static final String nicResourceName = "Nic-Card-1"; 

	// set in static {}
	private static DatatypeFactory datatypeFactory;
	private static String clusterUUID;
	private static String networkUUID; 
	private static String diskProductOfferUUID;
	private static String vdcUUID;
	private static String serverProductOfferUUID;
	
	// user-defined required parameters set in parseParameters
	private String endpoint;
	private String userEmailAddressSlashCustomerUUID;
	private String password;
	
//	private final String imageUUID; == super.getImageId()
	private int diskSize;
	private int cpuSize;
	private int ramSize;
	private String instanceType; 
	private UserService service; // web service

	// VM status 
	private String serverUUID; // UUID of the created server, DONT INITIALIZE!!
	private String privateDnsName;
	private String status;
	
	// read fixed FCO parameters from properties file: fco.properties
	static {
		Properties prop = new Properties();
		InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(PROPERTIES_FILE_NAME); // emits no exception but null returned
		if (in == null) { log.severe("The system cannot find the file specified: " + PROPERTIES_FILE_NAME); } 
		else {
			try {
				prop.load(in);
				try { in.close(); } catch (IOException e) {}
				clusterUUID = prop.getProperty("clusterUUID");
				networkUUID = prop.getProperty("networkUUID");
				diskProductOfferUUID = prop.getProperty("diskProductOfferUUID");
				vdcUUID = prop.getProperty("vdcUUID");
				serverProductOfferUUID = prop.getProperty("serverProductOfferUUID");
				if (prop.getProperty("hostnameVerification") != null && prop.getProperty("hostnameVerification").startsWith("disable")) 
					disableHostnameVerification();
				Shrinker.myLogger.info("Properties file " + PROPERTIES_FILE_NAME + " loaded");
			} catch (IOException e) { log.severe("Cannot read properties file: " + PROPERTIES_FILE_NAME); }
		}
		try {
			datatypeFactory = DatatypeFactory.newInstance();
		} catch (DatatypeConfigurationException x) {
			Shrinker.myLogger.severe("Cannot create DatatypeFactory instance: " + x.getMessage());
		}
	}
		
	public FCOVM(Map<String, List<String>> contextandcustomizeVA, boolean testConformance, String imageUUID)  {
		super(imageUUID, contextandcustomizeVA, testConformance);
	}
	// NOTE: this is the only place where instance fields can be set, the constructor does not complete (nor field initialization) 
	// before calling runinstance
	@Override protected void parseVMCreatorParameters(Map<String, List<String>> parameters) {
		Shrinker.myLogger.fine("Parsing parameters for creating FCO VM"); 
		super.datacollectorDelay = 10000; // 10 seconds delay between polls
		if (parameters == null)
			throw new IllegalArgumentException("Missing parameters");
		if (!parameters.containsKey(ACCESS_KEY) || parameters.get(ACCESS_KEY) == null
				|| parameters.get(ACCESS_KEY).size() == 0 || parameters.get(ACCESS_KEY).get(0) == null)
			throw new IllegalArgumentException("Missing parameter: " + ACCESS_KEY);
		if (!parameters.containsKey(SECRET_KEY) || parameters.get(SECRET_KEY) == null
				|| parameters.get(SECRET_KEY).size() == 0 || parameters.get(SECRET_KEY).get(0) == null)
			throw new IllegalArgumentException("Missing parameter: " + SECRET_KEY);
		this.userEmailAddressSlashCustomerUUID = parameters.get(ACCESS_KEY).get(0);
		this.password = parameters.get(SECRET_KEY).get(0);
		if (parameters.containsKey(ENDPOINT) && parameters.get(ENDPOINT) != null && parameters.get(ENDPOINT).size() > 0)
			this.endpoint = parameters.get(ENDPOINT).get(0);
		if (parameters.containsKey(INSTANCE_TYPE) && parameters.get(INSTANCE_TYPE) != null
				&& parameters.get(INSTANCE_TYPE).size() > 0)
			this.instanceType = parameters.get(INSTANCE_TYPE).get(0);
		if (parameters.containsKey(LOGIN_NAME) && parameters.get(LOGIN_NAME) != null
				&& parameters.get(LOGIN_NAME).size() > 0)
			super.loginName = parameters.get(LOGIN_NAME).get(0);
		
		// do the rest of field initializations
		status = UNKNOWN;
		// defaults
		this.cpuSize = 1;
		this.ramSize = 1024;
		this.diskSize = 16; // GB
		setInstanceType(instanceType); // override cpuSize, ramSize defaults
//		this.imageUUID == super.getImageId()
		try {
		this.service = getService(endpoint, userEmailAddressSlashCustomerUUID, password);
		} catch (Exception x) {
			Shrinker.myLogger.severe("Exception: Cannot create FCO service: " + x.getMessage());
			System.out.println("Exception: " + x.getMessage());
		}
//		System.out.println("VM fields");
//		System.out.println("endpoint: " + endpoint);
//		System.out.println("userEmailAddressSlashCustomerUUID: " + userEmailAddressSlashCustomerUUID);
//		System.out.println("imageUUID: " + super.getImageId());
//		System.out.println("instanceType: " + instanceType);
//		System.out.println("cpuSize: " + cpuSize);
//		System.out.println("ramSize: " + ramSize);
//		System.out.println("diskSize: " + diskSize);
//		System.out.println("service: " + service);
//		System.out.println("loginName: " + loginName);
	}
	
	private void setInstanceType(String instanceType) {
//		System.out.println("Setting worker VM instance type: " + instanceType);
		if ("m1.small".equals(instanceType)) {
			this.cpuSize = 1;
			this.ramSize = 2048;
		} else if ("m1.medium".equals(instanceType)) {
			this.cpuSize = 2;
			this.ramSize = 4096;
		} else if ("m1.large".equals(instanceType)) {
			this.cpuSize = 2;
			this.ramSize = 8196;
		} else if ("m1.xlarge".equals(instanceType)) {
			this.cpuSize = 4;
			this.ramSize = 16384;
		} else log.severe("Unknown instance type: " + instanceType);
	}
	
	private UserService getService(String endpoint, String userEmailAddressSlashCustomerUUID, String password) throws MalformedURLException, IOException {
	    URL url = new URL(com.extl.jade.user.UserAPI.class.getResource("."), endpoint);
	    UserAPI api = new UserAPI(url, new QName("http://extility.flexiant.net", "UserAPI")); // throws IOException if endpoint is not accessible
	    UserService service = api.getUserServicePort();
	    BindingProvider portBP = (BindingProvider) service;
	    portBP.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint);
	    portBP.getRequestContext().put(BindingProvider.USERNAME_PROPERTY, userEmailAddressSlashCustomerUUID);
	    portBP.getRequestContext().put(BindingProvider.PASSWORD_PROPERTY, password);
	    return service;
	} 
	
	private Server createServerObject() {
		String serverName = "Optimizer Worker VM " + UUID.randomUUID();
		Server server = new Server();
		Disk disk = new Disk();
		disk.setClusterUUID(clusterUUID);
	    disk.setProductOfferUUID(diskProductOfferUUID);
	    disk.setIso(true);
	    disk.setResourceName(serverName);
	    disk.setResourceType(ResourceType.DISK);
	    disk.setSize(diskSize);
	    disk.setVdcUUID(vdcUUID);
	    server.setClusterUUID(clusterUUID);
	    server.setImageUUID(super.getImageId());
	    server.setProductOfferUUID(serverProductOfferUUID);
	    server.setCpu(cpuSize);
	    server.setRam(ramSize);
	    server.getDisks().add(disk);
	    server.setResourceName(serverName);
	    server.setResourceType(ResourceType.SERVER);
		server.setVdcUUID(vdcUUID);
		server.setVirtualizationType(VirtualizationType.VIRTUAL_MACHINE);
		Nic nicCard = new Nic();
		nicCard.setClusterUUID(clusterUUID);
		nicCard.setNetworkUUID(networkUUID);
		nicCard.setNetworkType(NetworkType.IP);
		nicCard.setResourceName(nicResourceName);
		nicCard.setResourceType(ResourceType.NIC);
		nicCard.setVdcUUID(vdcUUID);
		server.getNics().add(nicCard);
		return server;
	}

	// !!!NOTE: this is invoked before the constructor completes!
	@Override public String runInstance(String key) throws VMManagementException {
		Shrinker.myLogger.info("Trying to start VM... (" + getImageId() + " " + instanceType + " " + endpoint + ")");
		int requests = reqCounter.incrementAndGet();
		if (requests > totalReqLimit) {
			Shrinker.myLogger.severe("Terminating shrinking process, too many non-terminated requests");
			Thread.dumpStack();
			System.exit(1);
		}
		// create server
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Creating server in FCO... (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
		try { 
			Shrinker.myLogger.fine("Create server...");
			Job createServerJob = service.createServer(createServerObject(), null, null, null);
			Shrinker.myLogger.fine("Waiting for Create server job to complete...");
			createServerJob.setStartTime(datatypeFactory.newXMLGregorianCalendar(new GregorianCalendar()));
			Job response = service.waitForJob(createServerJob.getResourceUUID(), true);	
			Shrinker.myLogger.fine("Create server job completed");
			this.serverUUID = response.getItemUUID();
			status = PENDING;
			if (response.getErrorCode() == null) Shrinker.myLogger.info("Server created: " + serverUUID);
			else throw new Exception("Cannot create server: " + response.getErrorCode());
		} catch (Exception x) {
			log.severe("Cannot create server: " + x.getMessage());
			status = TERMINATED;
			throw new VMManagementException("Cannot create server", x);
		}
		
		// start server
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Starting server in FCO... (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
		try { 
			Shrinker.myLogger.fine("Start server...");
			Job startServerJob = service.changeServerStatus(serverUUID, ServerStatus.RUNNING, true, null, null);
			Shrinker.myLogger.fine("Waiting for Start server job to complete...");
			startServerJob.setStartTime(datatypeFactory.newXMLGregorianCalendar(new GregorianCalendar()));
			Job response = service.waitForJob(startServerJob.getResourceUUID(), true);	
			Shrinker.myLogger.fine("Start server job completed");
			if (response.getErrorCode() == null) Shrinker.myLogger.fine("Server status changed to running");
			else throw new Exception("Cannot run server: " + response.getErrorCode());
		} catch (Exception x) {
			log.severe("Cannot create server: " + x.getMessage());
			throw new VMManagementException("Cannot create server", x);
		}

		Shrinker.myLogger.info("VM started (" + getImageId() + ", " + instanceType + ", " + endpoint + "): "+ getInstanceId());
		VirtualMachine.vmsStarted.incrementAndGet();
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Server started: " + getInstanceId() + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
		
        return serverUUID;
	}
	
	@Override public String getInstanceId() { 
		return serverUUID; 
	}
	
	@Override public String getIP() throws VMManagementException { 
		describeServer();
		return privateDnsName; 
	}
	
	@Override public String getPort() throws VMManagementException {
		describeServer();
		return super.getPort();
	}

	@Override public String getPrivateIP() throws VMManagementException {
		describeServer();
		return super.getPrivateIP();
	}
	
	private long lastrefresh = 0l;
	private void describeServer() throws VMManagementException {
		long currTime = System.currentTimeMillis();
		if (currTime - lastrefresh <= super.datacollectorDelay) {
			Shrinker.myLogger.fine("Describe server ommited, last call was " + (currTime - lastrefresh) + "ms ago.");
			return;
		}
		lastrefresh = currTime;
		
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Describing server... " + serverUUID + " in FCO... (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
		Shrinker.myLogger.fine("Describe server: " + serverUUID);
		if (serverUUID == null) return;
		SearchFilter searchFilter = new SearchFilter();
		FilterCondition filterCondition = new FilterCondition();
		filterCondition.setCondition(Condition.IS_EQUAL_TO);
		filterCondition.setField("resourceuuid");
		filterCondition.getValue().add(serverUUID);
		searchFilter.getFilterConditions().add(filterCondition);
		QueryLimit queryLimit = new QueryLimit();
		queryLimit.setMaxRecords(10000);
		queryLimit.setLoadChildren(true);
		try {
			ListResult resultList = service.listResources(searchFilter, queryLimit, ResourceType.SERVER);
			if (resultList == null) throw new Exception("Server UUID not found: " + serverUUID);
			List <Object> results = resultList.getList();
			if (results.size() == 0)  throw new Exception("Server UUID not found: " + serverUUID + " (empty list)");
			if (results.size() > 1)  log.severe("Server UUID found too many times: " + serverUUID + " (" + results.size() + ")");
			if (!(results.get(results.size() - 1) instanceof Server)) throw new Exception("Invalid object (Serve expected): " + results.get(results.size() - 1).getClass().getName());
			Server resultServer = (Server) results.get(results.size() - 1);
			Shrinker.myLogger.fine("Server UUID found");
			ArrayList<Nic> nics;
			nics = (ArrayList<Nic>) resultServer.getNics();
			if (nics.size() == 0) throw new Exception("No NIC found for server UUID: " + serverUUID + "");
			if (nics.size() > 1) Shrinker.myLogger.fine("Considering first NIC");
			Nic nic0 = nics.get(0);
			if (nic0.getIpAddresses() == null || nic0.getIpAddresses().size() == 0) throw new Exception("No IP address for NIC 0");
			privateDnsName = nic0.getIpAddresses().get(0).getIpAddress();
			status = mapVMStatus(resultServer.getStatus());
			Shrinker.myLogger.info("Server name: " + resultServer.getResourceName());
			Shrinker.myLogger.info("IP: " + privateDnsName);
			Shrinker.myLogger.info("Status: " + status + " (" + resultServer.getStatus() + ")");
		} catch (Exception x) {
			throw new VMManagementException("Cannot describe server", x);
		}
		// ??
		boolean isinInitialState = initializingStates.contains(getState());
		if (isinInitialState) {
			super.setIP(null);
			super.setPort(null);
			super.setPrivateIP(null);
		}
		if (RUNNING.equals(status)) {
			super.setIP(privateDnsName);
			super.setPrivateIP(privateDnsName);
			super.setPort("22");
			super.setState(VMREADY);
		}
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Describe done. Status: " + status + ", IP: " + privateDnsName + ". (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
	}

	private String mapVMStatus(ServerStatus serverStatus) {
		if (serverStatus == null) return UNKNOWN;
		switch (serverStatus) {
			case STARTING:
			case MIGRATING:
			case REBOOTING:
			case RECOVERY:
			case BUILDING:
			case INSTALLING:
				return PENDING;
			case RUNNING:
				return RUNNING;
			case DELETING:
				return STOPPING;
			case ERROR:
				return ERROR;
			case STOPPED:
				return TERMINATED;
			case STOPPING:
				return STOPPING;
			default:
				return UNKNOWN;
		}
	}
	
	@Override public void terminateInstance() throws VMManagementException {
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Deleting server... " + serverUUID + " in FCO... (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
		Shrinker.myLogger.fine("Delete server: " + serverUUID);
		if (serverUUID == null) throw new VMManagementException("Server UUID is null", null);
		try {
			int requests = reqCounter.decrementAndGet();
			if (requests < 0) {
				Shrinker.myLogger.severe("Terminating shrinking process, too much VM termination requests");
				Thread.dumpStack();
			}
			Job deleteJob = service.deleteResource(serverUUID, true, null);
			if (deleteJob == null) throw new VMManagementException("Server UUID not found: " + serverUUID + " (null job)", null);
			deleteJob.setStartTime(datatypeFactory.newXMLGregorianCalendar(new GregorianCalendar()));
			Shrinker.myLogger.fine("Waiting for Delete server job to complete...");
			Job response = service.waitForJob(deleteJob.getResourceUUID(), true);
			Shrinker.myLogger.fine("Delete server job completed");
			if (response.getErrorCode() == null) {
				Shrinker.myLogger.info("Server deleted: " + serverUUID);
			} else throw new VMManagementException("Cannot terminate server: " + response.getErrorCode(), null);
		} catch (ExtilityException x) { throw new VMManagementException("Cannot terminate server", x); }
		discard();
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Server deleted: " + getInstanceId() + " " + this.privateDnsName + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
	}

	@Override public void rebootInstance() throws VMManagementException {
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Rebooting server... " + serverUUID + " in FCO... (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
		try {
			Shrinker.myLogger.info("Reboot server: " + serverUUID);
			Job rebootServerJob = service.changeServerStatus(serverUUID, ServerStatus.REBOOTING, true, null, null);
			Shrinker.myLogger.fine("Waiting for Reboot server job to complete...");
			rebootServerJob.setStartTime(datatypeFactory.newXMLGregorianCalendar(new GregorianCalendar()));
			Job response = service.waitForJob(rebootServerJob.getResourceUUID(), true);	
			Shrinker.myLogger.fine("Reboot server job completed");
			if (response.getErrorCode() == null) {}
			else throw new Exception("Cannot reboot server: " + response.getErrorCode());
		} catch (Exception x) {
			throw new VMManagementException("Cannot reboot instance", x);
		}
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Server rebooted: " + getInstanceId() + " " + this.privateDnsName + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
	}
	
	private void discard() {
//		no resources to discard
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
			log.severe("Hostname verification disabled");
		} catch (Throwable x) {
			log.severe("Error at turning off hostname verification " + x.getMessage());
		}
	}
}
