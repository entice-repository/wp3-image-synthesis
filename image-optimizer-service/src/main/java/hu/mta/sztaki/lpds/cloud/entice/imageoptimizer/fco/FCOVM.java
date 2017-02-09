package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.fco;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.extl.jade.user.Condition;
import com.extl.jade.user.Disk;
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

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.VM;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.rest.Configuration;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.utils.OutputStreamWrapper;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.utils.SshSession;

public class FCOVM extends VM {
	private static final Logger log = LoggerFactory.getLogger(FCOVM.class);
	public static final String CLOUD_INTERFACE = "fco";
	
	private ExecutorService threadExecutor = Executors.newFixedThreadPool(2); // runs startServer and describeServer threads
	private AtomicBoolean describeInProgress = new AtomicBoolean(false); // allow new describe if no describe in progress 
	
	private String userDataBase64; // cloud-init
	private String login; // for cloud-init
	private String sshKeyPath; // for cloud-init
	
	// configuration-defined fixed-parameters (optimization task-invariant)
	private final String serverName = "Optimizer VM " + UUID.randomUUID(); 
	private static final String nicResourceName = "Nic-Card-1"; 
	
	// user-defined required parameters (must be present in request json)
	private final String endpoint;
	private final String userEmailAddressSlashCustomerUUID;
	private final String password;
	
	// user-defined optional parameters (defaults set in properties files)
	private final String imageUUID;
	private final int diskSize;
	private final String clusterUUID;
	private final String networkUUID; 
	private final String diskProductOfferUUID;
	private final String vdcUUID;
	private final String serverProductOfferUUID;
	private final int cpuSize;
	private final int ramSize;

	// VM status 
	private final DatatypeFactory datatypeFactory;
	private final UserService service; // web service
	private String serverUUID = null; // UUID of the created server
	private String privateDnsName;
	private String status = UNKNOWN;
	
	public static class Builder {
		// required parameters
		private final String endpoint;
		private final String userEmailAddressSlashCustomerUUID;
		private final String password;
		private final String imageUUID;
		// optional parameters
		private String clusterUUID = Configuration.clusterUUID;
		private String networkUUID = Configuration.networkUUID; 
		private String diskProductOfferUUID = Configuration.diskProductOfferUUID;
		private String vdcUUID = Configuration.vdcUUID;
		private String serverProductOfferUUID = Configuration.serverProductOfferUUID;
		private int cpuSize = 1;
		private int ramSize = 1024;
		private int diskSize = 16; // GB
		
		public Builder(String endpoint, String userEmailAddressSlashCustomerUUID, String password, String imageUUID)  {
			this.endpoint = endpoint;
			this.userEmailAddressSlashCustomerUUID = userEmailAddressSlashCustomerUUID;
			this.password = password;
			this.imageUUID = imageUUID;
		}
		public Builder withDiskSize(int diskSize) {
			this.diskSize = diskSize;
			return this;
		}
		public Builder withNetworkUUID(String networkUUID) {
			this.networkUUID = networkUUID; 
			return this;
		}
		public Builder withClusterUUID(String clusterUUID) {
			this.clusterUUID = clusterUUID;
			return this;
		}
		public Builder withDiskProductOfferUUID(String diskProductOfferUUID) {
			this.diskProductOfferUUID = diskProductOfferUUID;
			return this;
		}
		public Builder withVdcUUID(String vdcUUID) {
			this.vdcUUID = vdcUUID;
			return this;
		}
		public Builder withServerProductOfferUUID(String serverProductOfferUUID) {
			this.serverProductOfferUUID = serverProductOfferUUID;
			return this;
		}
		public Builder withCpuSize(int cpuSize) {
			this.cpuSize = cpuSize;
			return this;
		}
		public Builder withRamSize(int ramSize) {
			this.ramSize = ramSize;
			return this;
		}
		public Builder withInstanceType(String instanceType) {
			if ("m1.small".equals(instanceType)) {
				cpuSize = 1;
				ramSize = 2048;
			} else if ("m1.medium".equals(instanceType)) {
				cpuSize = 1;
				ramSize = 4096;
			} else if ("m1.large".equals(instanceType)) {
				cpuSize = 2;
				ramSize = 8196;
			} else if ("m1.xlarge".equals(instanceType)) {
				cpuSize = 4;
				ramSize = 16384;
			} else log.warn("Unknown instance type: " + instanceType);
			return this;
		}
		
		public FCOVM build() throws MalformedURLException, DatatypeConfigurationException, IOException {
			return new FCOVM(this);
		}
	}
	
	private FCOVM(Builder builder) throws MalformedURLException, DatatypeConfigurationException, IOException {
		userEmailAddressSlashCustomerUUID = builder.userEmailAddressSlashCustomerUUID;
		password = builder.password;

		clusterUUID = builder.clusterUUID;
		networkUUID = builder.networkUUID; 
		diskProductOfferUUID = builder.diskProductOfferUUID;
		vdcUUID = builder.vdcUUID;
		serverProductOfferUUID = builder.serverProductOfferUUID;
		
		endpoint = builder.endpoint;
		imageUUID = builder.imageUUID;
		diskSize = builder.diskSize;

		cpuSize = builder.cpuSize;
		ramSize = builder.ramSize;

		service = getService(endpoint, userEmailAddressSlashCustomerUUID, password);
		datatypeFactory = DatatypeFactory.newInstance();
	}
	
	private Server createServerObject() {
	    // create a server resource using Standard server product offer and set basic settings
		log.info("Server name: " + serverName);
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
		return server;
	}
	
	@Override public void run(Map<String, String> parameters) throws Exception {
		log.info("Launching optimizer VM in FCO...");
		
		// parameters for emulating cloud-init
		if (parameters != null) {
			userDataBase64 = parameters.get(USER_DATA_BASE64);
			login = parameters.get(LOGIN);
			sshKeyPath = parameters.get(SSH_KEY_PATH);
		}
		
		// create server
		try { createServer(); } 
		catch (Exception x) {
			log.error("Cannot create server: " + x.getMessage());
			status = VM.TERMINATED;
			throw x;
		}
		// change status in async thread (may take longer time)
        if (!threadExecutor.isShutdown()) threadExecutor.execute(new VMStarterThread(this));
        else log.debug("Executor is down");
	}
	
	private void createServer() throws Exception {
		log.debug("Create server...");
		Job createServerJob = service.createServer(createServerObject(), null, null, null);
		log.debug("Waiting for Create server job to complete...");
		createServerJob.setStartTime(datatypeFactory.newXMLGregorianCalendar(new GregorianCalendar()));
		Job response = service.waitForJob(createServerJob.getResourceUUID(), true);	
		log.debug("Create server job completed");
		serverUUID = response.getItemUUID();
		if (response.getErrorCode() == null) log.info("Server created: " + serverUUID);
		else throw new Exception("Cannot create server: " + response.getErrorCode());
	}	
	
	public class VMStarterThread implements Runnable {
		private final FCOVM vm;
		VMStarterThread(FCOVM vm) throws DatatypeConfigurationException { this.vm = vm;	}
		@Override public void run() {
			log.debug("Server starter thread started");
			// run
			try { runServer(); } 
			catch (Exception x) {
				log.error("Cannot run server: " + x.getMessage());
				try { vm.terminate(); } catch (Exception e) { log.error("Cannot terminate VM", e); }
				vm.status = VM.TERMINATED;
			}
			// describe to get IP
			describeServerIfNotInProgress();
			// run cloud-init
			emulateCloudInitWithRetries();
			log.debug("Server starter thread ended");
		}
		private void runServer() throws Exception {
			log.debug("Start server...");
			Job startServerJob = service.changeServerStatus(serverUUID, ServerStatus.RUNNING, true, null, null);
			log.debug("Waiting for Start server job to complete...");
			startServerJob.setStartTime(datatypeFactory.newXMLGregorianCalendar(new GregorianCalendar()));
			Job response = service.waitForJob(startServerJob.getResourceUUID(), true);	
			log.debug("Start server job completed");
			if (response.getErrorCode() == null) log.debug("Server status changed to running");
			else throw new Exception("Cannot run server: " + response.getErrorCode());
		}
	}

	private UserService getService(String endpoint, String userEmailAddressSlashCustomerUUID, String password) throws MalformedURLException, IOException {
	    URL url = new URL(com.extl.jade.user.UserAPI.class.getResource("."), endpoint);
	    UserAPI api = new UserAPI(url, new QName("http://extility.flexiant.net", "UserAPI")); // throws IOException if endpoint is not accessible
	    UserService service = api.getUserServicePort();
	    // get the binding provider
	    BindingProvider portBP = (BindingProvider) service;
	    // set the service endpoint
	    portBP.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint);
	    // caller's authentication details and password
	    portBP.getRequestContext().put(BindingProvider.USERNAME_PROPERTY, userEmailAddressSlashCustomerUUID);
	    portBP.getRequestContext().put(BindingProvider.PASSWORD_PROPERTY, password);
	    return service;
	} 
	
	// getters and setters
	@Override public String getInstanceId() { return serverUUID; }
	@Override public String getStatus() { return status; }
	@Override public String getIP() { return privateDnsName; }

	@Override public void describeInstance() throws Exception {
		describeServerIfNotInProgress();
	}	

	private void describeServerIfNotInProgress() {
		if (threadExecutor.isShutdown()) {
			log.debug("Executor is down");
			return;
		}
		synchronized (describeInProgress) {
			if (describeInProgress.get()) {
				log.debug("Describe is in progress");
				return;
			} else {
				try { 
					if (!threadExecutor.isShutdown()) {
						threadExecutor.execute(new VMDescribeThread()); 
						describeInProgress.set(true);
					} else log.debug("Executor is down. Cannot describe.");
				} catch (DatatypeConfigurationException e) {} // should not happen now
			} 
		}
	}

	public class VMDescribeThread implements Runnable {
		VMDescribeThread() throws DatatypeConfigurationException {}
		@Override public void run() {
			log.debug("Server describe thread started");
			try { describeServer(); } 
			catch (Exception x) { log.warn("Cannot describe server: " + x.getMessage()); }
			log.debug("Server describe thread ended");
			synchronized (describeInProgress) {
				describeInProgress.set(false);
			}
		}
	}

	private void describeServer() throws Exception {
		log.debug("Describe server: " + serverUUID);
		if (serverUUID == null) return;
		// create an FQL filter and a filter condition
		SearchFilter searchFilter = new SearchFilter();
		FilterCondition filterCondition = new FilterCondition();
		// set the condition type
		filterCondition.setCondition(Condition.IS_EQUAL_TO);
		// the field to be matched
		filterCondition.setField("resourceuuid");
		// and a list of values
		filterCondition.getValue().add(serverUUID);
		// add the filter condition to the query
		searchFilter.getFilterConditions().add(filterCondition);
		// limit to the number of results
		QueryLimit queryLimit = new QueryLimit();
		queryLimit.setMaxRecords(10000);
		queryLimit.setLoadChildren(true);
		
		ListResult resultList = service.listResources(searchFilter, queryLimit, ResourceType.SERVER);
		if (resultList == null) throw new Exception("Server UUID not found: " + serverUUID);
		List <Object> results = resultList.getList();
		if (results.size() == 0)  throw new Exception("Server UUID not found: " + serverUUID + " (empty list)");
		if (results.size() > 1)  log.warn("Server UUID found too many times: " + serverUUID + " (" + results.size() + ")");
		if (!(results.get(results.size() - 1) instanceof Server)) throw new Exception("Invalid object (Serve expected): " + results.get(results.size() - 1).getClass().getName());
		Server resultServer = (Server) results.get(results.size() - 1);
		log.debug("Server UUID found");
		ArrayList<Nic> nics;
		nics = (ArrayList<Nic>) resultServer.getNics();
		if (nics.size() == 0) throw new Exception("No NIC found for server UUID: " + serverUUID + "");
		if (nics.size() > 1) log.debug("Too many NIC found for server UUID: " + serverUUID + ". Considering the first only.");
		Nic nic0 = nics.get(0);
		if (nic0.getIpAddresses() == null || nic0.getIpAddresses().size() == 0) throw new Exception("No IP address for NIC 0");
		privateDnsName = nic0.getIpAddresses().get(0).getIpAddress();
		status = mapVMStatus(resultServer.getStatus());
		log.debug("Server name: " + resultServer.getResourceName());
		log.debug("IP: " + privateDnsName);
		log.debug("Status: " + status + " (" + resultServer.getStatus() + ")");
	}

	private String mapVMStatus(ServerStatus serverStatus) {
		if (serverStatus == null) return VM.UNKNOWN;
		switch (serverStatus) {
			case STARTING:
			case MIGRATING:
			case REBOOTING:
			case RECOVERY:
			case BUILDING:
			case INSTALLING:
				return VM.PENDING;
			case RUNNING:
				return VM.RUNNING;
			case DELETING:
				return VM.STOPPING;
			case ERROR:
				return VM.ERROR;
			case STOPPED:
				return VM.TERMINATED;
			case STOPPING:
				return VM.STOPPING;
			default:
				return VM.UNKNOWN;
		}
	}
	
	@Override public void terminate() throws Exception {
		log.debug("Delete server: " + serverUUID);
		if (serverUUID == null) throw new Exception("Server UUID is null");
		Job deleteJob = service.deleteResource(serverUUID, true, null);
		if (deleteJob == null) throw new Exception("Server UUID not found: " + serverUUID + " (null job)");
		deleteJob.setStartTime(datatypeFactory.newXMLGregorianCalendar(new GregorianCalendar()));
		log.debug("Waiting for Delete server job to complete...");
		Job response = service.waitForJob(deleteJob.getResourceUUID(), true);
		log.debug("Delete server job completed");
		if (response.getErrorCode() == null) {
			log.info("Server deleted: " + serverUUID);
		} else throw new Exception("Cannot terminate server: " + response.getErrorCode());
	}

	// try to do cloud-init until completed (SSH is up)
	private void emulateCloudInitWithRetries () {
		log.debug("Emulating cloud-init...");
		if (userDataBase64 == null) {
			log.debug("No cloud-config");
			return;
		}
		if (login == null) {
			log.error("No login name specified to perform cloud-init");
			return;
		}
		if (sshKeyPath == null) {
			log.error("No path to SSH key specified to perform cloud-init");
			return;
		}
		int trials = 0;
		final int sleep = 10; // seconds
		final int maxTrials = 5 * 6; // up to 5 minutes (6 trials / minute)  
		do {
			trials++;
			if (privateDnsName != null && VM.RUNNING.equals(status)) {
				try {
					emulateCloudInit(privateDnsName, login, sshKeyPath);
					log.debug("Cloud-init done");
					break;
				} catch (Exception e) {
					log.debug("emulateCloudInit failed: " + e.getMessage());
				}
			} else log.debug("VM has no IP or is not running (" + status + ")");
			//  no IP yet, do describe
			describeServerIfNotInProgress();
			log.debug("Trial " + trials + " failed. Retrying cloud-init in " + sleep + "s...");
			try { Thread.sleep(sleep * 1000l); } catch (InterruptedException e) {}
		} while (trials <= maxTrials);
		
		if (trials > maxTrials) log.error("Failed to run cloud-init");
	}
	
	private void emulateCloudInit(String ip, String login, String sshKeyPath) throws Exception {
		// mkdir -p /var/lib/cloud/instance/ 
		// /var/lib/cloud/instance/user-data.txt
		// sudo "/usr/bin/cloud-init -d modules || /usr/bin/cloud-init start"
		SshSession ssh = null;
		try {
			ssh = new SshSession(ip, login, sshKeyPath);
			OutputStreamWrapper stdout = new OutputStreamWrapper();
			OutputStreamWrapper stderr = new OutputStreamWrapper();
			int exitCode;
			
			// upload user-data.txt
			log.debug("Uploading /root/user-data.txt...");
			stdout.clear(); stderr.clear();
			exitCode = ssh.executeCommand("sudo echo " + userDataBase64 + " | base64 --decode > /root/user-data.txt", stdout, stderr);
			if (exitCode != 0) {
				log.debug(stderr.toString());
				throw new Exception("Cannot upload user-data.txt to host " + ip);
			}

			// optionally, before re-running cloud-init: rm -f /var/lib/cloud/instance/sem/*
			
			// run cloud-init for modules: cc_write_files, cc_runcmd (cc_package_update_upgrade_install); from /usr/lib/python2.7/dist-packages/cloudinit/config/
			log.debug("Running cloud-init cc_write_files, cc_runcmd sections from /root/user-data.txt...");
			stdout.clear(); stderr.clear();
			exitCode = ssh.executeCommand(	"sudo cloud-init --file /root/user-data.txt single --name cc_write_files " +
											"&& sudo cloud-init --file /root/user-data.txt single --name cc_runcmd " +
											"&& sudo /var/lib/cloud/instance/scripts/runcmd", stdout, stderr);
			if (exitCode != 0) {
				log.debug(stderr.toString());
				throw new Exception("Cannot run cloud-init on user-data.txt on host " + ip);
			}
			
		} finally {	if (ssh!= null) ssh.close(); }
	}
	
	@Override public void discard() {
		threadExecutor.shutdown();
	}
	
	public static void main(String [] args) throws Exception {
		String endpoint = "https://cp.sd1.flexiant.net/soap/user/current/?wsdl";
		String optimizerImageId = "800da8ae-d424-30e0-8eba-c690310da426";
		String username = "userEmail/customerUUID";
		String password = "password";
		FCOVM vm = new FCOVM.Builder(endpoint, username, password, optimizerImageId)
				.withInstanceType("m1.medium")
				.withDiskSize(16)
				.build();
		Map <String, String> params = new HashMap<String, String>();
		params.put(LOGIN, "root"); 
		params.put(SSH_KEY_PATH, "image-optimizer-service_priv.rsa");
		params.put(USER_DATA_BASE64, "I2Nsb3VkLWNvbmZpZw0Kd3JpdGVfZmlsZXM6DQotIHBhdGg6IC9yb290L29wdGltaXplLnNoDQogIHBlcm1pc3Npb25zOiAiMDcwMCINCiAgY29udGVudDogfA0KICAgICMhL2Jpbi9iYXNoDQpydW5jbWQ6DQotIGVjaG8gJ0hlbGxvIHdvcmxkIScgPiAvcm9vdC9oZWxsby13b3JsZC50eHQ=");
		vm.run(params);
		do {
			Thread.sleep(10000);
			log.debug("Polling VM status...");
			if (vm.serverUUID != null) try { vm.describeInstance(); } catch (Exception x) { log.debug(x.getMessage()); }
		} while (!vm.status.equals(VM.RUNNING));
		vm.terminate();
		vm.discard();
	}
}
