package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.wttarget;

import java.util.Map;
import java.util.logging.Logger;

import javax.ws.rs.core.MediaType;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;

import static hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VirtualMachine.VMState.VMREADY;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMManagementException;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VirtualMachine;

public class WTVM extends VirtualMachine {

	private static Logger log = Shrinker.myLogger;
	private static final int totalReqLimit = Integer.parseInt(System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxUsableCPUs"));
	private static AtomicInteger reqCounter = new AtomicInteger();
	
	public static final String ACCESS_KEY = "accessKey";
	public static final String SECRET_KEY = "secretKey";
	public static final String ENDPOINT = "endpoint";
	
	public static final String UNKNOWN = "unknown";
	public static final String PENDING = "pending";
	public static final String BOOTING = "booting";
	public static final String RUNNING = "running";
	public static final String SHUTDOWN = "shutting-down";
	public static final String STOPPING = "stopping";
	public static final String STOPPED = "stopped";
	public static final String TERMINATED = "terminated";
	public static final String ERROR = "error";

	// user-defined required parameters set in parseParameters
	private String endpoint;
	private String username;
	private String password;
	private String ovfURL;

	// VM status 
	private String vmId; 
	private String vmName;
	private String privateDnsName;
	private String status;
		
	public WTVM(Map<String, List<String>> contextandcustomizeVA, boolean testConformance, String imageUUID)  {
		super(imageUUID, contextandcustomizeVA, testConformance);
	}
	
	// NOTE: this is the only place where instance fields can be set, the constructor does not complete (nor field initialization) 
	// before calling runinstance
	@Override protected void parseVMCreatorParameters(Map<String, List<String>> parameters) {
		Shrinker.myLogger.fine("Parsing parameters for creating VMware VM"); 
		super.datacollectorDelay = 10000; // 10 seconds delay between polls
		if (parameters == null)
			throw new IllegalArgumentException("Missing parameters");
		if (!parameters.containsKey(ACCESS_KEY) || parameters.get(ACCESS_KEY) == null
				|| parameters.get(ACCESS_KEY).size() == 0 || parameters.get(ACCESS_KEY).get(0) == null)
			throw new IllegalArgumentException("Missing parameter: " + ACCESS_KEY);
		if (!parameters.containsKey(SECRET_KEY) || parameters.get(SECRET_KEY) == null
				|| parameters.get(SECRET_KEY).size() == 0 || parameters.get(SECRET_KEY).get(0) == null)
			throw new IllegalArgumentException("Missing parameter: " + SECRET_KEY);
		this.username = parameters.get(ACCESS_KEY).get(0);
		this.password = parameters.get(SECRET_KEY).get(0);
		if (parameters.containsKey(ENDPOINT) && parameters.get(ENDPOINT) != null && parameters.get(ENDPOINT).size() > 0) {
			this.endpoint = parameters.get(ENDPOINT).get(0);
			if (!this.endpoint.endsWith("/")) this.endpoint += "/";
		}
		if (parameters.containsKey(LOGIN_NAME) && parameters.get(LOGIN_NAME) != null
				&& parameters.get(LOGIN_NAME).size() > 0)
			super.loginName = parameters.get(LOGIN_NAME).get(0);
		
		this.ovfURL = System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ovfURL");
		if (this.ovfURL == null) Shrinker.myLogger.severe("ERROR: Missing WT parameter (system property): hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ovfURL");

		// do the rest of field initializations
		status = UNKNOWN;
	}

	// !!!NOTE: this is invoked before the constructor completes!
	@Override public String runInstance(String key) throws VMManagementException {
		Shrinker.myLogger.info("Trying to start VM... (" + getImageId() + " " + endpoint + ")");
		int requests = reqCounter.incrementAndGet();
		Shrinker.myLogger.info("runInstance requests " + requests + ", max: " + totalReqLimit);
		if (requests > totalReqLimit) {
			Shrinker.myLogger.severe("Terminating shrinking process, runInstance requests " + requests + " > " + totalReqLimit);
			Thread.dumpStack();
			System.exit(1);
		}
		// run VM
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Creating VM... (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
		try {
			runVM();
		} catch (Exception x) {
			log.severe("Cannot create VM: " + x.getMessage());
			status = TERMINATED;
			reqCounter.decrementAndGet(); // note: in the case of null instanceId, this.terminateInstance() is not called from VirtualMacine.terminate, therefore we must decrement here
			throw new VMManagementException("Cannot create server", x);
		}
		
		Shrinker.myLogger.info("VM started (" + getImageId() + ", " + endpoint + "): "+ getInstanceId());
		VirtualMachine.vmsStarted.incrementAndGet();
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] VM started: " + getInstanceId() + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
		
        return vmId;
	}
	
	@Override public String getInstanceId() { 
		return vmId; 
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
		
		boolean isinInitialState = initializingStates.contains(getState());
		if (isinInitialState) {
			super.setIP(null);
			super.setPort(null);
			super.setPrivateIP(null);
		}
		
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Describing VM: " + vmId + "... (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
		Shrinker.myLogger.fine("Describe server: " + vmId);

//		if (vmId == null) lookupVMId();
		if (vmId == null) return;

		describeVM();
		
		if (RUNNING.equals(status)) {
			super.setIP(privateDnsName);
			super.setPrivateIP(privateDnsName);
			super.setPort("22");
		}

		if (super.getIP() != null && super.getPort() != null && super.getPrivateIP() != null && isinInitialState)
			super.setState(VMREADY);

		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Describe done. Id: " + getInstanceId() + ", status: " + status + ", IP: " + privateDnsName + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
	}

	@Override public void terminateInstance() throws VMManagementException {
		Shrinker.myLogger.info("Terminating VM: " + getInstanceId());
		int requests = reqCounter.decrementAndGet();
		if (requests < 0) {
			Shrinker.myLogger.severe("Too much VM termination requests");
			Thread.dumpStack();
		}
		for (int i = 0; i < 2; i++) {
			try {
				terminateVM();
				return;
			} catch (VMManagementException x) {
				Shrinker.myLogger.warning("Failed to delete server: " + vmId + ". Retrying...");
				System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Failed to delete server: " + vmId + ". Retrying... (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
			}
		}
		try { Thread.sleep(10000); } catch (InterruptedException x) {}
		terminateVM();
	}
	
	@Override public void rebootInstance() throws VMManagementException {
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Rebooting VM " + vmId + " in WT... (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
		try {
			Shrinker.myLogger.info("Reboot server: " + vmId);
			rebootVM();
			Shrinker.myLogger.fine("Reboot server job completed");
		} catch (Exception x) {
			throw new VMManagementException("Cannot reboot instance", x);
		}
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] VM rebooted: " + getInstanceId() + " " + this.privateDnsName + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
	}

	private static String base64Encode(String value) {
		return value != null ? new String(Base64.encodeBase64(value.getBytes())) : "";
	}
	
	private void runVM() throws Exception {
		log.info("Launching VM in VMware...");
		Client client = null;
		try {
			String service = this.endpoint + "vms/deploy";
			log.info("Sending POST to '" + service + "'");
			log.info("Username: '" + username + "'");
			
			// disk: this.imageId
			// ovf template: this.
			// { "imageId": "http://s3entice.wtelecom.es:9000/minio/redmine/redmine.ovf", "imageUrl": "http://s3entice.wtelecom.es:9000/minio/redmine/redmine-disk1.vmdk" }
			JSONObject jsonContent = new JSONObject();
			jsonContent.put("imageUrl",getImageId());
			jsonContent.put("imageId", this.ovfURL);
			
			log.info("This is to post: " + jsonContent.toString());
			
			// send POST
			client = Client.create();
			WebResource webResource = client.resource(service);
			ClientResponse response = webResource
					.header("Authorization", "Basic " + base64Encode(this.username + ":" + this.password))
//					.type(MediaType.MULTIPART_FORM_DATA)
					.type(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class, jsonContent.toString());
			
			if (response.getStatus() != 200) {
				log.severe("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
				throw new Exception("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
			}
			
			String responseString = response.getEntity(String.class);
			log.info("VM created: " + responseString);
			
			JSONObject responseJSON = null;
	    	try { responseJSON = new JSONObject(new JSONTokener(responseString)); }
	    	catch (JSONException e) { 
				log.severe("Invalid JSON: " + e.getMessage());
				throw new Exception("Invalid JSON: " + e.getMessage());
	    	}
			
	    	JSONObject statusJSON = null;
	    	try { statusJSON = responseJSON.getJSONObject("status"); }
	    	catch (JSONException e) { 
				log.severe("Invalid JSON, key 'status' not found: " + e.getMessage());
				throw new Exception("Invalid JSON, key 'status' not found: " + e.getMessage());
	    	}
	    	
			String message = statusJSON.optString("message");
	    	String status = statusJSON.optString("status");
	    	this.status = mapVMStatus(status);
			this.vmName = statusJSON.optString("name");
			this.vmId = statusJSON.optString("vmid");
			
			log.info("vmId: " + this.vmId);
			log.info("name: " + this.vmName);
			log.info("status: " + this.status);
			log.info("message: " + message);

		} finally {
			if (client != null) client.destroy();
		}
	}

	private void describeVM() throws VMManagementException {
		log.info("Describe instance: " + vmId);
//		if (vmId == null) lookupVMId();
		if (vmId == null) {	log.severe("vmid not yet available"); return; }
		
		Client client = null;
		try {
			String service = endpoint + "vms/" + vmId;
			log.info("Sending GET to '" + service + "'");
			client = Client.create();
			WebResource webResource = client.resource(service);
			ClientResponse response = webResource				
					.header("Authorization", "Basic " + base64Encode(this.username + ":" + password))
					.get(ClientResponse.class);
			if (response.getStatus() != 200) {
				log.severe("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
				throw new VMManagementException("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class), null);
			} 
			// process response
			String responseString = response.getEntity(String.class);
			log.info("Processing response (status 200): " + responseString);
			JSONObject responseJSON = null;
	    	try { responseJSON = new JSONObject(new JSONTokener(responseString)); }
	    	catch (JSONException e) { 
				log.severe("Invalid JSON object: " + e.getMessage());
				throw new VMManagementException("Invalid JSON object: " + e.getMessage(), null);
	    	}
			
			this.status = mapVMStatus(responseJSON.optString("status"));
			if (RUNNING.equals(status)) {
				this.privateDnsName = responseJSON.optString("address");
			}
			
			log.info("Instance name: " + this.vmName);
			log.info("Instance id: " + this.vmId);
			log.info("IP: " + this.privateDnsName);
			log.info("Status: " + this.status);
			
			if (RUNNING.equals(status)) {
				super.setIP(privateDnsName);
				super.setPrivateIP(privateDnsName);
				super.setPort("22");
			}
		} finally {
			if (client != null) client.destroy();
		}
	}	

	/* private void lookupVMId() throws VMManagementException {
		if (vmId != null) {	log.fine("vmid already set"); return; }
		log.fine("Looking VM id by VM name: " + this.vmName);
		Client client = null;
		try {
			String service = endpoint + "vms";
			log.fine("Sending GET to '" + service + "'");
			client = Client.create();
			WebResource webResource = client.resource(service);
			ClientResponse response = webResource
					.header("Authorization", "Basic " + base64Encode(this.username + ":" + password))
					.get(ClientResponse.class);
			if (response.getStatus() != 200) {
				log.severe("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
				throw new VMManagementException("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class), null);
			}
			// returns json array of objects
			String responseString = response.getEntity(String.class);
			log.fine("VMs: " + responseString);
			JSONArray jsonArray;
	    	try { jsonArray = new JSONArray(new JSONTokener(responseString)); }
	    	catch (JSONException e) { 
				log.severe("Invalid JSON array: " + e.getMessage());
				throw new VMManagementException("Invalid JSON object: " + e.getMessage(), null);
	    	}
	    	for (int i = 0; i < jsonArray.length(); i++) {
		    	try { 
		    		JSONObject record = jsonArray.getJSONObject(i);
		    		String name = record.optString("name");
		    		String id = record.optString("vmid");
		    		if (this.vmName != null && this.vmName.equals(name)) {
		    			log.fine("VM name found in VMs listing");
		    			if (id != null) {
		    				this.vmId = id;
		    				log.info("vmid: " + this.vmId);
		    			}
		    			break;
		    		}
		    	} catch (JSONException e) { 
					log.severe("Invalid JSON: object " + e.getMessage());
					throw new VMManagementException("Invalid JSON object: " + e.getMessage(), null);
		    	}
	    	}
		} finally {
			if (client != null) client.destroy();
		}
	} */
	
	private String mapVMStatus(String serverStatus) {
		if (RUNNING.equalsIgnoreCase(serverStatus)) return RUNNING;
		else if (PENDING.equalsIgnoreCase(serverStatus)) return PENDING;
		else if (STOPPING.equalsIgnoreCase(serverStatus)) return STOPPING;
		else if (STOPPING.equalsIgnoreCase(serverStatus)) return STOPPING;
		else if (TERMINATED.equalsIgnoreCase(serverStatus)) return TERMINATED;
		else if (ERROR.equalsIgnoreCase(serverStatus)) return ERROR;
		return UNKNOWN;
	}

	private void rebootVM() throws Exception {
		log.info("Rebooting VM: " + vmId);
		if (vmId == null) throw new Exception("VM is null");
		Client client = null;
		try {
			String service = endpoint + "vms/" + vmId + "/reboot";
			log.fine("Sending PUT to '" + service + "'");
			client = Client.create();
			WebResource webResource = client.resource(service);
			// send PUT
			ClientResponse response = webResource
					.header("Authorization", "Basic " + base64Encode(this.username + ":" + password))
					.put(ClientResponse.class);
			if (response.getStatus() != 200) {
				log.severe("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
				throw new Exception("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
			}
			String responseString = response.getEntity(String.class);
			log.info("VM rebooted: " + responseString);
		} finally {
			if (client != null) client.destroy();
		}
	}
	
	public void discard() {
		log.fine("Discarding VM: " + vmName + ", id: " + vmId);
		this.vmId = null;
		this.vmName = null;
		this.privateDnsName = null;
		this.privateDnsName = null;
	}
	
	private void terminateVM() throws VMManagementException {
		log.info("Delete VM: " + vmId);
		if (vmId == null) throw new VMManagementException("Instance id is null", null);
		Client client = null;
		try {
			String service = endpoint + "vms/" + vmId;
			log.fine("Sending DELETE to '" + service + "'");
			client = Client.create();
			WebResource webResource = client.resource(service);
			// send POST
			ClientResponse response = webResource
					.header("Authorization", "Basic " + base64Encode(this.username + ":" + password))
					.delete(ClientResponse.class);
			if (response.getStatus() != 200) {
				log.severe("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
				throw new VMManagementException("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class), null);
			}
			String responseString = response.getEntity(String.class);
			log.info("VM terminated: " + responseString);
		} finally {
			if (client != null) client.destroy();
		}
		discard();
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] VM deleted: " + getInstanceId() + " " + this.privateDnsName + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
	}
}
