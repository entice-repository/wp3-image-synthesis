package hu.mta.sztaki.lpds.entice.virtualimagedecomposer.rest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.jersey.api.client.ClientResponse.Status;

@Path("/tasks") 
public class VirtualImageDecomposer {
	private static final Logger log = LoggerFactory.getLogger(VirtualImageDecomposer.class); 
	private void logRequest(final String method, final HttpHeaders httpHeaders, final HttpServletRequest request) {
		log.info("" + method);
	}

	// request fields
	private final static String SOURCE_BASE_IMAGE_URL = "sourceBaseImageUrl";
	private final static String SOURCE_VIRTUAL_IMAGE_ID = "sourceVirtualImageId";
	private final static String INSTALLER_IDS = "installerIds";
	private final static String SNAPSHOT_URL = "snapshotUrl";
	private final static String KNOWLEDGE_BASE_REF = "knowledgeBaseRef";
	private final static String DEBUG = "debug"; // for devops only
	private final static String DEBUG_FILE = "debug"; // for devops only
	
	private final static String PARTITION = "partition";
	
	// response fields
	private final static String FRAGMENT_URL = "fragmentUrl";
	private final static String STATUS = "status";
	private final static String MESSAGE = "message";
	private enum CalculationStatus { DONE, PENDING, RUNNING, FAILED }

	// other constants
	final static String INPUTS_FILE = "input.sh";
	final static String DONE_FILE = "done";
	final static String FAILURE_FILE = "failure";
	final static String PHASE_FILE = "phase";
	final static String URL_FILE = "url";

	// queue of fragment computation tasks
	static ExecutorService fragmentComputationTasksQueue = Executors.newFixedThreadPool(NBDAllocation.SIZE); 
	
	@POST @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON) 
	public Response calculateFragment (
		@Context HttpHeaders headers,
		@Context HttpServletRequest request,
		@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
		String body) {
		logRequest("POST", headers, request);		
		if (Configuration.virtualImageDecomposerToken != null && !Configuration.virtualImageDecomposerToken.equals(token)) return Response.status(Status.BAD_REQUEST).entity("Missing authentication token").build();
		if (!new File (Configuration.virtualImageDecomposerPath).exists()) return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Working dir " + Configuration.virtualImageDecomposerPath + " does not exist").build();
		if (!new File (Configuration.scriptsDir).exists()) return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Scripts dir " + Configuration.scriptsDir + " does not exist").build();
		
		// parse input
		log.debug("Parsing JSON entity body...");
 	   	JSONObject requestBody = null;
        if (body != null && body.length() > 0) {
        	try { requestBody = new JSONObject(new JSONTokener(body)); }
    		catch (JSONException e) { return Response.status(Status.BAD_REQUEST).entity("Invalid JSON content: " + e.getMessage()).build(); }
        } else { return Response.status(Status.BAD_REQUEST).entity("Missing entity body!").build(); }

        // verify required parameters
        if ("".equals(requestBody.optString(SOURCE_BASE_IMAGE_URL))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + SOURCE_BASE_IMAGE_URL).build(); 
        if ("".equals(requestBody.optString(SOURCE_VIRTUAL_IMAGE_ID))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + SOURCE_VIRTUAL_IMAGE_ID).build(); 
        if ("".equals(requestBody.optString(KNOWLEDGE_BASE_REF))) log.warn("Missing parameter: " + KNOWLEDGE_BASE_REF); 
        if (requestBody.optJSONArray(INSTALLER_IDS) == null && "".equals(requestBody.optString(SNAPSHOT_URL))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + INSTALLER_IDS + " || " + SNAPSHOT_URL).build(); 
        
        String partition = "1";
        String volumeGroup = "";
        String logicalVolume = "";
        if (!"".equals(requestBody.optString(PARTITION))) {
        	String [] tempParition = requestBody.optString(PARTITION).split(" ");
        	if (tempParition.length == 1) {
        		partition = tempParition[0];
        	} else if (tempParition.length == 2) {
        		partition = "";
        		volumeGroup = tempParition[0];
        		logicalVolume = tempParition[1];
        	} else {
        		return Response.status(Status.BAD_REQUEST).entity("Invalid content in parameter " + PARTITION + ": '' or '<paritionNumber>' or  '<volumeGroup> <logicalVolume>' expected.").build();        		
        	}
        	
        }
        
		// create working dir
		String taskId = UUID.randomUUID().toString();
		String workingDir = Configuration.virtualImageDecomposerPath + "/" + taskId + "/";
		if (!new File(workingDir).exists()) {
			if (!new File (workingDir).mkdirs()) return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Cannot create directory: " + workingDir).build(); 		
		} else return Response.status(Status.BAD_REQUEST).entity("Task already exists: " + taskId).build(); // should not happen
		
		PrintWriter out = null;
		try {
			// JSON
//			out = new PrintWriter(workingDir + "/" + "input.json");
//			out.println(requestBody.toString()); 

			// SH
			out = new PrintWriter(workingDir + INPUTS_FILE);
			
			out.println("SOURCE_BASE_IMAGE_URL" + "=\"" + requestBody.optString(SOURCE_BASE_IMAGE_URL) + "\"");
			out.println("SOURCE_VIRTUAL_IMAGE_ID" + "=\"" + requestBody.optString(SOURCE_VIRTUAL_IMAGE_ID) + "\"");
			out.println("VIRTUAL_IMAGE_COMPOSER_URL" + "=\"" + Configuration.virtualImageComposerRestUrl + "/scripts/" + "\"");
			out.println("PARTITION" + "=\"" + partition + "\"");
			out.println("VOLUME_GROUP" + "=\"" + volumeGroup + "\"");
			out.println("LOGICAL_VOLUME" + "=\"" + logicalVolume + "\"");
			out.println("SNAPSHOT_URL" + "=\"" + requestBody.optString(SNAPSHOT_URL) + "\"");
			out.println("INSTALLER_IDS" + "=\"" + jsonArrayToSpaceSeparatedList(requestBody.optJSONArray(INSTALLER_IDS)) + "\"");
			out.println("INSTALLER_STORAGE_URL" + "=\"" + Configuration.installerStorageUrl + "/\""); // must use / at the end
			out.println("FRAGMENT_STORAGE_URL" + "=\"" + Configuration.fragmentStorageUrl + "/\""); // must use / at the end
			out.println("FRAGMENT_STORAGE_TOKEN" + "=\"" + Configuration.fragmentStorageToken + "\""); // must use / at the end
			out.println("KNOWLEDGE_BASE_REF" + "=\"" + requestBody.optString(KNOWLEDGE_BASE_REF, taskId) + "\"");
			out.println("FRAGMENT_ID" + "=\"" + requestBody.optString(KNOWLEDGE_BASE_REF, taskId) + "\"");
			out.println("DEBUG" + "=\"" + (requestBody.optBoolean(DEBUG) ? "true" : "false") + "\"");
			out.println("S3_ENDPOINT" + "=\"" + Configuration.s3Endpoint + "\"");
			out.println("S3_BUCKET_NAME" + "=\"" + Configuration.s3BucketName + "\"");
			out.println("export AWS_ACCESS_KEY_ID" + "=\"" + Configuration.s3AccessKey + "\"");
			out.println("export AWS_SECRET_ACCESS_KEY" + "=\"" + Configuration.s3SecretKey + "\"");
			
		} catch (IOException x) {
			log.error(x.getMessage(), x);
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Cannot save parameters file: " + INPUTS_FILE + " (" + x.getMessage() + ")").build() ; 		
		} finally { if (out != null) out.close(); }
		
		// queue fragment computation
		if (!fragmentComputationTasksQueue.isShutdown()) fragmentComputationTasksQueue.execute(new FragmentComputationTask(workingDir));
		else {
			log.info("fragmentComputationTasksQueue is down");
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("VirtualImageDecomposer is shutting down").build() ;
		}
		return Response.status(Status.OK).entity(taskId).build();
	}
	
	@GET @Path("{id}") @Produces(MediaType.APPLICATION_JSON)
	public Response getStatus (
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@PathParam("id") String id) {
		logRequest("GET STATUS " + id, headers, request);

		if (!id.matches("[-_a-zA-Z0-9]+")) return Response.status(Status.BAD_REQUEST).entity("Invalid id syntax").build();

		JSONObject response = new JSONObject();
		String workingDir = Configuration.virtualImageDecomposerPath + "/" + id + "/";

		// working dir exists: PENDING 
		if (!new File(workingDir).exists()) return Response.status(Status.BAD_REQUEST).entity("Working dir for task " + id + " not found: " + workingDir).build();

		// phase exists: message
		response.put(MESSAGE, "");
		if (new File(workingDir + PHASE_FILE).exists()) {
			// read phase content
			response.put(MESSAGE, fileContent(workingDir + PHASE_FILE));
		}

		// done file exists: DONE
		// failed exists: FAILED
		response.put(STATUS, CalculationStatus.PENDING.toString());
		if (new File(workingDir + DONE_FILE).exists()) response.put(STATUS, CalculationStatus.DONE.toString());
		if (new File(workingDir + FAILURE_FILE).exists()) {
			response.put(STATUS, CalculationStatus.FAILED.toString()); // failed even if done present
			// set message 
			response.put(MESSAGE, fileContent(workingDir + FAILURE_FILE) + ", phase: " + response.optString(MESSAGE));
		}

		// url exists: fragmentUrl
		response.put(FRAGMENT_URL, "");
		if (new File(workingDir + URL_FILE).exists()) {
			// read url content
			response.put(FRAGMENT_URL, fileContent(workingDir + URL_FILE));
		}
		
		return Response.status(Status.OK).entity(response.toString()).build();
	}
	
	@DELETE @Path("{id}") @Produces(MediaType.APPLICATION_JSON)
	public Response delete(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			@PathParam("id") String id) {
		logRequest("DELETE " + id, headers, request);

		if (!id.matches("[-_a-zA-Z0-9]+")) return Response.status(Status.BAD_REQUEST).entity("Invalid id syntax").build();
		if (Configuration.virtualImageDecomposerToken != null && !Configuration.virtualImageDecomposerToken.equals(token)) return Response.status(Status.BAD_REQUEST).entity("Missing authentication token").build();

		// if DONE delete working dir
		File workDir = new File(Configuration.virtualImageDecomposerPath + "/" + id + "/");
		if (!workDir.exists()) return Response.status(Status.BAD_REQUEST).entity("Working directory not found: " + workDir.getAbsolutePath()).build();

		File debugFile = new File(Configuration.virtualImageDecomposerPath + "/" + id + "/" + DEBUG_FILE);
		log.debug("Debug file: "  + debugFile + " exists: " + debugFile.exists());
		
		if (Configuration.testMode || debugFile.exists()) {
			log.warn("Test mode: keeping working directory: " + workDir);
			return Response.status(Status.OK).build();
		}
		if (!deleteDir(workDir)) return Response.status(Status.BAD_REQUEST).entity("Cannot delete working directory: " + workDir.getAbsolutePath()).build();
		return Response.status(Status.OK).build();
	}
	
	private boolean deleteDir(File file) {
	    File[] contents = file.listFiles();
	    if (contents != null) { 
	    	for (File f : contents) {
		    	if (!deleteDir(f)) {
		    		return false;
		    	}
		    }
	    }
	    if (!file.delete()) {
	    	log.warn("Cannot delete file or folder: " + file.getAbsolutePath());
	    	return false;
	    }
	    return true;
	}
	
	private String jsonArrayToSpaceSeparatedList(JSONArray array) {
		if (array == null) return "";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < array.length(); i++) {
			String e = array.optString(i);
			if (!"".equals(e)) sb.append(e + " ");
		}
		return sb.toString().trim();
	}
	
	private String fileContent(String path) {
		StringBuilder sb = new StringBuilder();
		Scanner scanner = null;
		try {
			scanner = new Scanner(new File(path));
			scanner.useDelimiter("\\Z");
			while (scanner.hasNext()) sb.append(scanner.next());
		} catch (FileNotFoundException x) { // should not happen
			log.warn("File not found: ", x);
		} finally {
			if (scanner != null) scanner.close();
		}
		return sb.toString().trim();
	}
}
