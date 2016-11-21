package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.rest;

import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;

public class WebApplicationContextListener implements ServletContextListener {
	private static final Logger log = LoggerFactory.getLogger(Optimizer.class); 

	private ScheduledExecutorService scheduler = null;
	
	public void contextInitialized(ServletContextEvent sce) {
		log.info("Starting up Image Optimizer Service...");
		try {
			scheduler = Executors.newSingleThreadScheduledExecutor();
			scheduler.scheduleAtFixedRate(new Scheduler(), 0, 3600, TimeUnit.SECONDS); // start now, run every hour
		} catch (Exception e) {
			log.error("Cannot start scheduler: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public void contextDestroyed(ServletContextEvent sce) {
		log.info("Shutting down Image Optimizer Service...");
		if (scheduler != null) scheduler.shutdownNow();
	}
	
	class Scheduler extends TimerTask {
		public void run() {
			log.debug("Scheduled task started...");
			Optimizer.taskCacheCleanup();
		}
	}
}
