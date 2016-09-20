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
import java.io.Writer;
import java.util.Vector;

import hu.mta.sztaki.lpds.cloud.entice.util.exception.ProcessStartupException;
import hu.mta.sztaki.lpds.cloud.entice.util.exception.ScriptExecutionError;
import hu.mta.sztaki.lpds.cloud.entice.util.exception.ThreadTimeoutException;

public class ThreadedExec extends ExecHelper {

	private long timeout;

	private Thread executor;

	/**
	 * 
	 * @param timeout
	 *            in ms, if -1 the timeout is disabled, the thread left running
	 *            forever.
	 */
	public ThreadedExec(long timeout) {
		this.timeout = timeout;
	}

	/**
	 * Might leave the executor thread running forever (if the timeout was set
	 * up as -1ms). The caller has to terminate the thread by destroying the non
	 * timeouted process!
	 */
	@Override
	public ExecResult execProg(final String execme, final boolean waitForResults, final Writer sendoutput,
			final boolean saveout) throws IOException, InterruptedException, ScriptExecutionError {
		LocalLogger.myLogger.info("Threaded executor starts for " + execme);
		final Vector<Exception> ex = new Vector<Exception>();
		final Vector<Process> p = new Vector<Process>();
		executor = new Thread(Thread.currentThread().getThreadGroup(), "ThreadedExecutor " + execme) {
			public void run() {
				try {
					Process pr = ThreadedExec.super.execProg(execme, false, sendoutput, saveout).getRan();
					p.add(pr);
					int retcode = pr.waitFor();
					if (timeout == -1) {
						LocalLogger.myLogger.info("Daemon execution stopped (" + retcode + "): " + execme);
					}
				} catch (Exception e) {
					LocalLogger.myLogger.warning("Threaded execution failed: " + e.getMessage());
					ex.add(e);
				}
			};
		};
		executor.start();
		if (timeout == -1) {
			while (p.isEmpty()) {
				Thread.sleep(100);
			}
		} else {
			LocalLogger.myLogger.info("Timeout before: " + execme);
			executor.join(timeout);
			LocalLogger.myLogger.info("Timeout after: " + execme);
			if (executor.isAlive()) {
				p.get(0).destroy();
				LocalLogger.myLogger.info("DESTROY: " + execme);
				throw new ThreadTimeoutException("Timeout while executing: " + execme, null);
			} else if (!ex.isEmpty()) {
				LocalLogger.myLogger.info("EXFOUND: " + execme);
				throw new ProcessStartupException("Failed to start the process: " + execme, ex.get(0));
			}
		}
		return waitForResults ? new ExecResult(p.get(0).exitValue()) : new ExecResult(p.get(0));
	}
}
