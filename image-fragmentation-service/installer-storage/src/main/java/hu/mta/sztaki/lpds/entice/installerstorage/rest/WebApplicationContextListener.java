package hu.mta.sztaki.lpds.entice.installerstorage.rest;

import javax.servlet.ServletContextListener;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;

public class WebApplicationContextListener implements ServletContextListener {
	private static final Logger log = LoggerFactory.getLogger(Installers.class); 
	private ScheduledExecutorService scheduler = null;

	public void contextInitialized(ServletContextEvent sce) {
		log.info("Starting up Installer Storage service...");
		try {
			scheduler = Executors.newSingleThreadScheduledExecutor();
			scheduler.scheduleAtFixedRate(new TimedTask(), 0, 5 * 60, TimeUnit.SECONDS); // re-scan every 5 mins
		} catch (Exception e) {
			log.error("Cannot start scheduler: " + e.getMessage());
		}
	}
	
	public void contextDestroyed(ServletContextEvent sce) {
		log.info("Shutting down Installer Storage service...");
		if (scheduler != null) scheduler.shutdown();
	}
	
	class TimedTask extends TimerTask {
		public void run() {
			try { loadNewInstallers(); } 
			catch (Throwable x) { x.printStackTrace(); }
		}
	}
	
	private void loadNewInstallers() {
		// scan all installers in file system and add metadata
		if (new File(Configuration.installerStoragePath).exists()) {
			String [] ids = new File(Configuration.installerStoragePath).list();
			if (ids != null) { 
				for (String id: ids) {
					if (Installers.installerMetadata.containsKey(id)) continue;
					log.debug("Reading installer: " + id + "...");
					File metadataFile = new File(Configuration.installerStoragePath + "/" + id + "/" + Installers.INSTALLER_METADATA_FILE_NAME);
					File installFile = new File(Configuration.installerStoragePath + "/" + id + "/" + Installers.INSTALLER_INSTALL_SCRIPT_FILE_NAME);
					File initFile = new File(Configuration.installerStoragePath + "/" + id + "/" + Installers.INSTALLER_INIT_SCRIPT_FILE_NAME);
					// register installer if it has (1) install script and (2) metadata file
					if (!initFile.exists()) {
						log.debug("No init script file: " + metadataFile.getAbsolutePath());
					}
					if (!installFile.exists()) {
						log.error("No install script file: " + metadataFile.getAbsolutePath());
					} else {
						if (metadataFile.exists()) {
							try {
								JSONObject metadata = new JSONObject(new JSONTokener(new FileReader(metadataFile)));
								Installers.installerMetadata.put(id, metadata);
								log.info("Found installer: " + id);
							} catch (FileNotFoundException x) {} // should not happen
						} else log.error("No metadata file: " + metadataFile.getAbsolutePath());
					}
				}
			}
		} else log.error("Missing storage path:" + Configuration.installerStoragePath);
	}
}
