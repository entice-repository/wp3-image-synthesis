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

import java.util.TreeMap;
import java.util.Vector;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.amazontarget.EC2;

public abstract class VMFactory {
	public static final String factorysetup = "hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMFactory";
	public static final VMFactory instance;
	private static final TreeMap<String, Vector<String>> customData = new TreeMap<String, Vector<String>>();

	protected abstract String[] listSPToLookup();

	protected abstract void prepareVMFactory();

	/**
	 * Before terminating the shrinking process this call might be wise to take
	 * for same IaaSs
	 */
	public abstract void terminateFactory();

	protected abstract VirtualMachine createNewVM(String vaId, TreeMap<String, Vector<String>> contextandcustomizeVA);

	private static void propsPopulator() {
		for (String s : instance.listSPToLookup()) {
			Vector<String> vec = new Vector<String>();
			vec.add(System.getProperty(factorysetup + "." + instance.getClass().getSimpleName() + "." + s));
			customData.put(s, vec);
		}
	}

	static {
		try {
			String factoryName=System.getProperty(factorysetup);
			if(factoryName==null) {
				factoryName=EC2.class.getCanonicalName();
			}
			instance = (VMFactory) Class.forName(factoryName).getConstructor((Class<?>[]) null)
					.newInstance((Object[]) null);
			propsPopulator();
			instance.prepareVMFactory();
		} catch (Exception e) {
			// Most likely the class named in the VMFactory setup is not
			// instantiable with the default constructor
			throw new ExceptionInInitializerError(e);
		}
	}

	/**
	 * currently pre-runtime VA modification is supported only via the
	 * XenFactory
	 */
	public VirtualMachine requestVM(String vaId, TreeMap<String, Vector<String>> contextandcustomizeVA) {
		if(contextandcustomizeVA==null) {
			contextandcustomizeVA=new TreeMap<String, Vector<String>>();
		}
		contextandcustomizeVA.putAll(customData);
		return createNewVM(vaId, contextandcustomizeVA);
	}
}
