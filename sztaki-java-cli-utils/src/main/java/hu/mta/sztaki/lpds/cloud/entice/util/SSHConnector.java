/*
 *    Copyright 2009-2015 Gabor Kecskemeti, University of Westminster, MTA SZTAKI
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package hu.mta.sztaki.lpds.cloud.entice.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.TreeMap;

import hu.mta.sztaki.lpds.cloud.entice.util.exception.ScriptExecutionError;

/**
 * This class allows building ssh proxies, for firewalled ssh connections
 * 
 * @author gaborkecskemeti
 *
 */
public class SSHConnector {
	public static String connectionBlocker = "blocker";
	public static Random generator = new Random();
	private final int centralport;
	private final String centraluser;
	private final TreeMap<Integer, Process> portMappers = new TreeMap<Integer, Process>();

	private final static TreeMap<String, SSHConnector> connectors = new TreeMap<String, SSHConnector>();

	public static SSHConnector getSSHConnector() {
		synchronized (connectors) {
			return connectors.get(Thread.currentThread().getThreadGroup().getName());
		}
	}

	public SSHConnector(String user, String sourcehost, String sourceport, String targetuser, String targethost,
			String remport, boolean noPortMapping)
			throws UnknownHostException, ScriptExecutionError, IllegalStateException {
		if (getSSHConnector() != null)
			throw new IllegalStateException("The current thread already has an ssh connector instance");
		if (noPortMapping) {
			LocalLogger.myLogger.info("Direct SSH connections!");
			centralport = -1;
		} else {
			centralport = openConnection(user, sourcehost, sourceport, targethost, remport, noPortMapping).getPort();
		}
		connectors.put(Thread.currentThread().getThreadGroup().getName(), this);
		centraluser = targetuser;
	}

	public boolean portScan(InetAddress addr, int port) {
		synchronized (connectionBlocker) {
			LocalLogger.myLogger.info("Scnanning: " + addr + ":" + port);
			try {
				Socket s = new Socket(addr, port);
				s.close();
				return true;
			} catch (IOException e) {
				return false;
			}
		}
	}

	public void destroyConnector() {
		synchronized (portMappers) {
			for (Process p : portMappers.values()) {
				p.destroy();
			}
			portMappers.clear();
		}
		synchronized (connectors) {
			for (String connid : connectors.keySet()) {
				if (connectors.get(connid) == this) {
					connectors.remove(connid);
					break;
				}
			}
		}
	}

	public void destroyConnection(int port) {
		synchronized (portMappers) {
			Process p = portMappers.remove(port);
			if (p != null)
				p.destroy();
		}
	}

	public URI openConnection(String targethost, String remport) throws UnknownHostException, ScriptExecutionError {
		return openConnection(centraluser, "localhost", "" + centralport, targethost, remport, centralport <= 0);
	}

	private URI openConnection(final String user, final String sourcehost, final String sourceport,
			final String targethost, final String remport, boolean noPortMapping)
			throws UnknownHostException, ScriptExecutionError {
		LocalLogger.myLogger.info("Connection request for " + targethost + ":" + remport);
		String mappedhost = targethost;
		String mappedport = remport;
		if (!noPortMapping) {
			synchronized (connectionBlocker) {
				int portToTest = 0;
				while (portScan(InetAddress.getLocalHost(), portToTest = generator.nextInt(60000) + 1000))
					;
				LocalLogger.myLogger.info("Detected unused port: " + portToTest);
				final int portToOpen = portToTest;
				// Request daemon execution
				ThreadedExec t = new ThreadedExec(-1); // wait forever to complete remote execute
				try {
					Process ran = t.execProg(
							"ssh -N -o ServerAliveInterval=5 -o ConnectTimeout=5 -o BatchMode=yes -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -p "
									+ sourceport + " " + user + "@" + sourcehost + " -L" + portToOpen + ":" + targethost
									+ ":" + remport,
							false, null, false).getRan();
					synchronized (portMappers) {
						portMappers.put(portToOpen, ran);
					}
					Thread.sleep(1500);
					try {
						ran.exitValue();
						throw new ScriptExecutionError("cannot create ssh connections for host: " + sourcehost, null);
					} catch (IllegalThreadStateException e) {
						// Ssh connection still established
					}
					mappedhost = "localhost";
					mappedport = "" + portToOpen;
				} catch (Exception e) {
					LocalLogger.myLogger.warning("cannot initiate ssh connection " + e.getMessage());
				}
			}
		}
		try {
			return new URI("ssh://" + mappedhost + ":" + mappedport);
		} catch (URISyntaxException e) {
			LocalLogger.myLogger.severe(e.getMessage());
			return null;
		}
	}
}
