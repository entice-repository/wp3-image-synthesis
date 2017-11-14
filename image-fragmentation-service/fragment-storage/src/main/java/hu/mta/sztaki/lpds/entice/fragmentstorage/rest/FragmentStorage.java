package hu.mta.sztaki.lpds.entice.fragmentstorage.rest;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.ClientResponse.Status;

/*
	curl -X POST -H "token: entice" -H "Content-type: application/gzip" --data-binary @x.tar.gz http://192.168.153.217:8080/fragment-storage/rest/fragments/1234567890
	curl -X GET http://192.168.153.217:8080/fragment-storage/rest/fragments/1234567890
*/

@Path("/fragments") 
public class FragmentStorage {

	private static final Logger log = LoggerFactory.getLogger(FragmentStorage.class); 
	private void logRequest(final String method, final HttpHeaders httpHeaders, final HttpServletRequest request) {
		log.info("" + method);
	}

	private static final String DELTA_FILE = "delta-package.tar.gz";
	private static final int DEFAULT_BUFFER_SIZE = 16 * 1024; // 16k
	
	@GET @Path("{id}/{cloud}") @Produces("application/gzip")
	public Response getFragment(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			@PathParam("id") String id,
			@PathParam("cloud") String cloud) {
		// Note: this storage ignores cloud
		return getFragment(headers, request, token, id);
	}
	
	@GET @Path("{id}") @Produces("application/gzip")
	public Response getFragment(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			@PathParam("id") String id) {
		logRequest("GET", headers, request);
		if (!id.matches("[-_a-zA-Z0-9]+")) return Response.status(Status.BAD_REQUEST).entity("Invalid id syntax").build();

		log.debug("Fragment file: " + Configuration.fragmentStoragePath + "/" + id + "/" + DELTA_FILE);
		if (!new File (Configuration.fragmentStoragePath).exists()) return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Storage path " + Configuration.fragmentStoragePath + " does not exist").build(); 
		if (!new File(Configuration.fragmentStoragePath + "/" + id).exists()) return Response.status(Status.BAD_REQUEST).entity("Fragment id not found: " + id).build();
		File fragmentFile = new File(Configuration.fragmentStoragePath + "/" + id + "/" + DELTA_FILE);
		if (!fragmentFile.exists()) return Response.status(Status.BAD_REQUEST).entity("Fragment not found: " + fragmentFile.getAbsolutePath()).build();
		return Response.ok(fragmentFile, MediaType.APPLICATION_OCTET_STREAM)
						 .header("Content-Disposition", "attachment; filename=\"" + id + ".tar.gz" + "\"" )
						 .build();
	} 	 

	@DELETE @Path("{id}")
	public Response deleteFragment(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			@PathParam("id") String id) {
		logRequest("DELETE", headers, request);
		if (Configuration.fragmentStorageToken != null && !Configuration.fragmentStorageToken.equals(token)) return Response.status(Status.BAD_REQUEST).entity("Missing authentication token").build();

		if (!id.matches("[-_a-zA-Z0-9]+")) return Response.status(Status.BAD_REQUEST).entity("Invalid id syntax").build();
		log.debug("Fragment dir: " + Configuration.fragmentStoragePath + "/" + id + "/");
		if (!new File (Configuration.fragmentStoragePath).exists()) return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Storage path " + Configuration.fragmentStoragePath + " does not exist").build(); 
		if (!new File(Configuration.fragmentStoragePath + "/" + id).exists()) return Response.status(Status.BAD_REQUEST).entity("Fragment id not found: " + id).build();
		File fragmentDir = new File(Configuration.fragmentStoragePath + "/" + id + "/");
		try {
			deleteDir(fragmentDir);
		} catch (Exception x) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Cannot delete fragment dir: " + fragmentDir.getAbsolutePath() + ": " + x.getMessage()).build();
		}
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
	
	@POST @Consumes({MediaType.APPLICATION_OCTET_STREAM, "application/gzip"})
	public Response storeFragment(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token) {
		logRequest("POST", headers, request);	
		return saveFragment(headers, request, token, null);
	} 

	@POST @Path("{id}") @Consumes({MediaType.APPLICATION_OCTET_STREAM, "application/gzip"})
	public Response storeFragmentWithId(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			@PathParam("id") String id) {
		logRequest("POST ID", headers, request);	
		return saveFragment(headers, request, token, id);
	} 
	
	private Response saveFragment(
			HttpHeaders headers,
			HttpServletRequest request,
			String token, 
			String fragmentId) {
		// check password
		if (Configuration.fragmentStorageToken != null && !Configuration.fragmentStorageToken.equals(token)) return Response.status(Status.BAD_REQUEST).entity("Missing authentication token").build();

		if (!new File (Configuration.fragmentStoragePath).exists()) return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Storage path " + Configuration.fragmentStoragePath + " does not exist").build();
		String id = fragmentId == null ? UUID.randomUUID().toString() : fragmentId;
		String fragmentDirName = Configuration.fragmentStoragePath + "/" + id;

		File fragmentFile = new File(fragmentDirName + "/" + DELTA_FILE);
		if (!new File(fragmentDirName).exists()) {
			if (!new File (fragmentDirName).mkdirs()) return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Cannot create fragment directory: " + fragmentDirName).build(); 		
		} else if (fragmentFile.exists()) return Response.status(Status.BAD_REQUEST).entity("Fragment already exists: " + fragmentFile.getAbsolutePath()).build();

		log.debug("Fragment file: " + fragmentFile.getAbsolutePath());
		
		InputStream in = null;
        OutputStream out = null;
        try {
        	in = request.getInputStream();
        	out = new BufferedOutputStream(new FileOutputStream(fragmentFile));
            byte [] buffer = new byte[DEFAULT_BUFFER_SIZE];
	        int readBytes;
	        while ((readBytes = in.read(buffer)) > 0) out.write(buffer, 0, readBytes); 
        } catch (IOException x) {
        	log.error(x.getMessage(), x);
        	return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Error while saving fragment: " + x.getMessage()).build(); 		
        }
        finally { 
        	if (in != null) try { in.close(); } catch(IOException e) {}
        	if (out != null) try { out.close(); } catch(IOException e) {}
        }
		return Response.status(Status.OK).entity(id).build();
	} 	
}