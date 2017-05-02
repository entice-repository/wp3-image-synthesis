package hu.mta.sztaki.lpds.entice.virtualimagemanager.rest;

import javax.persistence.EntityManager;
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

import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.DBManager;
import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.Image;
import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.Image.ImageStatus;
import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.Image.ImageType;

/*
TESTS:
#!/bin/bash
curl -X POST -H "token: entice" -H "Content-Type: application/json" -d '{author:admin,url:"http://host"}' http://localhost:8080/virtual-image-manager/rest/baseimages/ > baseImageId || exit 1
	cat baseImageId
curl http://localhost:8080/virtual-image-manager/rest/baseimages/$(cat baseImageId) || exit 1
	echo
curl -X DELETE -H "token: entice" -H "user: admin" http://localhost:8080/virtual-image-manager/rest/baseimages/$(cat baseImageId) || exit 1
	echo Base image deleted
*/

@Path("/baseimages") 
public class BaseImages {
	private static final Logger log = LoggerFactory.getLogger(BaseImages.class); 
	private void logRequest(final String method, final HttpHeaders httpHeaders, final HttpServletRequest request) {
		log.info("" + method);
	}

	@POST @Consumes(MediaType.APPLICATION_JSON)	@Produces(MediaType.TEXT_PLAIN)
	public Response registerBaseImage(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			String body) {
		logRequest("POST", headers, request);
		if (Configuration.virtualImageManagerToken != null && !Configuration.virtualImageManagerToken.equals(token)) return Response.status(Status.BAD_REQUEST).entity("Missing authentication token").build();
		// parse input
		log.debug("Parsing JSON request body...");
		JSONObject requestBody = null;
        if (body != null && body.length() > 0) {
    		try { requestBody = new JSONObject(new JSONTokener(body)); }
    		catch (JSONException e) { return Response.status(Status.BAD_REQUEST).entity("Invalid JSON content: " + e.getMessage()).build(); }
        } else { return Response.status(Status.BAD_REQUEST).entity("Missing entity body!").build(); }
        // check required parameters
        if ("".equals(requestBody.optString(Image.OWNER))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + Image.OWNER).build(); 
        if ("".equals(requestBody.optString(Image.URL))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + Image.URL).build(); 
        // create response
        Image baseImage;
        try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
			log.debug("Creating base image...");
			baseImage = new Image();
			// process base attributes
			baseImage.setType(ImageType.BASE);
			baseImage.setStatus(ImageStatus.READY);
			baseImage.setName(requestBody.optString(Image.NAME));
			baseImage.setOwner(requestBody.optString(Image.OWNER));
			baseImage.setDescription(requestBody.optString(Image.DESCRIPTION));
			baseImage.setUrl(requestBody.optString(Image.URL));
			baseImage.setDiskPartition(requestBody.optString(Image.PARTITION));
			// process tags
			JSONArray tags = requestBody.optJSONArray(Image.TAGS);
			if (tags != null) {
				for (int i = 0; i < tags.length(); i++) {
					String tag = tags.optString(i);
					if (!"".equals(tag)) baseImage.addTag(tag);
					else log.warn("Ignoring non-string tag: " + tag.toString());
				}
			} else log.warn("No tags provided");
			// process proprietary image ids
			JSONObject imageIds = requestBody.optJSONObject(Image.CLOUD_IMAGE_IDS);
			if (imageIds != null) {
				for (Object cloud: imageIds.keySet()) {
					if (!(cloud instanceof String) || "".equals(imageIds.optString((String)cloud)))	log.warn("Ignoring image id: " + cloud.toString() + " (non-string key or missing string value)");
					else baseImage.addImageId((String) cloud, imageIds.optString((String)cloud));
				}
			} else log.warn("No image ids provided");
			// store in database
			entityManager.persist(baseImage);
			entityManager.getTransaction().commit();
			entityManager.close();
			log.debug("Base image stored: " + baseImage.getId() + " (" + baseImage.getUrl() + ")");
        } catch (Throwable x) {
        	log.error(x.getMessage());
        	return Response.status(Status.INTERNAL_SERVER_ERROR).entity(x.getMessage()).build();
        }
		return Response.status(Status.OK).entity(baseImage.getId() + "\n").build();
	}

	@GET @Path("{id}") @Produces(MediaType.APPLICATION_JSON)
	public Response getBaseImageMetadata (
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@PathParam("id") String id) {
		logRequest("GET " + id, headers, request);
		log.debug("Searching for base image: " + id + "");
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
		if (image.getType() != ImageType.BASE)  return Response.status(Status.BAD_REQUEST).entity("Not a base image").build();
		log.debug("Base image found");
		// create response
		log.debug("Creating response...");
		JSONObject result = getBaseImageMetadata(image);
		return Response.status(Status.OK).entity(result.toString()).build();
	}

	@DELETE	@Path("{id}")
	public Response deleteBaseImage (
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_TOKEN) String token,
			@HeaderParam(CustomHTTPHeaders.HTTP_HEADER_OWNER) String user,
			@PathParam("id") String id) {
		logRequest("DELETE " + id, headers, request);
		if (Configuration.virtualImageManagerToken != null && !Configuration.virtualImageManagerToken.equals(token)) return Response.status(Status.BAD_REQUEST).entity("Missing authentication token").build();
		log.debug("Searching for base image with id: " + id + "");
		Image baseImage;
        try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
			baseImage = entityManager.find(Image.class, id);
			entityManager.getTransaction().commit();
			entityManager.close();
        } catch (Throwable x) {
        	log.error(x.getMessage());
        	return Response.status(Status.INTERNAL_SERVER_ERROR).entity(x.getMessage()).build();
        }
		if (baseImage == null) return Response.status(Status.BAD_REQUEST).entity("Invalid base image id: " + id).build();
		log.debug("Base image found");
		if (!"admin".equals(user) && !baseImage.getOwner().equals(user))  return Response.status(Status.BAD_REQUEST).entity("Header field user differs from author: " + user).build();
		if (baseImage.getOutgoingEdges().size() > 0) return Response.status(Status.BAD_REQUEST).entity("Base image has child virtual image(s)").build();
        try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
			log.debug("Deleting base image...");
			baseImage = entityManager.find(Image.class, id);
			entityManager.remove(baseImage);
			entityManager.getTransaction().commit();
			entityManager.close();
			log.debug("Base image deleted");
        } catch (Throwable x) {
        	log.error(x.getMessage());
        	return Response.status(Status.INTERNAL_SERVER_ERROR).entity(x.getMessage()).build();
        }
		return Response.status(Status.OK).build();
	}
	
	private JSONObject getBaseImageMetadata(Image image) {
		JSONObject json = new JSONObject();
		json.put(Image.ID, image.getId());
		json.put(Image.NAME, image.getName());
		json.put(Image.URL, image.getUrl());
		json.put(Image.PARTITION, image.getDiskPartition());
		json.put(Image.STATUS, image.getStatus());
		json.put(Image.CREATED, image.getCreated());
		json.put(Image.OWNER, image.getOwner());
		json.put(Image.DESCRIPTION, image.getDescription());
		JSONArray tags = new JSONArray();
		for (String tag: image.getTags()) tags.put(tag);
		json.put(Image.TAGS, tags);
		JSONObject imageIds = new JSONObject();
		for (String cloud: image.getImageIds().keySet()) imageIds.put(cloud, image.getImageIds().get(cloud));
		json.put(Image.CLOUD_IMAGE_IDS, imageIds);
		return json;
	}
	
	static String getBaseImageUrl(Image image) {
		if (image.getType() == Image.ImageType.BASE) return image.getUrl();
		else return getBaseImageUrl(image.getIncomingEdges().get(0).getFromImage());
	}
	
	static String getBaseImagePartition(Image image) {
		if (image.getType() == Image.ImageType.BASE) return image.getDiskPartition();
		else return getBaseImagePartition(image.getIncomingEdges().get(0).getFromImage());
	}
	
	static String getCloudImageId(final Image image, final String cloud) {
		if (image.getType() == Image.ImageType.BASE) return image.getImageIds().get(cloud);
		else return getCloudImageId(image.getIncomingEdges().get(0).getFromImage(), cloud);
	}
}