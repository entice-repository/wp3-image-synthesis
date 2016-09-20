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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Vector;

public class IPDetector {
	public static Inet4Address getLocalIP() throws SocketException {
		Enumeration<NetworkInterface> nics = NetworkInterface
				.getNetworkInterfaces();
		Vector<Inet4Address> locaddrs = new Vector<Inet4Address>();
		while (nics.hasMoreElements()) {
			NetworkInterface nic = nics.nextElement();
			Enumeration<InetAddress> addrs = nic.getInetAddresses();
			while (addrs.hasMoreElements()) {
				InetAddress addr = addrs.nextElement();
				if ((addr instanceof Inet4Address) && !addr.isLoopbackAddress()) {
					locaddrs.add((Inet4Address) addr);
				}
			}
		}
		if (locaddrs.size() > 1) {
			throw new SocketException("Multiple IPs available");
		}
		return locaddrs.firstElement();
	}
}
