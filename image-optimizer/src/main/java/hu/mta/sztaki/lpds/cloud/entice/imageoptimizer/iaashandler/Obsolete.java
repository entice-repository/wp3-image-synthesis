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

import java.io.IOException;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;
import hu.mta.sztaki.lpds.cloud.entice.util.IPDetector;

/**
 * Functions and data stored in this class are highly suspectible to not work in
 * regular IaaS services. They need to be refactored or thrown out.
 * 
 * @author gaborkecskemeti
 *
 */
public class Obsolete {

	public static final String rshPrefix;

	static {
		try {
			rshPrefix = "root@" + IPDetector.getLocalIP().getHostAddress() + ":";
		} catch (IOException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	public static String genVAid(String itID) {
		return Shrinker.getContext().origVaid.substring(Obsolete.rshPrefix.length() + 1).split("/")[1] + "_ITER_"
				+ itID;
	}

	public static String genVAFileLoc(String newVAid) {
		// repository => where the images are stored in general
		// newVAid => the name of the image
		// appliance.tgz => the actual image
		return Obsolete.rshPrefix + "/repository/" + newVAid + "/appliance.tgz";
	}
}
