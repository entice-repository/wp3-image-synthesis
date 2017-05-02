package hu.mta.sztaki.lpds.entice.virtualimagelauncher.fco;

import java.io.Closeable;
import java.io.OutputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SshSession implements Closeable {
	private static final Logger log = LoggerFactory.getLogger(SshSession.class); 
	private final Session session;
	
	public SshSession(final String host, final String user, final String privateKeyFile) throws JSchException {
		synchronized (this) {
			JSch jsch = new JSch();
			if (privateKeyFile != null) jsch.addIdentity(privateKeyFile); // 
	        Session session = jsch.getSession(user, host, 22);
	        Properties config = new Properties(); 
	        config.put("StrictHostKeyChecking", "no");
	        session.setConfig(config);
	        session.setTimeout(10 * 60 * 1000); // 10 min session timeout
	        try {
	        	session.connect(10000); // 10sec
	        } catch (JSchException e) { // failover on com.jcraft.jsch.JSchException: Auth fail
//	        	String errorMsg = e.getMessage();
//        		log.debug("Auth failed at openning SSH connection (" + errorMsg + "). Retrying once again in 1 sec...");
        		try { session.disconnect(); } catch (Exception x) {}
        		try { Thread.sleep(1000); } catch (InterruptedException x) {}
        		jsch = new JSch();
        		session = jsch.getSession(user, host, 22);
        		if (privateKeyFile != null) jsch.addIdentity(privateKeyFile);  
    			session.setConfig(config);
    	        session.setTimeout(10 * 60 * 1000); // 10 min session timeout
        		session.connect(10000);
	        }
	        this.session = session;
		}
	}
	
	@Override public void close() {
		if (session != null) session.disconnect();
		log.trace("...session disconnected");
	}
	
	public int executeCommand(final String cmd) throws JSchException {
		return executeCommand(cmd, null, null);
	}
	
	public int executeCommand(final String cmd, OutputStream stdout, OutputStream stderr) throws JSchException {
		Channel channel = null;
		int exitStatus = -1;
		if (stdout == null) stdout = new OutputStreamWrapper();
		if (stderr == null) stderr = new OutputStreamWrapper();
		try {
			if (!session.isConnected()) session.connect();
			
			channel = session.openChannel("exec");
	        ((ChannelExec) channel).setCommand(cmd);
	        
	        channel.setOutputStream(stdout);
	        channel.setExtOutputStream(stderr);
	        
	        channel.connect();
	        log.trace("...channel connected");
			log.trace("Command: " + cmd);
	        
	        while (!channel.isClosed()) {
	        	try { Thread.sleep(100); } catch (Exception e) {}
	        }
	        exitStatus = channel.getExitStatus();
	        log.trace("Cmd exit status: " + channel.getExitStatus());
	        if (stdout instanceof OutputStreamWrapper && ((OutputStreamWrapper)stdout).size() > 0) {
	        	log.trace("STDOUT: ");
	        	log.trace(stdout.toString()); // ends with newline
	        }
	        if (stdout instanceof OutputStreamWrapper && ((OutputStreamWrapper)stderr).size() > 0) {
	        	log.trace("STDERR: ");
	        	log.trace(stderr.toString()); // ends with newline
	        }
	
	        channel.disconnect();
	        log.trace("...channel disconnected");
		} 
		finally { 
			if (channel != null) channel.disconnect(); 
		}
		return exitStatus;
	}
}
