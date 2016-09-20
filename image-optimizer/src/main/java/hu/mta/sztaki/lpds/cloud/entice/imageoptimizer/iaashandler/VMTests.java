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
import java.util.concurrent.TimeoutException;

public class VMTests {

	public static final String vmUptimeCheck = "scripts/testuptime.sh";

	public static int checkUptime(String host, String port, String login)
			throws InterruptedException, IOException, VMManagementException, TimeoutException {
		try {
			Shrinker.myLogger.info("Checkuptime called on " + host);
			StringWriter sw = new StringWriter();
			if (RemoteExecutor.REMEXECERRORS
					.contains(ScriptError.mapError(new RemoteExecutor(new ExecHelper()).remoteExecWithRetry(3, host,
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

	public static void restartTest(String host, String port, String login, int beforeRestart)
			throws InterruptedException, IOException, VMManagementException {
		Shrinker.myLogger.info("restartTest");
		for (int i = 0; i < 5; i++) {
			try {
				int returncode = checkUptime(host, port, login);
				if (returncode < beforeRestart) {
					Shrinker.myLogger.info("Restart detected");
					return;
				}
			} catch (TimeoutException e) {

			}
			Thread.sleep(10000);
		}
		throw new VMManagementException("Restart was unsuccessful for " + host, null);
	}
}
