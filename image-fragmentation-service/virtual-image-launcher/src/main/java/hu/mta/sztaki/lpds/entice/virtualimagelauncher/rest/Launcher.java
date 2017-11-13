package hu.mta.sztaki.lpds.entice.virtualimagelauncher.rest;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import hu.mta.sztaki.lpds.entice.virtualimagelauncher.ec2.EC2VM;
import hu.mta.sztaki.lpds.entice.virtualimagelauncher.fco.FCOVM;
import hu.mta.sztaki.lpds.entice.virtualimagelauncher.wt.WTVM;

import com.sun.jersey.api.client.ClientResponse.Status;

@Path("/launcher")
public class Launcher {
	private static final Logger log = LoggerFactory.getLogger(Launcher.class); 
	private void logRequest(final String method, final HttpHeaders httpHeaders, final HttpServletRequest request) {
		log.info("" + method);
	}
	
	private static final String CLOUD = "cloud";
	private static final String CLOUD_INTERFACE = "cloudInterface"; // OPTIONAL (default: properties file || ec2)
	private static final String EC2_ENDPOINT = "endpoint";
	private static final String ACCESS_KEY = "accessKey";
	private static final String SECRET_KEY = "secretKey";
	private static final String VIRTUAL_IMAGE_ID = "virtualImageId";
	private static final String INSTANCE_TYPE = "instanceType";
	private static final String KEYPAIR_NAME = "keypair";
	private static final String CONTEXTUALIZATION = "contextualization";
	private static final String CLOUD_IMAGE_ID = "cloudImageId";
	
	@POST @Consumes(MediaType.APPLICATION_JSON)
	public Response launchVirtualImage (
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			String body) {
		logRequest("POST", headers, request);
		Client client = new Client();
		try {
			log.debug("Parsing JSON entity body...");
			JSONObject requestBody = null;
	        if (body != null && body.length() > 0) {
	    		try { requestBody = new JSONObject(new JSONTokener(body)); }
	    		catch (JSONException e) { return Response.status(Status.BAD_REQUEST).entity("Invalid JSON content: " + e.getMessage()).build(); }
	        } else { return Response.status(Status.BAD_REQUEST).entity("Missing entity body!").build(); }
	
	        if ("".equals(requestBody.optString(EC2_ENDPOINT))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + EC2_ENDPOINT).build(); 
	        if ("".equals(requestBody.optString(ACCESS_KEY))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + ACCESS_KEY).build(); 
	        if ("".equals(requestBody.optString(SECRET_KEY))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + SECRET_KEY).build(); 
	        if ("".equals(requestBody.optString(VIRTUAL_IMAGE_ID))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + VIRTUAL_IMAGE_ID).build(); 
	        if ("".equals(requestBody.optString(CLOUD_IMAGE_ID)) &&	"".equals(requestBody.optString(CLOUD))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + CLOUD + " or " + CLOUD_IMAGE_ID).build(); 
	        
	        String virtualImageId = requestBody.optString(VIRTUAL_IMAGE_ID);
	        String cloud = requestBody.optString(CLOUD);
	        String cloudImageId = requestBody.optString(CLOUD_IMAGE_ID);
	        String cloudPostfix = "".equals(cloud) ? "" : "/" + cloud;

	        
	        String cloudInterface = requestBody.optString(CLOUD_INTERFACE, EC2VM.cloudInterface);
	        
	        String resourceURL;
	        WebResource webResource;
	        ClientResponse response;
	        // get proprietary base image id, if not explicitly specified
	        if ("".equals(cloudImageId)) {
				resourceURL = Configuration.virtualImageManagerRestURL + "/virtualimages/" + virtualImageId + "/cloudimageids/" + cloud;
	        	log.debug("Getting cloud image id: " + resourceURL);
				webResource = client.resource(resourceURL);
				response = webResource.get(ClientResponse.class);
				if (response.getStatus() != 200) {
					log.warn("Cannot get cloud image id for virtual image: " + virtualImageId); 
					log.info("Service " + resourceURL + " HTTP error code: " + response.getStatus());
					return Response.status(response.getStatus()).entity(response.getEntity(String.class)).build();
				} else {
					cloudImageId = response.getEntity(String.class).trim();
					log.info("Proprietary image id: " + cloudImageId + " of virtual image: " + virtualImageId);
				}
	        }        
	        
	        // create contextualization (with runcmd section to install fragments)
	        String userDataBase64 = "";
	        if (!"".equals(requestBody.optString(CONTEXTUALIZATION))) {
	        	// FIXME check cloud-init merging
	        	String userData = base64Decode(requestBody.optString(CONTEXTUALIZATION));
	    		String emtyRuncmdPattern = "^#cloud-config[\\s\\S]*runcmd:[\\s]*[\\w][\\s\\S]*"; // empty runcmd section
	    		if (userData.matches(emtyRuncmdPattern)) {
	    			log.debug("Empty runcmd section");
	    			// delete runcmd line
	    			userData = userData.replaceAll("runcmd:", "");
	    		}        	
	        	if (!userData.contains("runcmd:")) {
	        		userData += "\nruncmd:\n";
	        		userData += "- wget --tries=3 -qO- " + Configuration.virtualImageComposerRestURL + "/scripts/" + virtualImageId + cloudPostfix + " | sh || echo 'Cannot download fragment assembly script' > .delta-failure\n";
	        		userDataBase64 = base64Encode(userData);
	        	} else {
	        		String runcmdWithItem = "^#cloud-config[\\s\\S]*runcmd:[\\s]*-[\\s\\S]*"; // non-empty runcmd section
	        		if (!userData.matches(runcmdWithItem)) return Response.status(Status.BAD_REQUEST).entity("Contextualization has invalid sytax (must match: " + runcmdWithItem + ")").build();	
	        		log.debug("runcmd section with item(s)");
	        		String runcmd = "runcmd:";
	        		int runcmdPosition = userData.indexOf(runcmd);
		        	int runcmdItemPosition = userData.indexOf("-", runcmdPosition);
		        	String tab = userData.substring(runcmdPosition + runcmd.length(), runcmdItemPosition);
		        	StringBuilder newUserData = new StringBuilder();
		        	newUserData.append(userData.substring(0, runcmdPosition + runcmd.length()));
		        	newUserData.append(tab);
		        	newUserData.append("- wget --tries=3 -qO- " + Configuration.virtualImageComposerRestURL + "/scripts/" + virtualImageId + cloudPostfix + " | sh || echo 'Cannot download fragment assembly script' > .delta-failure");
		        	newUserData.append(userData.substring(runcmdPosition + runcmd.length()));
		        	userDataBase64 = base64Encode(newUserData.toString());
	        	}
	        } else { // No user-defined contextualization, just create a brand new
	        	
	        	StringBuilder userData = new StringBuilder();
	        	userData.append("#cloud-config\n");
	        	// we assume wget is available on base OS
//	        	userData.append("packages:\n");
//	        	userData.append("- wget\n");
	        	userData.append("runcmd:\n");
	        	userData.append("- wget --tries=3 -qO- " + Configuration.virtualImageComposerRestURL + "/scripts/" + virtualImageId + cloudPostfix + " | sh && test ${PIPESTATUS[0]} == 0 || echo 'Could not download or execute fragment assembly script' >> /var/log/image-assembly.log\n");
	        	userDataBase64 = base64Encode(userData.toString());
	        }
	        
	        // launch VM
	        String instanceId;
	        try { 
	        	if (cloudInterface.equalsIgnoreCase(EC2VM.cloudInterface)) {
		        	instanceId = EC2VM.runInstance(requestBody.optString(EC2_ENDPOINT), requestBody.optString(ACCESS_KEY), requestBody.optString(SECRET_KEY), 
		        					cloudImageId, requestBody.optString(INSTANCE_TYPE, "m1.small"), requestBody.optString(KEYPAIR_NAME), userDataBase64);
	        	} else if (cloudInterface.equalsIgnoreCase(FCOVM.cloudInterface)) {
	        		FCOVM vm = new FCOVM.Builder(requestBody.optString(EC2_ENDPOINT), requestBody.optString(ACCESS_KEY), requestBody.optString(SECRET_KEY), cloudImageId)
	        				.withInstanceType(requestBody.optString(INSTANCE_TYPE, "m1.small"))
	        				.withDiskSize(16) // GB
	        				.withKeypair(requestBody.optString(KEYPAIR_NAME))
	        				.build();  
	        		vm.run(userDataBase64);
	        		instanceId = vm.getInstanceId();vm.run(userDataBase64);
	        	} else if (cloudInterface.equalsIgnoreCase(WTVM.cloudInterface)) {
	        		instanceId = WTVM.runInstance(requestBody.optString(EC2_ENDPOINT), requestBody.optString(ACCESS_KEY), requestBody.optString(SECRET_KEY), userDataBase64, cloudImageId);
	        	} else {
	        		return Response.status(Status.BAD_REQUEST).entity("Unsupported cloud interface: " + cloudInterface).build();
	        	}

	        } catch (Exception x) {
	        	return Response.status(Status.BAD_REQUEST).entity(x.getMessage()).build();	
	        }
			return Response.status(Status.OK).entity(instanceId).build();
		} finally { client.destroy(); }
	} 

	public static String base64Encode(String value) {
		return value != null ? new String(Base64.encodeBase64(value.getBytes())) : "";
	}
	
	public static String base64Decode(String value) {
		return value != null ? new String(Base64.decodeBase64(value.getBytes())) : "";
	}

    /*
    // forward response 
      Response clientResponse = target.request().get();
try {
    InputStream input = clientResponse.readEntity(InputStream.class);
    OutputStream output = response.getOutputStream();
    int next;
    while((next = input.read()) != -1) {
        output.write(next);
    }
} catch (IOException e) {
    ...
}
     */
}