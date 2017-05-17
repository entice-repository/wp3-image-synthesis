package hu.mta.sztaki.lpds.entice.virtualimagelauncher.wt;

import javax.ws.rs.core.MediaType;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class WTVM {
	
	private static final Logger log = LoggerFactory.getLogger(WTVM.class);
	public static final String cloudInterface = "wt";

	public static String runInstance(String endpoint, String username, String password, String userDataBase64, String cloudImageId) throws Exception {
		log.info("Launching VM in VMware...");
		String vmId = "";

		String vmdkURL = "";
		String ovfURL = "";

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
			log.debug("Sending POST to '" + service + "'");
			
			JSONObject post = new JSONObject();
			post.put("vmdkUrl", vmdkURL);
			post.put("ovfUrl", ovfURL);
			post.put("userData", userDataBase64);
			
			// send POST
			client = Client.create();
			WebResource webResource = client.resource(service);
			ClientResponse response = webResource
					.header("Authorization", "Basic " + base64Encode(username + ":" + password))
					.type(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class, post.toString());
			
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

		} finally {
			if (client != null) client.destroy();
		}
		return vmId;
	}
	
	private static String base64Encode(String value) {
		return value != null ? new String(Base64.encodeBase64(value.getBytes())) : "";
	}
}