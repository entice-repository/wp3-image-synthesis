package hu.mta.sztaki.lpds.entice.installerstorage.rest;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.ClientResponse.Status;

@Path("/installers") 
public class Installers {
	private static final Logger log = LoggerFactory.getLogger(Installers.class); 
	private void logRequest(final String method, final HttpHeaders httpHeaders, final HttpServletRequest request) {
		log.info("" + method);
	}

//	private static final int DEFAULT_BUFFER_SIZE = 16 * 1024; // 16k
	static final String INSTALLER_INSTALL_SCRIPT_FILE_NAME = "install.sh";
	static final String INSTALLER_INIT_SCRIPT_FILE_NAME = "init.sh";
	static final String INSTALLER_METADATA_FILE_NAME = "metadata.json";

	@SuppressWarnings("unused")	private final static String ID = "id";
	@SuppressWarnings("unused")	private final static String CREATED = "created";
	
	public static Map<String, JSONObject> installerMetadata = new ConcurrentHashMap<String, JSONObject>();
	
	@GET @Path("{id}") @Produces(MediaType.APPLICATION_JSON)
	public Response getInstallerMetadata (
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@PathParam("id") String id) {
		logRequest("GET METADATA " + id, headers, request);
		if (!installerMetadata.containsKey(id)) return Response.status(Status.BAD_REQUEST).entity("Invalid installer id: " + id).build();
		return Response.status(Status.OK).entity(installerMetadata.get(id).toString()).build();
	}	
	
	@GET @Produces(MediaType.APPLICATION_JSON)
	public Response getAllInstallerMetadata (
			@Context HttpHeaders headers,
			@Context HttpServletRequest request) {
		logRequest("GET ALL METADATA", headers, request);
		JSONArray response = new JSONArray(installerMetadata.values());
		return Response.status(Status.OK).entity(response.toString()).build();
	}	
	
	@GET @Path("{id}/install") @Produces("text/x-shellscript")
	public Response getInstallScript (
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			@PathParam("id") String id) {
		logRequest("GET INSTALL", headers, request);
		if (!installerMetadata.containsKey(id)) return Response.status(Status.BAD_REQUEST).entity("Invalid installer id: " + id).build();
		File installFile = new File(Configuration.installerStoragePath + "/" + id + "/" + Installers.INSTALLER_INSTALL_SCRIPT_FILE_NAME);
		if (!installFile.exists()) return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Install script file is missing: " + installFile.getAbsolutePath()).build(); 
		return Response.ok(installFile, "text/x-shellscript")
						 .header("Content-Disposition", "attachment; filename=\"" + id + "-install.sh" + "\"" )
						 .build();
	} 	 

	@GET @Path("{id}/init") @Produces("text/x-shellscript")
	public Response getInitScript (
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			@PathParam("id") String id) {
		logRequest("GET INIT", headers, request);
		if (!installerMetadata.containsKey(id)) return Response.status(Status.BAD_REQUEST).entity("Invalid installer id: " + id).build();
		File initFile = new File(Configuration.installerStoragePath + "/" + id + "/" + Installers.INSTALLER_INIT_SCRIPT_FILE_NAME);
		if (!initFile.exists()) { 
			return Response.ok()
				 .header("Content-Disposition", "attachment; filename=\"" + id + "-init.sh" + "\"" )
				 .entity("#!/bin/sh\n")
				 .build();
//			return Response.status(Status.BAD_REQUEST).entity("Init script file is missing: " + initFile.getAbsolutePath()).build(); 
		} else {
			return Response.ok(initFile, "text/x-shellscript")
						 .header("Content-Disposition", "attachment; filename=\"" + id + "-init.sh" + "\"" )
						 .build();
		}
	} 	 

	// TODO
	@POST @Consumes({"multipart/form-data"})
	// store a new install script
	public Response registerInstaller (
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token) {
		logRequest("POST", headers, request);
		if (Configuration.installerStorageToken != null && !Configuration.installerStorageToken.equals(token)) return Response.status(Status.BAD_REQUEST).entity("Missing authentication token").build();
//		String id = UUID.randomUUID().toString();
		// add id and created fields to metadata
		return Response.status(Status.BAD_REQUEST).entity("Not implemented").build();
//		return Response.status(Status.OK).entity(id).build();
	}

	// TODO
	@DELETE @Path("{id}") @Produces(MediaType.APPLICATION_JSON)
	public Response deleteInstaller (
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			@PathParam("id") String id) {
		logRequest("DELETE", headers, request);
		if (Configuration.installerStorageToken != null && !Configuration.installerStorageToken.equals(token)) return Response.status(Status.BAD_REQUEST).entity("Missing authentication token").build();
		if (!installerMetadata.containsKey(id)) return Response.status(Status.BAD_REQUEST).entity("Invalid installer id: " + id).build();
		return Response.status(Status.BAD_REQUEST).entity("Not implemented").build();
//		return Response.status(Status.OK).build();
	}	

	/*
	@POST @Path("{id}")	@Consumes({"text/x-shellscript"})
	// store a new install script
	public Response storeInstallScript(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			@PathParam("id") String id) {
		logRequest("POST", headers, request);		
		if (token == null || !Configuration.token.equals(token)) return Response.status(Status.BAD_REQUEST).entity("Missing authentication token").build();
//		if (!id.matches(ID_REGEXP)) return Response.status(Status.BAD_REQUEST).entity("Invalid id syntax").build();
		if (!new File (Configuration.storagePath).exists()) return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Storage path " + Configuration.storagePath + " does not exist").build(); 
		
		String dirName = Configuration.storagePath + "/" + id;
		File installerScriptFile = new File(dirName + "/" + INSTALLER_SCRIPT_NAME);
		log.debug("Installer script file: " + installerScriptFile.getAbsolutePath());
		if (!new File(dirName).exists()) {
			if (!new File (dirName).mkdirs()) 
				return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Cannot create fragment directory: " + dirName).build();
		} else {
			if (installerScriptFile.exists()) return Response.status(Status.BAD_REQUEST).entity("Installer already exists").build();
		}

		InputStream in = null;
        OutputStream out = null;
        try {
        	in = request.getInputStream();
        	out = new BufferedOutputStream(new FileOutputStream(installerScriptFile));
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
		return Response.status(Status.OK).build();
	} 
	
	@POST @Path("{id}/metadata") @Consumes(MediaType.APPLICATION_JSON)
	public Response buildVirtualImage(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			@PathParam("id") String id,
			String body) {
		logRequest("POST", headers, request);
//		if (!id.matches(Installer.ID_REGEXP)) return Response.status(Status.BAD_REQUEST).entity("Invalid id syntax").build();
		if (token == null || !Configuration.token.equals(token)) return Response.status(Status.BAD_REQUEST).entity("Missing authentication token").build();
		
		log.debug("Parsing JSON entity body...");
		JSONObject requestBody = null;
        if (body != null && body.length() > 0) {
    		try { requestBody = new JSONObject(new JSONTokener(body)); }
    		catch (JSONException e) { return Response.status(Status.BAD_REQUEST).entity("Invalid JSON content: " + e.getMessage()).build(); }
        } else { return Response.status(Status.BAD_REQUEST).entity("Missing entity body!").build(); }

        // verify required parameters
        if ("".equals(requestBody.optString(AUTHOR))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + AUTHOR).build(); 

        // check/create metadata dir
        String dirName = Configuration.storagePath + "/" + id;
		File fileName = new File(dirName + "/" + INSTALLER_METADATA_FILE_NAME);
		log.debug("Metadata file: " + Configuration.storagePath + "/" + id + "/" + INSTALLER_METADATA_FILE_NAME);
		if (!new File(dirName).exists()) {
			if (!new File (dirName).mkdirs()) 
				return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Cannot create directory: " + dirName).build();
		} else if (fileName.exists()) return Response.status(Status.BAD_REQUEST).entity("Metadata file already exists").build();
		
		// fill in automatically set data
		requestBody.put(ID, id);
		long now = System.currentTimeMillis();
		requestBody.put(CREATED, now);
		requestBody.put(MODIFIED, now);
	        
		// save metadata
		PrintWriter out = null;
		try {
			out = new PrintWriter(fileName);
			out.println(requestBody.toString());
		} 
		catch (IOException x) {
			log.error(x.getMessage(), x);
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Error while saving file: " + x.getMessage()).build(); 		
		}
		finally { if (out != null) out.close(); }
		
		// cache metadata
		Installers.installerMetadata.put(id, requestBody);
		
		return Response.status(Status.OK).build();
	}
	
		@POST
	@Path("{id}")
	@Consumes({"text/x-shellscript"})
	public Response storeIniyScript(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			@PathParam("id") String id) {
		logRequest("POST", headers, request);		
		if (token == null || !Configuration.token.equals(token)) return Response.status(Status.BAD_REQUEST).entity("Missing authentication token").build();
//		if (!id.matches(Installer.ID_REGEXP)) return Response.status(Status.BAD_REQUEST).entity("Invalid id syntax").build();
		if (!new File (Configuration.storagePath).exists()) return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Storage path " + Configuration.storagePath + " does not exist").build(); 
		
		String dirName = Configuration.storagePath + "/" + id;
		File fileName = new File(dirName + "/" + INSTALLER_INIT_FILE_NAME);
		log.debug("Init script file: " + fileName.getAbsolutePath());
		if (!new File(dirName).exists()) {
			if (!new File (dirName).mkdirs()) 
				return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Cannot create fragment directory: " + dirName).build();
		} else {
			if (fileName.exists()) return Response.status(Status.BAD_REQUEST).entity("Init script already exists").build();
		}
		
		InputStream in = null;
        OutputStream out = null;
        try {
        	in = request.getInputStream();
        	out = new BufferedOutputStream(new FileOutputStream(fileName));
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
		return Response.status(Status.OK).build();
	}
*/
}
