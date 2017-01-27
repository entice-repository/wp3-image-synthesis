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
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.concurrent.TimeoutException;

import hu.mta.sztaki.lpds.cloud.entice.util.exception.ScriptExecutionError;

public class RemoteExecutor {

	public static final String remoteExecutorScript = "scripts/remoteexecute.sh";
	public static final String keyfile;
	public static final EnumSet<ScriptError> REMEXECERRORS = EnumSet.of(ScriptError.REMEXEC_IPCHECK,
			ScriptError.REMEXEC_SCRIPTCOPY, ScriptError.REMEXEC_EXEC);

	private final ExecHelper executor;
	
	static {
		String kftemp = System.getProperty("hu.mta.sztaki.lpds.cloud.entice.util.sshkey");
		if (kftemp == null) {
			LocalLogger.myLogger.severe("Cannot init exechelper, no sshkey found in system property definitions!");
			throw new RuntimeException("hu.mta.sztaki.lpds.cloud.entice.util - not present");
		}
		keyfile = kftemp;
	}

	public RemoteExecutor(ExecHelper executor) {
		this.executor = executor;
	}

	// executes 5 times with 5 seconds delay the script on the remote host (see remoteexecute.sh)
	public ExecHelper.ExecResult remoteExecute(String exechost, String execport, String login, InetAddress realIP, String key,
			String execme, Writer output, boolean saveout, boolean wait)
			throws IOException, InterruptedException, ScriptExecutionError {
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] remote execute: " + execme + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
		ExecHelper.ExecResult result = executor.execProg(
				ExecHelper.transformScriptsLoc(remoteExecutorScript) + " " + (key == null ? keyfile : key) + " "
						+ exechost + " " + execport + " " + (saveout ? "yes" : "no") + " "
						+ (realIP != null ? realIP.getHostAddress() : "NOIPCHECK") + " " + execme + " " + (login == null ? "root" : login),
				wait, output, saveout);
//		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] remote execute ret code: " + result + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
		return result;
	}

	public int remoteExecWithRetry(int retries, String exechost, String execport, String login, InetAddress realIP, String key, 
			String execme, Writer output, boolean saveout) throws TimeoutException, IOException, ScriptExecutionError {
		Writer orig = output;
		int retcode = ScriptError.REMEXEC_EXEC.errno;
		do {
			LocalLogger.myLogger.info("Retryable execution starts");
			if (orig != null) {
				// FIXME only stringwriters are allowed as output writers
				output = new StringWriter();
			}
			try {
//				System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] remote execute retry: " + retries);
				retcode = remoteExecute(exechost, execport, login, realIP, key, execme, output, saveout, true).getRetcode(); // takes 21 seconds on rsync error with 5 trials and 1 sec sleep in remoteexecute.sh
			} catch (InterruptedException e) {
			} catch (IOException e) {
			}
			LocalLogger.myLogger.info("Retry -" + retries + "- err:" + retcode);
		} while (REMEXECERRORS.contains(ScriptError.mapError(retcode)) && --retries > 0);
		if (retries <= 0) {
			System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] RemoteExecutor: max retries reached: " + retries + " for script: " + execme);
			throw new TimeoutException("ExecwithRetryTimeout");
		} else {
			LocalLogger.myLogger.info("Retryable execution finishes");
			if (orig != null) {
				orig.write(output.toString());
			}
			return retcode;
		}
	}
}
