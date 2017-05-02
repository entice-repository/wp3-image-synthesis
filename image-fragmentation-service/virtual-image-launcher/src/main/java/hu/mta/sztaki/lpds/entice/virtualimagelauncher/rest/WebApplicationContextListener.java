package hu.mta.sztaki.lpds.entice.virtualimagelauncher.rest;

import javax.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.ServletContextEvent;

public class WebApplicationContextListener implements ServletContextListener {
	private static final Logger log = LoggerFactory.getLogger(Launcher.class); 
	
	public void contextInitialized(ServletContextEvent sce) {
		log.info("Starting up Virtual Image Launcher service...");
	}
	
	public void contextDestroyed(ServletContextEvent sce) {
		log.info("Shutting down Virtual Image Launcher service...");
	}	
}