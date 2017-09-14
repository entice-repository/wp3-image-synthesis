package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.wt;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.xml.datatype.DatatypeConfigurationException;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.VM;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.utils.ResourceUtils;

// creates an image optimizer orchestrator VM in WT VMware
public class WTVM extends VM {
	
	private static final Logger log = LoggerFactory.getLogger(WTVM.class);
	public static final String CLOUD_INTERFACE = "wt";

	// user-defined required parameters (must be present in request json)
	private final String endpoint;
	private final String username;
	private final String password;
	
	// VM status 
	private String vmId = null;
	private String vmName = null; 
	private String privateDnsName;
	private String status = UNKNOWN;
	
	public static class Builder {
		// required parameters
		private final String endpoint;
		private final String username;
		private final String password;
		public Builder(String endpoint, String username, String password)  {
			this.endpoint = endpoint.endsWith("/") ? endpoint : endpoint + "/"; ;
			this.username = username;
			this.password = password;
		}
		public WTVM build() throws MalformedURLException, DatatypeConfigurationException, IOException {
			return new WTVM(this);
		}
	}
	private WTVM(Builder builder) {
		endpoint = builder.endpoint;
		username = builder.username;
		password = builder.password;
	}
	
	@Override public void run(Map<String, String> parameters) throws Exception {
		log.info("Launching VM in VMware...");
		Client client = null;
		try {
			String service = endpoint + "vms/orchestrator/deploy";
			log.debug("Sending POST to '" + service + "'");
			String userData = parameters.get(USER_DATA);
			
			// write user-data to a temp file
//			File fileToUpload;
//			try {
//				fileToUpload = File.createTempFile("user-data", "tmp");
//				PrintWriter out = new PrintWriter(fileToUpload);
//				
//				if (userData != null) out.println(userData);
//				out.close();
//			} catch (IOException x) {
//				throw new Exception("Cannot create temp file for user-data");
//			}
//			
//			// create multi-part body
//			FileDataBodyPart fileDataBodyPart = new FileDataBodyPart("file", fileToUpload, MediaType.APPLICATION_OCTET_STREAM_TYPE);
//	        fileDataBodyPart.setContentDisposition(FormDataContentDisposition.name("file").fileName(fileToUpload.getName()).build());
//			MultiPart formData = new FormDataMultiPart();
//			formData.bodyPart(fileDataBodyPart);

			log.debug("User-data: " + userData);

			
			// send POST
			client = Client.create();
			WebResource webResource = client.resource(service);
//			ClientResponse response = webResource
//					.header("Authorization", "Basic " + ResourceUtils.base64Encode(username + ":" + password))
//					.type(MediaType.MULTIPART_FORM_DATA)
//					.accept(MediaType.APPLICATION_JSON)
//					.post(ClientResponse.class, formData);
			ClientResponse response = webResource
					.header("Authorization", "Basic " + ResourceUtils.base64Encode(username + ":" + password))
					.type(MediaType.TEXT_PLAIN)
					.accept(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class, userData);

			
			// cleanup
//			try { formData.close(); } catch (IOException x) {} // silently ignore 
//			fileToUpload.delete();
			
			if (response.getStatus() != 200) {
				log.error("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
				throw new Exception("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
			}
			
			String responseString = response.getEntity(String.class);
			log.debug("VM created: " + responseString);
			
			JSONObject responseJSON = null;
	    	try { responseJSON = new JSONObject(new JSONTokener(responseString)); }
	    	catch (JSONException e) { 
				log.warn("Invalid JSON: " + e.getMessage());
				throw new Exception("Invalid JSON: " + e.getMessage());
	    	}
			
//	    	{
//	    		  "status": {
//	    		    "message": "New Orquestrator deploy launched, please wait. This process may take a while, the machine must restart several times",
//	    		    "name": "649-eos",
//	    		    "status": "Success",
//	    		    "vmid": "543"
//	    		  }
//	    	}

			JSONObject statusJSON = null;
	    	try { statusJSON = responseJSON.getJSONObject("status"); }
	    	catch (JSONException e) { 
				log.warn("Invalid JSON, key 'status' not found: " + e.getMessage());
				throw new Exception("Invalid JSON, key 'status' not found: " + e.getMessage());
	    	}
	    	
			String message = statusJSON.optString("message");
	    	String status = statusJSON.optString("status");
	    	this.status = mapVMStatus(status);
			this.vmName = statusJSON.optString("name");
			this.vmId = statusJSON.optString("vmid");
			
			log.info("status: " + this.status);
			log.info("vmId: " + this.vmId);
			log.info("name: " + this.vmName);
			log.debug("message: " + message);

		} finally {
			if (client != null) client.destroy();
		}
	}

	@Override public void describeInstance() throws Exception {
		log.debug("Describe instance: " + vmId);

//		if (vmId == null) lookupVMId();
		if (vmId == null) {	log.error("vmid not yet available"); return; }
		
		Client client = null;
		try {
			String service = endpoint + "vms/" + vmId;
			log.debug("Sending GET to '" + service + "'");
			client = Client.create();
			WebResource webResource = client.resource(service);
			ClientResponse response = webResource				
					.header("Authorization", "Basic " + ResourceUtils.base64Encode(username + ":" + password))
					.get(ClientResponse.class);
			if (response.getStatus() != 200) {
				log.error("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
				throw new Exception("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
			} 
			// process response
			String responseString = response.getEntity(String.class);
			log.debug("Processing response (status 200)");
			JSONObject responseJSON = null;
	    	try { responseJSON = new JSONObject(new JSONTokener(responseString)); }
	    	catch (JSONException e) { 
				log.error("Invalid JSON object: " + e.getMessage());
				throw new Exception("Invalid JSON object: " + e.getMessage());
	    	}
			
			this.status = mapVMStatus(responseJSON.optString("status"));
			this.privateDnsName = responseJSON.optString("address");
			
			log.info("Instance name: " + this.vmName);
			log.info("Instance id: " + this.vmId);
			log.info("IP: " + this.privateDnsName);
			log.info("Status: " + this.status);
		} finally {
			if (client != null) client.destroy();
		}
	}	

	/*
	private void lookupVMId() throws Exception {
		if (vmId != null) {	log.debug("vmid already set"); return; }
		log.debug("Looking VM id by VM name: " + this.vmName);
		Client client = null;
		try {
			String service = endpoint + "vms";
			log.debug("Sending GET to '" + service + "'");
			client = Client.create();
			WebResource webResource = client.resource(service);
			ClientResponse response = webResource
					.header("Authorization", "Basic " + ResourceUtils.base64Encode(username + ":" + password))
					.get(ClientResponse.class);
			if (response.getStatus() != 200) {
				log.error("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
				throw new Exception("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
			}
			// returns json array of objects
			String responseString = response.getEntity(String.class);
			log.debug("VMs: " + responseString);
			JSONArray jsonArray;
	    	try { jsonArray = new JSONArray(new JSONTokener(responseString)); }
	    	catch (JSONException e) { 
				log.error("Invalid JSON array: " + e.getMessage());
				throw new Exception("Invalid JSON object: " + e.getMessage());
	    	}
	    	for (int i = 0; i < jsonArray.length(); i++) {
		    	try { 
		    		JSONObject record = jsonArray.getJSONObject(i);
		    		String name = record.optString("name");
		    		String id = record.optString("vmid");
		    		if (this.vmName != null && this.vmName.equals(name)) {
		    			log.debug("VM name found in VMs listing");
		    			if (id != null) {
		    				this.vmId = id;
		    				log.info("vmid: " + this.vmId);
		    			}
		    			break;
		    		}
		    	} catch (JSONException e) { 
					log.error("Invalid JSON: object " + e.getMessage());
					throw new Exception("Invalid JSON object: " + e.getMessage());
		    	}
	    	}
		} finally {
			if (client != null) client.destroy();
		}
	} */
	
	// getters and setters
	@Override public String getInstanceId() { return vmId; }
	@Override public String getStatus() { return status; }
	@Override public String getIP() { return privateDnsName; }

	private String mapVMStatus(String serverStatus) {
		// FIXME
		if (VM.RUNNING.equalsIgnoreCase(serverStatus)) return VM.RUNNING;
		else if (VM.PENDING.equalsIgnoreCase(serverStatus)) return VM.PENDING;
		else if (VM.STOPPING.equalsIgnoreCase(serverStatus)) return VM.STOPPING;
		else if (VM.STOPPING.equalsIgnoreCase(serverStatus)) return VM.STOPPING;
		else if (VM.TERMINATED.equalsIgnoreCase(serverStatus)) return VM.TERMINATED;
		else if (VM.ERROR.equalsIgnoreCase(serverStatus)) return VM.ERROR;
		return VM.UNKNOWN;
	}
	
	@Override public void terminate() throws Exception {
		log.info("Delete VM: " + vmId);
		if (vmId == null) throw new Exception("Instance id is null");
		Client client = null;
		try {
			String service = endpoint + "vms/" + vmId;
			log.debug("Sending DELETE to '" + service + "'");
			client = Client.create();
			WebResource webResource = client.resource(service);
			// send POST
			ClientResponse response = webResource
					.header("Authorization", "Basic " + ResourceUtils.base64Encode(username + ":" + password))
					.delete(ClientResponse.class);
			if (response.getStatus() != 200) {
				log.error("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
				throw new Exception("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
			}
			String responseString = response.getEntity(String.class);
			log.info("VM terminated: " + responseString);
		} finally {
			if (client != null) client.destroy();
		}
		discard();
	}
	
	@Override public void reboot() throws Exception {
		log.info("Rebooting VM: " + vmId);
		if (vmId == null) throw new Exception("VM is null");
		Client client = null;
		try {
			String service = endpoint + (endpoint.endsWith("/") ? "" : "/") + "vms/" + vmId + "/reboot";
			log.debug("Sending PUT to '" + service + "'");
			client = Client.create();
			WebResource webResource = client.resource(service);
			// send PUT
			ClientResponse response = webResource
					.header("Authorization", "Basic " + ResourceUtils.base64Encode(username + ":" + password))
					.put(ClientResponse.class);
			if (response.getStatus() != 200) {
				log.error("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
				throw new Exception("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
			}
			String responseString = response.getEntity(String.class);
			log.info("VM rebooted: " + responseString);
		} finally {
			if (client != null) client.destroy();
		}
	}
	
	@Override public void discard() {
		log.debug("Discarding VM: " + vmName + ", id: " + vmId);
		this.vmId = null;
		this.vmName = null;
		this.privateDnsName = null;
		this.privateDnsName = null;
	}	
}
