package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.wt;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
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
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.multipart.MultiPart;
import com.sun.jersey.multipart.file.FileDataBodyPart;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.VM;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.utils.ResourceUtils;

// creates orchestrator VM
public class WTVM extends VM {
	private static final Logger log = LoggerFactory.getLogger(WTVM.class);
	public static final String CLOUD_INTERFACE = "wt";
	
	private String userData; // cloud-init
	
	private String serverName; 
	
	// user-defined required parameters (must be present in request json)
	private final String endpoint;
	private final String username;
	private final String password;
	
	// VM status 
	private String vmId = null; // ID of the created server
	private String privateDnsName;
	private String status = UNKNOWN;
	
	public static class Builder {
		// required parameters
		private final String endpoint;
		private final String username;
		private final String password;
		
		public Builder(String endpoint, String username, String password)  {
			this.endpoint = endpoint;
			this.username = username;
			this.password = password;
		}
		public WTVM build() throws MalformedURLException, DatatypeConfigurationException, IOException {
			return new WTVM(this);
		}
	}
	
	private WTVM(Builder builder) throws MalformedURLException, DatatypeConfigurationException, IOException {
		username = builder.username;
		password = builder.password;
		endpoint = builder.endpoint;
	}
	
	private void listVMs() throws Exception {
		Client client = Client.create();
		String service = endpoint + (endpoint.endsWith("/") ? "" : "/") + "vms";
		log.debug("Sending GET '" + service + "'");
		WebResource webResource = client.resource(service);
		
		// send GET
		ClientResponse response = webResource
				.header("Authorization", "Basic " + ResourceUtils.base64Encode(username + ":" + password))
				.get(ClientResponse.class);
		
		if (response.getStatus() != 200) {
			log.error("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
			throw new Exception("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
		}
		
		String responseString = response.getEntity(String.class);
		log.info("VMs: " + responseString);
	}
	
	@Override public void run(Map<String, String> parameters) throws Exception {
		log.info("Launching optimizer VM in FCO...");
		
		// parameters for emulating cloud-init
		if (parameters != null) {
			userData = parameters.get(USER_DATA);
		}
		
		// run
		Client client = Client.create();
		String service = endpoint + (endpoint.endsWith("/") ? "" : "/") + "vms/orquestrator/deploy";
		log.debug("Sending POST '" + service + "'");
		WebResource webResource = client.resource(service);
		
		// write user data to temp file
		File fileToUpload = File.createTempFile("user-data", "tmp");
		PrintWriter out = new PrintWriter(fileToUpload);
		out.println(userData);
		out.close();
		
		// create multipart body
		FileDataBodyPart fileDataBodyPart = new FileDataBodyPart("file", fileToUpload, MediaType.APPLICATION_OCTET_STREAM_TYPE);
        fileDataBodyPart.setContentDisposition(FormDataContentDisposition.name("file").fileName(fileToUpload.getName()).build());
		MultiPart formData = new FormDataMultiPart();
		formData.bodyPart(fileDataBodyPart);

		// send POST
		ClientResponse response = webResource
				.header("Authorization", "Basic " + ResourceUtils.base64Encode(username + ":" + password))
				.type(MediaType.MULTIPART_FORM_DATA)
				.accept(MediaType.APPLICATION_JSON)
				.post(ClientResponse.class, formData);
		
		formData.close();
		
		if (response.getStatus() != 200) {
			log.error("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
			throw new Exception("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
		}
		
		String responseString = response.getEntity(String.class);
		log.info("VM created: " + responseString);
		JSONObject responseJSON = null;
    	try { responseJSON = new JSONObject(new JSONTokener(responseString)); }
    	catch (JSONException e) { 
			log.warn("Invalid JSON: " + e.getMessage());
			throw new Exception("Invalid JSON: " + e.getMessage());
    	}
		String status = responseJSON.optString("status");
		serverName = responseJSON.optString("name");
		String message = responseJSON.optString("message");
		log.info("status: " + status);
		log.info("name: " + serverName);
		log.info("message: " + message);
		
//		this.vmId = responseString;
	}
	
	// getters and setters
	@Override public String getInstanceId() { return vmId; }
	@Override public String getStatus() { return status; }
	@Override public String getIP() { return privateDnsName; }

	@Override public void describeInstance() throws Exception {
		log.debug("Describe server: " + vmId);
		if (vmId == null) return;
		Client client = Client.create();
		String service = endpoint + (endpoint.endsWith("/") ? "" : "/") + "vms/" + vmId;
		log.debug("Sending GET '" + service + "'");
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
		log.debug("Processing response (status 200): " + responseString);
		JSONObject responseJSON = null;
    	try { responseJSON = new JSONObject(new JSONTokener(responseString)); }
    	catch (JSONException e) { 
			log.error("Invalid JSON: " + e.getMessage());
			throw new Exception("Invalid JSON: " + e.getMessage());
    	}
		
		status = mapVMStatus(responseJSON.optString("status"));
		privateDnsName = responseJSON.optString("address");
		
		log.debug("IP: " + privateDnsName);
		log.debug("Status: " + status);
	}	

	private String mapVMStatus(String serverStatus) {
		if (serverStatus == null) return VM.UNKNOWN;
		// TODO
//		switch (serverStatus) {
//			case STARTING:
//			case MIGRATING:
//			case REBOOTING:
//			case RECOVERY:
//			case BUILDING:
//			case INSTALLING:
//				return VM.PENDING;
//			case RUNNING:
//				return VM.RUNNING;
//			case DELETING:
//				return VM.STOPPING;
//			case ERROR:
//				return VM.ERROR;
//			case STOPPED:
//				return VM.TERMINATED;
//			case STOPPING:
//				return VM.STOPPING;
//			default:
//				return VM.UNKNOWN;
//		}
		return serverStatus;
	}
	
	@Override public void terminate() throws Exception {
		log.debug("Delete server: " + vmId);
		if (vmId == null) throw new Exception("Server UUID is null");
		
		Client client = Client.create();
		String service = endpoint + (endpoint.endsWith("/") ? "" : "/") + "vms/" + vmId;
		log.debug("Sending DELETE '" + service + "'");
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
		
		discard();
	}
	
	@Override public void reboot() throws Exception {
		log.info("Reboot server: " + vmId);
		if (vmId == null) throw new Exception("VM is null");
		
		Client client = Client.create();
		String service = endpoint + (endpoint.endsWith("/") ? "" : "/") + "vms/" + vmId + "/reboot";
		log.debug("Sending PUT '" + service + "'");
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
	}
	
	@Override public void discard() {
		log.debug("Discarding VM: " + serverName + ", server UUID: " + vmId);
	}
	
	public static void main(String [] args) throws Exception {
		String endpoint = "http://entice-api.wtelecom.es";
		String username = "user";
		String password = "password";
		
		WTVM vm = new WTVM.Builder(endpoint, username, password).build();
		Map <String, String> params = new HashMap<String, String>();
		params.put(USER_DATA, ResourceUtils.base64Decode("I2Nsb3VkLWNvbmZpZw0Kd3JpdGVfZmlsZXM6DQotIHBhdGg6IC9yb290L29wdGltaXplLnNoDQogIHBlcm1pc3Npb25zOiAiMDcwMCINCiAgY29udGVudDogfA0KICAgICMhL2Jpbi9iYXNoDQpydW5jbWQ6DQotIGVjaG8gJ0hlbGxvIHdvcmxkIScgPiAvcm9vdC9oZWxsby13b3JsZC50eHQ="));
//		vm.run(params);
		
		vm.listVMs();
		vm.vmId = "474";
		vm.describeInstance();
//		vm.terminate();
//		do {
//			Thread.sleep(10000);
//			log.debug("Polling VM status...");
//			if (vm.vmId != null) try { vm.describeInstance(); } catch (Exception x) { log.debug(x.getMessage()); }
//		} while (!vm.status.equals(VM.RUNNING));
//		vm.terminate();
	}
}
