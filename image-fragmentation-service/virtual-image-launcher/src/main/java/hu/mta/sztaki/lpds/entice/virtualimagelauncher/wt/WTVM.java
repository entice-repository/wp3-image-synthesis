package hu.mta.sztaki.lpds.entice.virtualimagelauncher.wt;

import java.util.concurrent.Executors;

import javax.ws.rs.core.MediaType;
import java.util.concurrent.ExecutorService;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import hu.mta.sztaki.lpds.entice.virtualimagelauncher.fco.OutputStreamWrapper;
import hu.mta.sztaki.lpds.entice.virtualimagelauncher.fco.SshSession;
import hu.mta.sztaki.lpds.entice.virtualimagelauncher.rest.Configuration;

public class WTVM {
	
	private static final Logger log = LoggerFactory.getLogger(WTVM.class);
	public static final String cloudInterface = "wt";
	private static ExecutorService threadExecutor = Executors.newFixedThreadPool(1);
	
	public static final String UNKNOWN = "unknown";
	public static final String PENDING = "pending";
	public static final String BOOTING = "booting";
	public static final String RUNNING = "running";
	public static final String SHUTDOWN = "shutting-down";
	public static final String STOPPING = "stopping";
	public static final String STOPPED = "stopped";
	public static final String TERMINATED = "terminated";
	public static final String ERROR = "error";
	
	public static String runInstance(String endpoint, String username, String password, String cloudImageId, String runCommand) throws Exception {
		log.info("Launching VM in VMware...");
	
		String vmId = "undefined";
		String vmdkURL = "undefined";
		String ovfURL = "undefined";
		
		if (cloudImageId.contains(" ")) {
			String [] split = cloudImageId.split(" ");
			vmdkURL = split[0];
			ovfURL = split[1];
		} else {
			vmdkURL = cloudImageId;
			log.warn("cloudImageId misses image or ovf URL (no space found in id)");
		}
		
		Client client = null;
		try {
			String service = endpoint + "vms/deploy";

			JSONObject jsonContent = new JSONObject();
			jsonContent.put("imageUrl",vmdkURL);
			jsonContent.put("imageId", ovfURL);
			log.debug("Sending POST to '" + service + "' " + jsonContent.toString());
			
			// send POST
			client = Client.create();
			WebResource webResource = client.resource(service);
			ClientResponse response = webResource
					.header("Authorization", "Basic " + base64Encode(username + ":" + password))
					.type(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class, jsonContent.toString());
			
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
			
			JSONObject statusJSON = null;
	    	try { statusJSON = responseJSON.getJSONObject("status"); }
	    	catch (JSONException e) { 
				log.warn("Invalid JSON, key 'status' not found: " + e.getMessage());
				throw new Exception("Invalid JSON, key 'status' not found: " + e.getMessage());
	    	}
	    	
			String message = statusJSON.optString("message");
	    	String name = statusJSON.optString("name");
	    	String status = statusJSON.optString("status");
			vmId = statusJSON.optString("vmid");
			
			log.debug("message: " + message);
			log.debug("name: " + name);
			log.debug("status: " + status);
			log.info("vmId: " + vmId);

			threadExecutor.execute(new VMInitThread(endpoint, username, password, vmId, runCommand));
			
		} finally {
			if (client != null) client.destroy();
		}
		return vmId;
	}
	
	private static String base64Encode(String value) {
		return value != null ? new String(Base64.encodeBase64(value.getBytes())) : "";
	}
	
	
	private static class VMInitThread implements Runnable {
		String endpoint;
		String username, password;
		private String vmId;
		private String runCommand;
		
		private String status;
		private String ip = null;
		
		VMInitThread (String endpoint, String username, String password, String vmId, String runCommand) {
			this.endpoint = endpoint;
			this.username = username;
			this.password = password;
			this.vmId = vmId;
			this.runCommand = runCommand;
		}
		
		@Override public void run() {
			log.debug("VM init thread started");
			
			// wait for ip
			long timeout20mins = 20 * 60 * 60 * 1000l; // 20 mins in millis 
			long timeout5mins = 5 * 60 * 60 * 1000l; // 5 mins in millis 

			log.info("Waiting for VM IP...");
			long start = System.currentTimeMillis();
			while (System.currentTimeMillis() - start < timeout20mins) {
				try { describeVM(); } 
				catch (Exception x) {
					log.error("describeVM exception", x);
					return; // failed
				}
				if (RUNNING.equals(status) && this.ip != null) {
					log.info("VM " + this.vmId + " has IP: " + this.ip);
					break;
				}
				try { Thread.sleep(10000); } catch (Exception x) {} // sleep 10 sec
			}
			
			if (this.ip == null) {
				log.error("No IP within timeout: " + timeout20mins + " ms");
				return;
			} 
			
			// wait for sshd and run command
			log.info("Waiting for SSH connection...");
			start = System.currentTimeMillis();
			while (System.currentTimeMillis() - start < timeout5mins) {
				try { 
					if (runCommand()) break; 
				} catch (Exception x) {} // ignore exception, since maybe sshd is not yet up
				try { Thread.sleep(10000); } catch (Exception x) {} // sleep 10 sec
			}
		}
		
		private void describeVM() throws Exception {
			log.debug("Describe VM: " + vmId);
			Client client = null;
			try {
				String service = endpoint + "vms/" + vmId;
				log.info("Sending GET to '" + service + "'");

				client = Client.create();
				WebResource webResource = client.resource(service);
				ClientResponse response = webResource				
						.header("Authorization", "Basic " + base64Encode(this.username + ":" + password))
						.get(ClientResponse.class);
				if (response.getStatus() != 200) throw new Exception("WT API " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class), null);
				// process response
				String responseString = response.getEntity(String.class);
				log.debug("Processing response (status 200)");
				JSONObject responseJSON = null;
		    	try { responseJSON = new JSONObject(new JSONTokener(responseString)); }
		    	catch (JSONException e) { throw new Exception("Invalid JSON object: " + e.getMessage(), null); }
				
				this.status = mapVMStatus(responseJSON.optString("status"));
				if (RUNNING.equals(status)) {
					String address = responseJSON.optString("address");
					if (!"".equals(address)) this.ip = address;
				}
				
				log.info("IP: " + this.ip);
				log.info("Status: " + this.status);
			} catch (ClientHandlerException x) { // thrown at get
				throw new Exception("ClientHandlerExeption", x);
			} finally {
				if (client != null) client.destroy();
			}
		}	
		
		private String mapVMStatus(String serverStatus) {
			if (RUNNING.equalsIgnoreCase(serverStatus)) return RUNNING;
			else if (PENDING.equalsIgnoreCase(serverStatus)) return PENDING;
			else if (STOPPING.equalsIgnoreCase(serverStatus)) return STOPPING;
			else if (STOPPING.equalsIgnoreCase(serverStatus)) return STOPPING;
			else if (TERMINATED.equalsIgnoreCase(serverStatus)) return TERMINATED;
			else if (ERROR.equalsIgnoreCase(serverStatus)) return ERROR;
			return UNKNOWN;
		}

		private boolean runCommand() throws Exception {
			SshSession ssh = null;
			try {
				log.debug("Opening ssh connection to root@" + this.ip + " with ssh key: " + Configuration.sshKeyPath);
				ssh = new SshSession(ip, "root", Configuration.sshKeyPath);
				OutputStreamWrapper stdout = new OutputStreamWrapper();
				OutputStreamWrapper stderr = new OutputStreamWrapper();
				int exitCode;
				exitCode = ssh.executeCommand(runCommand, stdout, stderr);
				if (exitCode != 0) {
					log.error("Cannot run " + runCommand + ": " + stderr.toString());
				} else {
					log.debug("Successfully executed: " + runCommand);
					return true;
				}
			} finally {	if (ssh!= null) ssh.close(); }
			return false;
		}
	}
}