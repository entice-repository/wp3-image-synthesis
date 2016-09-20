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

public enum ScriptError {
	NOERROR(0), REMSCRIPT_ROOT(238), CRVASCRIPT_VAPACK(239), CRVASCRIPT_IMAGECR(
			240), CRVASCRIPT_OVA_UNTAR(241), CRVASCRIPT_IPS(242), CRVASCRIPT_REMSCRIPT(
			243), STARTVM_TRANSFER(244), STARTVM_REM(245), STARTVM_LOC(246), STARTVM_USAGE(
			247), REMSCIPT_RM(248), STARTVM_XEND(249), STARTVM_MOUNT(250), STARTVM_UNTAR(
			251), STARTVM_NOFREECPU(252), REMEXEC_IPCHECK(253), REMEXEC_SCRIPTCOPY(
			254), REMEXEC_EXEC(255), UNKNOWN(-1);
	public final int errno;

	private ScriptError(int errno) {
		this.errno = errno;
	}

	public static ScriptError mapError(int errno) {
		for (ScriptError curr : ScriptError.values()) {
			if (curr.errno == errno) {
				LocalLogger.myLogger.info("Mapped error:" + curr);
				return curr;
			}
		}
		return UNKNOWN;
	}

	public boolean checkError() {
		return errno != 0;
	}
}