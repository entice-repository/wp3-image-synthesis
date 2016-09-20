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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;

import hu.mta.sztaki.lpds.cloud.entice.util.exception.ScriptExecutionError;

public class ExecHelper {

	public static String transformScriptsLoc(String relLoc) {
		String prefix = System.getProperty("hu.mta.sztaki.lpds.cloud.entice.util.scriptprefix");
		return (prefix == null ? "" : prefix) + relLoc;
	}

	public static class ExecResult {
		private final Process ran;
		private final int retcode;

		public ExecResult(Process ran) {
			retcode = -1;
			this.ran = ran;
		}

		public ExecResult(int retcode) {
			ran = null;
			this.retcode = retcode;
		}

		private void throwStateProblem(String m) {
			LocalLogger.myLogger.severe(m);
			throw new IllegalStateException(m);
		}

		public Process getRan() {
			if (ran == null) {
				throwStateProblem("Already has a retcode!");
			}
			return ran;
		}

		public int getRetcode() {
			if (retcode == -1) {
				throwStateProblem("No retcode yet!");
			}
			return retcode;
		}
	}

	public ExecResult execProg(final String execme, final boolean waitForResults, final Writer sendoutput,
			final boolean saveout) throws IOException, InterruptedException, ScriptExecutionError {
		class ConsumeOutput extends Thread {
			final BufferedReader br;

			private ConsumeOutput(InputStream is) throws IOException {
				br = new BufferedReader(new InputStreamReader(is), 100000);
				start();
			}

			@Override
			public void run() {
				try {
					String line = null;
					while ((line = br.readLine()) != null) {
						if (saveout) {
							LocalLogger.myLogger.info(execme + " -->>> " + line);
						}
						if (sendoutput != null) {
							sendoutput.write(line + "\n");
						}
					}
					if (sendoutput != null) {
						sendoutput.flush();
						sendoutput.close();
					}
				} catch (IOException ioe) {
					LocalLogger.myLogger.severe("Cannot read output of '" + execme + "', reason: " + ioe.getMessage());
				}
			}
		}
		LocalLogger.myLogger.info("Executing (" + saveout + "): " + execme);
		Process ran = Runtime.getRuntime().exec(execme);
		new ConsumeOutput(ran.getErrorStream());
		new ConsumeOutput(ran.getInputStream());
		return waitForResults ? new ExecResult(ran.waitFor()) : new ExecResult(ran);
	}

}
