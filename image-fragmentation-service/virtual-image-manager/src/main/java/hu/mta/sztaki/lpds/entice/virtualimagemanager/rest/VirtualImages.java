package hu.mta.sztaki.lpds.entice.virtualimagemanager.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import javax.persistence.EntityManager;
import javax.persistence.Query;
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

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.WebResource;

import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.DBManager;
import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.Edge;
import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.Edge.EdgeStatus;
import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.Image;
import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.Image.ImageStatus;
import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.Image.ImageType;

@Path("/virtualimages") 
public class VirtualImages {
	private static final Logger log = LoggerFactory.getLogger(VirtualImages.class); 
	private void logRequest(final String method, final HttpHeaders httpHeaders, final HttpServletRequest request) {
		log.info("" + method /* + ", from: " + request.getRemoteAddr() */);
	}

	private final static String KNOWLEDGE_BASE_REF = "knowledgeBaseRef";
	private final static String PARENT_VMI_ID = "parentVMIId";
	private final static String DEBUG = "debug"; // for devops only

	static Map<String, Long> pendingFragmentComputations = new ConcurrentHashMap<String, Long>();
	
	@POST @Consumes(MediaType.APPLICATION_JSON)	
	public Response createVirtualImage(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			String body) {
		logRequest("POST", headers, request);
		if (Configuration.virtualImageManagerToken != null && !Configuration.virtualImageManagerToken.equals(token)) return Response.status(Status.BAD_REQUEST).entity("Missing authentication token").build();
		log.debug(body);
		
		// parse input
		log.debug("Parsing JSON entity body...");
 	   	JSONObject requestBody = null;
        if (body != null && body.length() > 0) {
        	try { requestBody = new JSONObject(new JSONTokener(body)); }
    		catch (JSONException e) { return Response.status(Status.BAD_REQUEST).entity("Invalid JSON content: " + e.getMessage()).build(); }
        } else { return Response.status(Status.BAD_REQUEST).entity("Missing entity body!").build(); }
        
        // verify required parameters
        if ("".equals(requestBody.optString(Image.OWNER))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + Image.OWNER).build(); 
        if ("".equals(requestBody.optString(Image.PARENT_VIRTUAL_IMAGE_ID))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + Image.PARENT_VIRTUAL_IMAGE_ID).build(); 

        // if not snapshot installer ids ir installerBase64 is required
        
        if ((requestBody.optJSONArray(Edge.INSTALLER_IDS) == null || requestBody.getJSONArray(Edge.INSTALLER_IDS).length() == 0) &&  "".equals(requestBody.optString(Edge.INSTALLER_BASE64)) && "".equals(requestBody.optString(Edge.SNAPSHOT_URL))) {
        	return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + Edge.INSTALLER_IDS + "/" + Edge.INSTALLER_BASE64 + "/" + Edge.SNAPSHOT_URL).build(); 
        }
        
    	String knowledgeBaseRef = requestBody.optString(KNOWLEDGE_BASE_REF);
    	if ("".equals(knowledgeBaseRef)) log.warn("Missing parameter: " + KNOWLEDGE_BASE_REF);
        
    	boolean debug = requestBody.optBoolean(DEBUG);
    	
        // check parent image
        Image parentImage;
        try {
    		EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
			log.debug("Searching for parent image: " + requestBody.optString(Image.PARENT_VIRTUAL_IMAGE_ID) + "...");
			// TODO let decomposer to find parent image id on snapshot
			parentImage = entityManager.find(Image.class, requestBody.optString(Image.PARENT_VIRTUAL_IMAGE_ID));
			entityManager.getTransaction().commit();
			entityManager.close();
        } catch (Throwable x) {
        	log.error(x.getMessage());
        	return Response.status(Status.INTERNAL_SERVER_ERROR).entity(x.getMessage()).build();
        }
        if (parentImage == null) return Response.status(Status.BAD_REQUEST).entity("Parent image with id: " + requestBody.optString(Image.PARENT_VIRTUAL_IMAGE_ID) + " not found").build();
        if (parentImage.getStatus() != Image.ImageStatus.READY) return Response.status(Status.BAD_REQUEST).entity("Wrong parent image status: " + parentImage.getStatus() + "").build();
        log.debug("Parent image found");
        
        // query edge implied tags (if installers are used)
        List <String> edgeTags;
		try { 
			// TODO forward Response
			edgeTags = queryInstallerImpliedTags(requestBody.optJSONArray(Edge.INSTALLER_IDS));  
		} catch (Exception x) { 
			log.error(x.getMessage());
			return Response.status(Status.BAD_REQUEST).entity(x.getMessage()).build(); 
		}

		// create virtual image
	    Image virtualImage = new Image();

        // initiate fragment calculation
        Edge edge = new Edge();
        edge.setStatus(EdgeStatus.PENDING);
        String fragmentCalculationId;
		try { fragmentCalculationId = initiateFragmentCalculation(requestBody, virtualImage, parentImage, edge, requestBody.optString(Edge.INSTALLER_BASE64), requestBody.optString(Edge.INIT_BASE64), knowledgeBaseRef, debug); }  // TODO
		catch (Exception x) { 
			log.error(x.getMessage());
			return Response.status(Status.BAD_REQUEST).entity(x.getMessage()).build(); 
		}
		
		// connect new virtual image with parent image, set edge attributes, and persist
        try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
			log.debug("Creating virtual image...");
			// set basic attributes
			virtualImage.setType(ImageType.VIRTUAL);
			virtualImage.setStatus(ImageStatus.PENDING);
			virtualImage.setName(requestBody.optString(Image.NAME));
			virtualImage.setOwner(requestBody.optString(Image.OWNER));
			virtualImage.setDescription(requestBody.optString(Image.DESCRIPTION));
			virtualImage.setImageSize(0l);

			virtualImage.setMessage("building...");
			// process image tags
			JSONArray tags = requestBody.optJSONArray(Image.TAGS);
			if (tags != null) {
				for (int i = 0; i < tags.length(); i++) {
					String tag = tags.optString(i);
					if (!"".equals(tag)) {
						virtualImage.addTag(tag);
					} else log.warn("Ignoring non-string tag...");
				}
			}
			// store installer ids
			if (requestBody.optJSONArray(Edge.INSTALLER_IDS) != null && requestBody.getJSONArray(Edge.INSTALLER_IDS).length() > 0) {
				JSONArray installerIds = requestBody.getJSONArray(Edge.INSTALLER_IDS);
				for (int i = 0; i < installerIds.length(); i++) {
					String installerId = installerIds.optString(i);
					if (!"".equals(installerId)) edge.addInstallerId(installerId);
				}
			}
			// set fragment calculation id
			edge.setFragmentComputationTaskId(fragmentCalculationId);
			// store snapshot URL
			edge.setSnapshotUrl(requestBody.optString(Edge.SNAPSHOT_URL));
			// store edge tags	
			edge.getTags().addAll(edgeTags);
			// add to image tags
			virtualImage.getTags().addAll(edgeTags);
			// persist edge
			entityManager.persist(edge);
			// persist image
			entityManager.persist(virtualImage);
			// fill in edge details
			edge.setFromImage(parentImage);
			edge.setToImage(virtualImage);
			// store the edge in both images
			parentImage.addOutgoingEdge(edge);
			virtualImage.addIncomingEdge(edge);
			// commit
			entityManager.getTransaction().commit();
			entityManager.close();
			log.debug("Virtual image created: " + virtualImage.getId());
        } catch (Throwable x) {
        	log.error(x.getMessage(), x);
        	return Response.status(Status.INTERNAL_SERVER_ERROR).entity(x.getMessage()).build();
        }
        // store pending fragment calculation
        pendingFragmentComputations.put(edge.getId(), edge.getCreated());
        // return response
        return Response.status(Status.OK).entity(virtualImage.getId()).build();
	}
	
	@SuppressWarnings("unchecked") 
	@GET @Produces(MediaType.APPLICATION_JSON)
	public Response getAllVirtualImageMetadata(
		@Context HttpHeaders headers,
		@Context HttpServletRequest request) {
		logRequest("GET ALL", headers, request);
		// get all virtual images
		List<Image> resultList;
        try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
			log.debug("Listing all virtual images...");
//	        Query query = entityManager.createQuery("SELECT i FROM Image as i WHERE i.type = :type");
//	        query.setParameter("type", ImageType.VIRTUAL);
			Query query = entityManager.createQuery("SELECT i FROM Image as i");
	        resultList = query.getResultList();
			entityManager.getTransaction().commit();
			entityManager.close();
        } catch (Throwable x) {
        	log.error(x.getMessage());
        	return Response.status(Status.INTERNAL_SERVER_ERROR).entity(x.getMessage()).build();
        }
        // create response
		JSONArray response = new JSONArray();
        for (Image image: (List<Image>) resultList)	response.put(getAggregatedVirtualImageMetadata(image));
        return Response.status(Status.OK).entity(response.toString()).build();
	}
	
	@GET @Path("{id}") @Produces(MediaType.APPLICATION_JSON)
	public Response getVirtualImageMetadata(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@PathParam("id") String id) {
		logRequest("GET " + id, headers, request);
		Image image;
		try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
			image = entityManager.find(Image.class, id);
			entityManager.getTransaction().commit();
			entityManager.close();
        } catch (Throwable x) {
        	log.error(x.getMessage());
        	return Response.status(Status.INTERNAL_SERVER_ERROR).entity(x.getMessage()).build();
        }
		if (image == null) return Response.status(Status.BAD_REQUEST).entity("Image id not found: " + id).build();
		log.debug("Virtual image found: " + id);
		return Response.status(Status.OK).entity(getAggregatedVirtualImageMetadata(image).toString()).build();
	}

	@DELETE	@Path("{id}")
	public Response deleteVirtualImage(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_OWNER) String user,
			@PathParam("id") String id) {
		logRequest("DELETE " + id, headers, request);
		if (Configuration.virtualImageManagerToken != null && !Configuration.virtualImageManagerToken.equals(token)) return Response.status(Status.BAD_REQUEST).entity("Missing authentication token").build();
		log.debug("Searching for virtual image: " + id + "...");
		Image virtualImage;
        try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
			virtualImage = entityManager.find(Image.class, id);
			entityManager.getTransaction().commit();
			entityManager.close();
        } catch (Throwable x) {
        	log.error(x.getMessage());
        	return Response.status(Status.INTERNAL_SERVER_ERROR).entity(x.getMessage()).build();
        }
		if (virtualImage == null) return Response.status(Status.BAD_REQUEST).entity("Invalid virtual image id: " + id).build();
		log.debug("Virtual image found");
		if (!"admin".equals(user) && !virtualImage.getOwner().equals(user))  return Response.status(Status.BAD_REQUEST).entity("Header field " + CustomHTTPHeaders.HTTP_HEADER_OWNER + " differs from owner: " + user).build();
		if (virtualImage.getOutgoingEdges().size() > 0) return Response.status(Status.BAD_REQUEST).entity("Cannot delete a virtual image having children (" + virtualImage.getOutgoingEdges().get(0).getToImage().getId() + ")").build();
		if (virtualImage.getStatus() == ImageStatus.PENDING) return Response.status(Status.BAD_REQUEST).entity("Cannot delete a virtual image under build (" + virtualImage.getStatus() + ")").build();
		
        // do the deletion
		try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
			log.debug("Deleting virtual image...");
			virtualImage = entityManager.find(Image.class, id);
			List<Edge> incomingEdges = virtualImage.getIncomingEdges();
			if (incomingEdges != null && incomingEdges.size() > 0) {
				for (Edge edge: incomingEdges) {
					Image parent = edge.getFromImage();
					parent.getOutgoingEdges().remove(edge);
					entityManager.remove(edge);
					// TODO abort fragment computation is pending
					pendingFragmentComputations.remove(edge.getId());
					// try to remove fragment file
					if (Configuration.fragmentStorageURL != null && Configuration.fragmentStorageToken != null) {
						String fragmentId = edge.getFragmentComputationTaskId();
						Client client = Client.create();
						try {
							String service = Configuration.fragmentStorageURL + "/" + fragmentId;
							log.debug("Sending DELETE to '" + service + "'");
							WebResource webResource = client.resource(service);
							ClientResponse response = webResource.header(CustomHTTPHeaders.HTTP_HEADER_TOKEN, Configuration.fragmentStorageToken).delete(ClientResponse.class);
							if (response.getStatus() != 200) log.warn("fragmentStorage " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
							else log.debug("Fragment file deleted");
						} 
						catch (Exception ex) { log.warn("Cannot delete fragment: " + fragmentId);	}
						finally { client.destroy(); }
					} else {
						log.debug("Fragment file will not be deleted (no fragmentStorageURL or fragmentStorageToken)");
					}
				}
				virtualImage.getIncomingEdges().clear();
			}
			entityManager.remove(virtualImage);
			entityManager.getTransaction().commit();
			entityManager.close();
			log.debug("Virtual image deleted");
        } catch (Throwable x) {
        	log.error(x.getMessage());
        	return Response.status(Status.INTERNAL_SERVER_ERROR).entity(x.getMessage()).build();
        }
		return Response.status(Status.OK).build();
	}

	// used by VirtualImageComposer to build merge scripts
	@GET @Path("{id}/fragments") @Produces(MediaType.APPLICATION_JSON)
	public Response getFragmentUrlsAsJSONArray(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			@PathParam("id") String id) {
		logRequest("GET", headers, request);
		
		log.debug("Looking up virtual image with id: " + id + "...");
		Image virtualImage;
		try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
			virtualImage = entityManager.find(Image.class, id);
			entityManager.getTransaction().commit();
			entityManager.close();
        } catch (Throwable x) {
        	log.error(x.getMessage());
        	return Response.status(Status.INTERNAL_SERVER_ERROR).entity(x.getMessage()).build();
        }
		// check result
		if (virtualImage == null) return Response.status(Status.BAD_REQUEST).entity("Virtual image " + id + " not found").build();
		if (virtualImage.getStatus() != Image.ImageStatus.READY) return Response.status(Status.BAD_REQUEST).entity("Wrong image status: " + virtualImage.getStatus()).build();
		// create response
		log.debug("Building response JSON");
		List<String> fragmentIds = getFragmentUrls(virtualImage, new ArrayList<String> ());
		log.debug("" + fragmentIds.size() + " fragment(s) found");
		JSONArray response = new JSONArray(fragmentIds);
		return Response.status(Status.OK).entity(response.toString()).build();
	}
	
	// used by Virtual Image Launcher to get proprietary image id
	@GET @Path("{id}/cloudimageids/{cloud}") @Produces(MediaType.TEXT_PLAIN)
	public Response getProprietaryImageId(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@PathParam("id") String id,
			@PathParam("cloud") String cloud) {
		logRequest("GET", headers, request);
		
		log.debug("Looking up virtual image with id: " + id + "...");
		Image virtualImage;
		try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
			virtualImage = entityManager.find(Image.class, id);
			entityManager.getTransaction().commit();
			entityManager.close();
        } catch (Throwable x) {
        	log.error(x.getMessage());
        	return Response.status(Status.INTERNAL_SERVER_ERROR).entity(x.getMessage()).build();
        }
		if (virtualImage == null) return Response.status(Status.BAD_REQUEST).entity("Virtual image id not found: " + id).build();
		String proprietaryImageId = BaseImages.getCloudImageId(virtualImage, cloud);
		if (proprietaryImageId == null) return Response.status(Status.BAD_REQUEST).entity("No proprietary image id found for virtual image " + id + " in cloud " + cloud).build();
		else return Response.status(Status.OK).entity(proprietaryImageId).build();
	}

	private void setCommonMetadata(Image image, JSONObject json) {
		json.put(Image.ID, image.getId());
		json.put(Image.NAME, image.getName());
		json.put(Image.TYPE, image.getType());
		json.put(Image.STATUS, image.getStatus());
		json.put(Image.MESSAGE, image.getMessage());
		json.put(Image.CREATED, image.getCreated());
		json.put(Image.OWNER, image.getOwner());
		json.put(Image.DESCRIPTION, image.getDescription());
		json.put(Image.IMAGE_SIZE, image.getImageSize());
	}
	
	private JSONObject getAggregatedVirtualImageMetadata(final Image image) {
		JSONObject json = new JSONObject();
		setCommonMetadata(image, json);
		JSONArray tags = new JSONArray();
		for (String tag: aggregateTags(image, new ArrayList<String>())) tags.put(tag);
		json.put(Image.TAGS, tags);
		return json;
	}
	
	// get tags in reversed order: first highest level tags, last base image tags
	public static List<String> aggregateTags(final Image image, List <String> tags) {
		// add current image tags
		tags.addAll(image.getTags());
		if (!image.getIncomingEdges().isEmpty()) {
			Edge edge = image.getIncomingEdges().get(0); // assumes a single parent
			// add incoming edge tags NOTE: edge tags are added to image tags
			// tags.addAll(edge.getTags());
			// recursively descend to predecessor(s)
			aggregateTags(edge.getFromImage(), tags);
		}
		return tags;
	}
	
	private String initiateFragmentCalculation(JSONObject requestBody, final Image target, final Image parent, final Edge edge, final String installerBase64, final String initBase64, final String knowledgeBaseRef, final boolean debug) throws Exception {
		Client client = Client.create();
		String service = Configuration.virtualImageDecomposerRestURL + "/tasks";
		try {
			WebResource webResource = client.resource(service);
			// prepare request
			JSONObject request = new JSONObject();
			// TODO can we detect parent image from snapshot?
			request.put(Image.SOURCE_BASE_IMAGE_URL, BaseImages.getBaseImageUrl(parent));
			request.put(Image.PARTITION, BaseImages.getBaseImagePartition(parent));
			request.put(Image.SOURCE_VIRTUAL_IMAGE_ID, parent.getId());
			request.put(Image.TARGET_VIRTUAL_IMAGE_ID, target.getId());
			
			if (!"".equals(installerBase64)) request.put(Edge.INSTALLER_BASE64, installerBase64);
			if (!"".equals(initBase64)) request.put(Edge.INIT_BASE64, initBase64);
			
			if (requestBody.optJSONArray(Edge.INSTALLER_IDS) != null) request.put(Edge.INSTALLER_IDS, requestBody.getJSONArray(Edge.INSTALLER_IDS));
			if (!"".equals(requestBody.optString(Edge.SNAPSHOT_URL))) request.put(Edge.SNAPSHOT_URL, requestBody.optString(Edge.SNAPSHOT_URL));
			if (!"".equals(knowledgeBaseRef)) request.put(KNOWLEDGE_BASE_REF, knowledgeBaseRef);
			request.put(PARENT_VMI_ID, parent.getId());
			if (debug) request.put(DEBUG, Boolean.TRUE);
			
			// send request
			log.debug("virtual-image-decomposer URL: " + service);
			log.debug("This is to POST to virtual-image-decomposer: " + request.toString());
			ClientResponse response = webResource
					.header(CustomHTTPHeaders.HTTP_HEADER_TOKEN, Configuration.virtualImageDecomposerToken)
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class, request.toString());
			if (response.getStatus() != 200) throw new Exception("Cannot POST to virtual image decomposer. Service " + service + " returned HTTP error code: " + response.getStatus() + " (" + response.getEntity(String.class) + ")");
			String fragmentComputationTaskId = response.getEntity(String.class).trim();
			log.info("Fragment computation task id: '" + fragmentComputationTaskId + "'");
			return fragmentComputationTaskId;
		} catch (Exception x) { throw new Exception("Cannot POST to virtual image decomposer. Exception: " + x.getMessage()); }
		finally { client.destroy(); }
	}
	
	private List<String> queryInstallerImpliedTags(JSONArray installerIds) throws Exception {
		List<String> result = new Vector<String>();
		if (installerIds != null) {
			log.debug("Getting installer tags from Installer storage...");
			Client client = Client.create();
			try {
				for (int i = 0; i < installerIds.length(); i++) {
					String installerId = installerIds.optString(i);
					if ("".equals(installerId)) throw new Exception("INSTALLER ERROR: Invalid id: " + installerId + "");
					String service = Configuration.installerStorageURL + "/installers/" + installerId; // metadata
					// create request
					log.debug("Getting tags from : " + service);
					WebResource webResource = client.resource(service);
					ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
					if (response.getStatus() != 200) throw new Exception("INSTALLER ERROR: Cannot GET tags for installer: " + installerId + ". Service " + service + " returned HTTP error code: " + response.getStatus() + ".");
					// process response
					JSONObject jsonResponse;
					try { jsonResponse = new JSONObject(new JSONTokener(response.getEntity(String.class))); }
		    		catch (JSONException e) { throw new Exception("INSTALLER ERROR: Invalid JSON from " + service + ". Exception: " + e.getMessage() + ""); }
					// create list of tags
					JSONArray tagsJson = jsonResponse.optJSONArray(Edge.TAGS);
					log.debug("Installer tags: " + tagsJson.toString());
					if (tagsJson != null) {
						for (int j = 0; j < tagsJson.length(); j++) {
							String tag = tagsJson.optString(j);
							if (!"".equals(tag)) result.add(tag);
						}
					}
				}
			} finally {	client.destroy(); }
		}
		return result;
	}
	
	// get list of fragment URLs starting from the lowest (just right after the base image) to the highest (producing the final virtual image)
	private List<String> getFragmentUrls(final Image image, List<String> fragmentUrls) {
		if (image.getType() == Image.ImageType.VIRTUAL) {
			Edge incoming = image.getIncomingEdges().get(0); // assumes a single parent
			getFragmentUrls(incoming.getFromImage(), fragmentUrls);
			fragmentUrls.add(incoming.getFragmentUrl());
		}
		return fragmentUrls;
	}	
}