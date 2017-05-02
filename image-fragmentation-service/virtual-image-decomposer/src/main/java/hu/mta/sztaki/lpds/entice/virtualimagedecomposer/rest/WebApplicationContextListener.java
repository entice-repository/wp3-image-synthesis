package hu.mta.sztaki.lpds.entice.virtualimagedecomposer.rest;

import javax.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import javax.servlet.ServletContextEvent;

public class WebApplicationContextListener implements ServletContextListener {
	private static final Logger log = LoggerFactory.getLogger(VirtualImageDecomposer.class); 

	public void contextInitialized(ServletContextEvent sce) {
		log.info("Starting up Virtual Image Decomposer service...");
		if (new File(Configuration.virtualImageDecomposerPath).exists()) {
			String [] ids = new File(Configuration.virtualImageDecomposerPath).list();
			if (ids != null && ids.length > 0) { 
				for (String id: ids) {
					String workingDir = Configuration.virtualImageDecomposerPath + "/" + id;
					log.debug("Reading working dir: " + workingDir + "...");
					
					// resume interrupted fragment calculations
					File inputs = new File(workingDir + "/" + VirtualImageDecomposer.INPUTS_FILE);
					File done = new File(workingDir + "/" + VirtualImageDecomposer.DONE_FILE);
					File failed = new File(workingDir + "/" + VirtualImageDecomposer.FAILURE_FILE);
					
					// queue task if not done/failed
					if (inputs.exists() && !done.exists() && !failed.exists()) {
						log.info("Queueing fragment computation task: " + workingDir + "/");
						if (!VirtualImageDecomposer.fragmentComputationTasksQueue.isShutdown()) {
							VirtualImageDecomposer.fragmentComputationTasksQueue.execute(new FragmentComputationTask(workingDir + "/"));
						} else {
							log.warn("fragmentComputationTasksQueue is down");
						}
					} else {
						log.debug("Fragment computation is done/failed or " + VirtualImageDecomposer.INPUTS_FILE + " is missing" );
					}
				}
			}
		} else log.error("Missing storage path:" + Configuration.virtualImageDecomposerPath);
	}
	
	public void contextDestroyed(ServletContextEvent sce) {
		log.info("Shutting down VirtualImageDecomposer service...");
		VirtualImageDecomposer.fragmentComputationTasksQueue.shutdown();
	}
}
