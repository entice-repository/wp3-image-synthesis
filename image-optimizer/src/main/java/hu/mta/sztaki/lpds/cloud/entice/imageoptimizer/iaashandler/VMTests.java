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
package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;
import hu.mta.sztaki.lpds.cloud.entice.util.ExecHelper;
import hu.mta.sztaki.lpds.cloud.entice.util.RemoteExecutor;
import hu.mta.sztaki.lpds.cloud.entice.util.ScriptError;
import hu.mta.sztaki.lpds.cloud.entice.util.exception.ScriptExecutionError;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMManagementException;

import java.io.IOException;
import java.io.StringWriter;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.TimeoutException;

public class VMTests {

	public static final String vmUptimeCheck = "scripts/testuptime.sh";

	public static int checkUptime(String host, String port, String login)
			throws InterruptedException, IOException, VMManagementException, TimeoutException {
		try {
			Shrinker.myLogger.info("Checkuptime called on " + host);
			StringWriter sw = new StringWriter();
			if (RemoteExecutor.REMEXECERRORS
					.contains(ScriptError.mapError(new RemoteExecutor(new ExecHelper()).remoteExecWithRetry(1, host,
							port, login, (InetAddress) null, (String) null, ExecHelper.transformScriptsLoc(vmUptimeCheck), sw, true)))) {
				throw new TimeoutException("remoteExecFailed");
			}
			String[] uptReturnRows = sw.toString().split("\n");
			if(uptReturnRows.length>0) {
				int uptime = Integer.parseInt(uptReturnRows[uptReturnRows.length - 1].trim());
				Shrinker.myLogger.info("Uptime check:" + uptime);
				return uptime;
			} else {
				Shrinker.myLogger.warning("Malformed uptime query response");
				throw new TimeoutException("Malformed response");
			}
		} catch (ScriptExecutionError e) {
			throw new VMManagementException(e.getMessage(), e);
		} catch (NumberFormatException e) {
			Shrinker.myLogger.finest(e.getMessage());
			throw new TimeoutException("Unparsable output, assuming transient situation");
		}
	}

	/*
	 * try to read uptime for at most 4 minutes in every 10 sec (4*60/10=24 times)
	 */
	public static void restartTest(String host, String port, String login, int beforeRestart)
			throws InterruptedException, IOException, VMManagementException {
		Shrinker.myLogger.info("restartTest");
		int delay = 10; // delay between poll in seconds
		long timeout = 4 * 60; // 4 minutes in seconds
		long rebootTime = System.currentTimeMillis();
		do {  // continue up to 4 minutes
			try {
				System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] uptime check (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
				int returncode = checkUptime(host, port, login);
				System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] uptime after restart: " + returncode + "s compared to " + beforeRestart + "s (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
				if (returncode < beforeRestart) {
					Shrinker.myLogger.info("Restart detected");
					System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] restart detected: " + returncode + " < " + beforeRestart);
					return;
				} 
				// sometimes it happens that OpenNebula does not reboot the VM in spite of the reboot EC2 request (e.g., after deleting directory /usr/share)
				// command line reboot however works, so should try in that way too the reboot, but what if EC2 reboot never will work again? maybe we should treat this as a fatal failure (as currently) 
				/* if (i == 0) {
					RemoteExecutor remoteExec = new RemoteExecutor(new ThreadedExec(60000));
					try { 
						Shrinker.myLogger.info("Rebooting from command line");
						System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] rebooting from shell...");
						remoteExec.remoteExecute(host, port, login,
								(InetAddress) null, (String) null, Shrinker.rebootScript, null, false, false)
								.getRetcode();
					} catch (Throwable x) { Shrinker.myLogger.warning("Reboot from command line failed: " + x.getMessage()); } 
				} */
			} catch (TimeoutException e) {
				System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] uptime timeout exzeption ");
			}
			Thread.sleep(delay * 1000l);
			beforeRestart += delay;
		} while (System.currentTimeMillis() - rebootTime < timeout * 1000l);
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] restart was unsuccessful: no uptime returned for " + timeout + "s or no decrease detected");
		throw new VMManagementException("Restart was unsuccessful for " + host, null);
	}
}
