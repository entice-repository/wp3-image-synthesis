package hu.mta.sztaki.lpds.entice.virtualimagemanager.rest;

import javax.servlet.ServletContextListener;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.DBManager;
import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.Edge;
import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.Image;
import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.Image.ImageStatus;

import java.util.List;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.servlet.ServletContextEvent;

public class WebApplicationContextListener implements ServletContextListener {
	private static final Logger log = LoggerFactory.getLogger(VirtualImages.class); 

	private ScheduledExecutorService scheduler = null;
	
	@SuppressWarnings("unchecked")
	public void contextInitialized(ServletContextEvent sce) {
		log.info("Starting up Virtual Image Manager service...");
		List<Edge> resultList;
		long now = System.currentTimeMillis();
        try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
			log.debug("Listing virtual images with PENDING status...");
	        Query query = entityManager.createQuery("SELECT i FROM Edge as i WHERE i.status = :status");
	        query.setParameter("status", Edge.EdgeStatus.PENDING);
	        resultList = (List<Edge>) query.getResultList();
        	for (Edge i: resultList) {
	        	if (i.getCreated() + Edge.BUILD_TIMEOUT * 1000 < now) {
	        		VirtualImages.pendingFragmentComputations.put(i.getId(), i.getCreated());
	        		log.info("Adding PENDING fragment computation of edge: " + i.getId());
	        	} else {
	        		log.warn("Fragment computation on edge " + i.getId() + " is too old. Dropping.");
	        		i.setStatus(Edge.EdgeStatus.FAILED);
	        		i.getToImage().setStatus(ImageStatus.FAILED);
	        		i.getToImage().setMessage("Fragment computation timeout. (started: " + i.getCreated() + ")");
	        	}
	        }
			entityManager.getTransaction().commit();
			entityManager.close();
        } catch (Throwable x) {
        	log.error(x.getMessage());
        }
		if (VirtualImages.pendingFragmentComputations.size() == 0) log.info("No PENDING fragment computation");
		try {
			scheduler = Executors.newSingleThreadScheduledExecutor();
			scheduler.scheduleAtFixedRate(new TimedTask(), 0, 1 * 60, TimeUnit.SECONDS); // every min
		} catch (Exception e) {
			log.error("Cannot start scheduler: " + e.getMessage());
			e.printStackTrace();
		}
		DBManager.getInstance();
	}
	
	public void contextDestroyed(ServletContextEvent sce) {
		log.info("Shutting down Virtual Image Manager service...");
		if (scheduler != null) scheduler.shutdownNow();
	}
	
	class TimedTask extends TimerTask {
		//  check status of submitted fragment computations
		public void run() {
			try {
				if (VirtualImages.pendingFragmentComputations.size() == 0) { 
//					log.debug("No PENDING fragment computations"); 
					return; 
				}
				log.debug("Re-checking statuses of PENDING fragment computations...");
				List<String> completed = new Vector<String>();
				List<String> obsolete = new Vector<String>();
				long now = System.currentTimeMillis();
				// get obsolete and pending edge ids
				List <String> edgeIdsToQuery = new Vector<String>();
				for (String edgeId: VirtualImages.pendingFragmentComputations.keySet()) {
					if (VirtualImages.pendingFragmentComputations.get(edgeId) + Edge.BUILD_TIMEOUT * 1000 < now) {
						log.debug("Now: " + now + ", started: " + VirtualImages.pendingFragmentComputations.get(edgeId)  + ", timeout: " + Edge.BUILD_TIMEOUT * 1000);
						log.warn("Fragment computation on edge " + edgeId + " took too much time. Dropping.");
						obsolete.add(edgeId);
					} else {
						log.debug("Pending edge id: " + edgeId);
						edgeIdsToQuery.add(edgeId);
					}
				}
				// query pending edges
				List <Edge> pendingEdges = new Vector<Edge>();
				try {
					EntityManager entityManager = DBManager.getInstance().getEntityManager();
					entityManager.getTransaction().begin();
					for (String edgeId: edgeIdsToQuery) {
						Edge edge = entityManager.find(Edge.class, edgeId);
						if (edge == null) {
							log.warn("Edge id not found: " + edgeId);
							completed.add(edgeId);
						} else {
							pendingEdges.add(edge);
						}
					}
					entityManager.getTransaction().commit();
					entityManager.close();
		        } catch (Throwable x) {
		        	log.error(x.getMessage());
		        }
				// query fragment status
				for (Edge edge: pendingEdges) {
					String taskId = edge.getFragmentComputationTaskId();
					if (isFragmentDone(edge.getId(), taskId)) {
						log.info("Fragment computation task id " + taskId + " is done, edge id: " + edge.getId());
						completed.add(edge.getId());
					}
				}
				// remove completed
				synchronized (VirtualImages.pendingFragmentComputations) {
					for (String edgeId: completed) VirtualImages.pendingFragmentComputations.remove(edgeId);
					for (String edgeId: obsolete) VirtualImages.pendingFragmentComputations.remove(edgeId);
				}
				// set failed edges
		        if (obsolete.size() > 0) {
		        	try {
						EntityManager entityManager = DBManager.getInstance().getEntityManager();
						entityManager.getTransaction().begin();
						log.debug("Updating obsolete edges status...");
						for (String edgeId: obsolete)  {
							Edge e = entityManager.find(Edge.class,  edgeId);
							if (e != null) {
				        		e.setStatus(Edge.EdgeStatus.FAILED);
				        		e.getToImage().setStatus(ImageStatus.FAILED);
				        		e.getToImage().setMessage("Fragment computation timeout");
				        	}
						}
						entityManager.getTransaction().commit();
						entityManager.close();
		        	} catch (Throwable x) {	log.error(x.getMessage()); }
		        }
			} catch (Throwable t) { t.printStackTrace(); }  
		}
	}
	
	private boolean isFragmentDone(final String edgeId, final String fragmentComputationTaskId) {
		try {
			// create request
			Client client = Client.create();
			boolean done = false;
			try {
				String service = Configuration.virtualImageDecomposerRestURL + "/tasks/" + fragmentComputationTaskId;
				log.debug("Sending GET '" + service + "'");
				WebResource webResource = client.resource(service);
				ClientResponse response = webResource.get(ClientResponse.class);
				if (response.getStatus() != 200) {
					log.warn("virtual-image-decomposer " + service + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
					return false;
				} 
				// process response
				String responseString = response.getEntity(String.class);
				log.debug("Processing response (status 200): " + responseString);
				JSONObject responseJSON = null;
		    	try { responseJSON = new JSONObject(new JSONTokener(responseString)); }
		    	catch (JSONException e) { 
					log.warn("Invalid JSON: " + e.getMessage());
					return false;
		    	}
				String status = responseJSON.optString(Edge.STATUS);
				String message = responseJSON.optString(Image.MESSAGE);
				String fragmentUrl = responseJSON.optString(Edge.FRAGMENT_URL);
				long imageSize = responseJSON.optLong(Image.IMAGE_SIZE, 0l);
				long fragmentSize = responseJSON.optLong(Edge.FRAGMENT_SIZE, 0l);
				if ("".equals(status)) log.error("Missing key in status response:" + Edge.STATUS);
				
				// store result
		        try {
					EntityManager entityManager = DBManager.getInstance().getEntityManager();
					entityManager.getTransaction().begin();
					Edge edge = entityManager.find(Edge.class, edgeId);
					if (edge != null) {
						if ("done".equalsIgnoreCase(status)) {
							edge.setStatus(Edge.EdgeStatus.READY);
							edge.getToImage().setStatus(Image.ImageStatus.READY);
							edge.getToImage().setImageSize(imageSize);
							if (!"".equals(fragmentUrl))	{
								log.debug("Fragment URL: " + fragmentUrl);
								edge.setFragmentUrl(fragmentUrl);
							} else log.error("Missing key in status reponse: " + Edge.FRAGMENT_URL);
							edge.setFragmentSize(fragmentSize);
							done = true;
						} else if ("failed".equalsIgnoreCase(status)) {
							edge.setStatus(Edge.EdgeStatus.FAILED);
							edge.getToImage().setStatus(Image.ImageStatus.FAILED);
							done = true;
						} else if ("pending".equalsIgnoreCase(status)) {
							edge.setStatus(Edge.EdgeStatus.PENDING);
						} else {
							log.warn("Invalid status: " + status);
							edge.getToImage().setMessage("Invalid fragment computation status: " + status);
						}
					} else log.warn("Edge with id " + edgeId + " not found");
					if (!"".equals(message)) edge.getToImage().setMessage(message);
					entityManager.getTransaction().commit();
					entityManager.close();
		        } catch (Throwable x) {
		        	log.error("database exception: " + x.getMessage());
		        	x.printStackTrace();
		        }
		        
		        if (done) {
					log.debug("Sending DELETE '" + service + "'");
		        	response = webResource.header(CustomHTTPHeaders.HTTP_HEADER_TOKEN, Configuration.virtualImageDecomposerToken).delete(ClientResponse.class);
					if (response.getStatus() != 200) {
						log.error("Cannot delete task: " + fragmentComputationTaskId + ". Virtual Image Decomposer returned HTTP status code: " + response.getStatus() + ", with content: " + response.getEntity(String.class));
					} 
		        }
			} finally { client.destroy(); }
	        return done;
		} catch (Throwable t) { t.printStackTrace(); return false; }
	}	
}