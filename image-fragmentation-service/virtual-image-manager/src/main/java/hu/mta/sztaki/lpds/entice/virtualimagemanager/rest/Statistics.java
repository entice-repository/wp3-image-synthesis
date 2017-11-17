package hu.mta.sztaki.lpds.entice.virtualimagemanager.rest;

import java.util.List;
import java.util.Vector;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.ClientResponse.Status;

import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.DBManager;
import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.Edge;
import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.Image;

@Path("/statistics") 
public class Statistics {
	private static final Logger log = LoggerFactory.getLogger(BaseImages.class); 
	private void logRequest(final String method, final HttpHeaders httpHeaders, final HttpServletRequest request) {
		log.info("" + method);
	}

	@SuppressWarnings("unchecked")
	@GET @Produces(MediaType.APPLICATION_JSON)
	public Response getBaseImageMetadata (
			@Context HttpHeaders headers,
			@Context HttpServletRequest request) {
		logRequest("GET ", headers, request);
		
		int numberOfBaseImages = 0;
		int numberOfVirtualImages = 0;
		int numberOfFragments = 0;
		long sizeOfBaseImages = 0l;
		long sizeOfVirtualImages = 0l;
		long sizeOfFragments = 0l;

		List<Image> baseList = new Vector<Image>();
        try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
	        Query query = entityManager.createQuery("SELECT i FROM Image as i WHERE i.type = :type");
	        query.setParameter("type", Image.ImageType.BASE);
	        baseList = (List<Image>) query.getResultList();
			entityManager.getTransaction().commit();
			entityManager.close();
        } catch (Throwable x) {}		
        for (Image image: baseList) {
        	numberOfBaseImages++;
        	if (image.getImageSize() != null) sizeOfBaseImages += image.getImageSize();
        }
		
        List <Image> virtualList = new Vector<Image>();
        try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
	        Query query = entityManager.createQuery("SELECT i FROM Image as i WHERE i.type = :type");
	        query.setParameter("type", Image.ImageType.VIRTUAL);
	        virtualList = (List<Image>) query.getResultList();
			entityManager.getTransaction().commit();
			entityManager.close();
        } catch (Throwable x) {}
        for (Image image: virtualList) {
        	numberOfVirtualImages++;
        	if (image.getImageSize() != null) sizeOfVirtualImages += image.getImageSize();
        }

    	List <Edge> edgeList = new Vector<Edge>();
        try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
	        Query query = entityManager.createQuery("SELECT i FROM Edge as i");
	        edgeList = (List<Edge>) query.getResultList();
			entityManager.getTransaction().commit();
			entityManager.close();
        } catch (Throwable x) {}
        for (Edge edge: edgeList) {
        	numberOfFragments++;
        	if (edge.getFragmentSize() != null) sizeOfFragments += edge.getFragmentSize();
        }
       // create response
		log.debug("Creating response...");
		JSONObject result = new JSONObject();
		
		result.put("numberOfBaseImages", numberOfBaseImages);
		result.put("numberOfVirtualImages", numberOfVirtualImages);
		result.put("numberOfFragments", numberOfFragments);
		result.put("sizeOfBaseImages", sizeOfBaseImages);
		result.put("sizeOfVirtualImages", sizeOfVirtualImages);
		result.put("sizeOfFragments", sizeOfFragments);
		
		result.put("reductionRatioPercent", 0f);
		if (sizeOfBaseImages!=0l) {
			result.put("reductionRatioPercent", 100 - 100*((double)sizeOfBaseImages + (double)sizeOfFragments)/((double)sizeOfBaseImages + (double)sizeOfVirtualImages));
		}
				
		return Response.status(Status.OK).entity(result.toString()).build();
	}
}