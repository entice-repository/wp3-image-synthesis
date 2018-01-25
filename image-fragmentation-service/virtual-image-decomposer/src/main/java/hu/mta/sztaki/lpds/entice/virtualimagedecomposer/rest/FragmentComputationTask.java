package hu.mta.sztaki.lpds.entice.virtualimagedecomposer.rest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FragmentComputationTask implements Runnable {
	private static final Logger log = LoggerFactory.getLogger(VirtualImageDecomposer.class);
	
	private static final String BUILD_LOG_FILE = "build.log";
	private static final String BUILD_OUT_FILE = "build.out";

	private final String workingDir;
	FragmentComputationTask(String workingDir) {
		this.workingDir = workingDir;
	}

	@Override public void run() {
		log.debug("Fragment computation task started: " + workingDir);
		int nbdAllocation = -1;
		try {
			// allocate nbd device
			nbdAllocation = NBDAllocation.allocate(); 
			if  (nbdAllocation == -1) { 
				log.error("Cannot allocate NBD device");
			} else {
				// run main script 
				String script = Configuration.MAIN_SCRIPT;
				// FIXME set timeout for process
				String command = "sudo /bin/bash " + Configuration.scriptsDir + "/" + script + " " + workingDir + " " + nbdAllocation;
				// note, redirection will not work:  " &> " + workingDir + "build.log" 
				try {
					log.info("Executing: '" + command + "' in dir: '" + Configuration.scriptsDir +"/'");
					Process p = Runtime.getRuntime().exec(command, null, new File(Configuration.scriptsDir + "/"));
					new ConsumeOutput(p.getErrorStream(), new File(workingDir + BUILD_LOG_FILE)); // write error stream to build file
					new ConsumeOutput(p.getInputStream(), new File(workingDir + BUILD_OUT_FILE));
					try { 
						int exitCode = p.waitFor();
						// handle non-0 exit code
						if (exitCode != 0) {
							log.error("Script finished with exit code: " + exitCode);
							p.destroy();
						} else {
							log.info("Script finished with exit code: " + exitCode);
						}
					} catch (InterruptedException e) {
						log.error("InterruptedException during running script", e);
					}
				} catch (IOException x) {
					log.error("IOException at running script", x);
				} finally {
				}
			}
		} finally {	
			if (nbdAllocation != -1) NBDAllocation.release(nbdAllocation);
		}
		log.debug("Fragment computation task ended: " + workingDir);
	}
	
	class ConsumeOutput extends Thread {
		private final BufferedReader br;
		private final PrintWriter out;

		private ConsumeOutput(InputStream is) throws IOException {
			br = new BufferedReader(new InputStreamReader(is));
			out = null;
			start();
		}
		
		private ConsumeOutput(InputStream is, File file) throws IOException {
			br = new BufferedReader(new InputStreamReader(is));
			out = new PrintWriter(new FileOutputStream(file));
			start();
		}
		
		@Override public void run() {
			try {
				String line = null;
				while ((line = br.readLine()) != null) {
					if (out == null) continue;
					out.println(line);
				}
			} catch (IOException e) { log.error("IOException", e); }
			finally {
				if (out != null) { try { out.close(); } catch (Throwable x) {} }
			}
		}
	}
}