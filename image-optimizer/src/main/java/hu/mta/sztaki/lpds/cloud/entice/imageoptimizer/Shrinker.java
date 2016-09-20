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

package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.TreeMap;
import java.util.Vector;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.DirectoryGroupManager;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.Group;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.GroupManager;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMFactory;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMInstanceManager;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.validator.ParallelValidatorThread;
import hu.mta.sztaki.lpds.cloud.entice.util.ExecHelper;
import hu.mta.sztaki.lpds.cloud.entice.util.ScriptError;

public class Shrinker extends Thread {
	public static Logger myLogger = Logger.getLogger("ASD.Shrinker");
	public static final String intermediateVAcreator = "scripts/makeWPImage.sh";
	public static final String rsyncTest = "scripts/rsynctest.sh";
	public static final String noSSHSetup = "hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.NoSSH";
	public static final String removeScript = "rmme";
	public static final String removalScriptPrelude = "#!/bin/sh\nexec 5>&1 6>&2 >> /dev/null 2>&1\n ROOT=$1 \n [ -z \"$1\" ] && ROOT=/ \n [ \"/reposi*\" = `echo /reposi*` ] || { [ \"$ROOT\" = \"/\" ] && exit 238 ; }\n true\n";
	private static TreeMap<String, ShrinkingContext> contexts = new TreeMap<String, ShrinkingContext>();
	private static final long startTime = System.currentTimeMillis();

	public static class ShrinkingContext {
		public final String origVaid;
		private String vaid, testScript;
		private File mountPoint;
		private File groupStatesFile;
		private boolean running = true;

		public ShrinkingContext(String origVAid) {
			this.origVaid = origVAid;
		}

		public String getTestScript() {
			return testScript;
		}

		public String getVaid() {
			return vaid;
		}

		public File getMountPoint() {
			return mountPoint;
		}

		public File getGroupStatesFile() {
			return groupStatesFile;
		}

		public boolean isRunning() {
			return running;
		}

		@Override
		public String toString() {
			return "ShrinkingContext(id: " + hashCode() + " OrigVA: " + origVaid + " va: " + vaid + " test: "
					+ testScript + " mounted at: " + mountPoint + " states stored at: " + groupStatesFile + ")";
		}
	}

	private static ShrinkingContext getContext(ThreadGroup tg, String origVaid) {
		String tgn = tg.getName();
		ShrinkingContext sc = contexts.get(tgn);
		if (sc == null) {
			sc = new ShrinkingContext(origVaid);
			contexts.put(tgn, sc);
		}
		return sc;
	}

	public static ShrinkingContext getContext() {
		return getContext(Thread.currentThread().getThreadGroup(), null);
	}

	static {
		try {
			// TODO: Allow custom logging setup
			myLogger.setLevel(Level.ALL);
			myLogger.setUseParentHandlers(false);
			FileHandler handler = new FileHandler("Shrinker.log", false);
			handler.setFormatter(new Formatter() {
				@Override
				public String format(LogRecord record) {
					StringBuilder sb = new StringBuilder(record.getLevel().getName());
					sb.append(" ");
					sb.append(record.getMillis());
					sb.append(" T");
					sb.append(record.getThreadID());
					sb.append(" ");
					sb.append(record.getSourceClassName());
					sb.append(".");
					sb.append(record.getSourceMethodName());
					sb.append("(): ");
					sb.append(record.getMessage());
					sb.append("\n");
					return sb.toString();
				}
			});
			myLogger.addHandler(handler);
		} catch (IOException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	public Shrinker(ThreadGroup tg, File mountPoint, String vaid, String vaTestScript) throws IOException {
		super(tg, "shrinker");
		ShrinkingContext sc = getContext(tg, vaid);
		sc.mountPoint = mountPoint;
		sc.vaid = vaid;
		// The file to report the progress of the optimisation
		sc.groupStatesFile = new File(vaid.hashCode() + ".groupStates");
		sc.testScript = vaTestScript;
		myLogger.info("Shrinking session started using context: " + sc);
		new File(removeScript).delete();
		RandomAccessFile raf = new RandomAccessFile(removeScript, "rwd");
		raf.writeBytes(removalScriptPrelude);
		raf.close();
	}

	// TODO Before doing anything we might want to make sure the test specified
	// by the user is validating the original VA

	@Override
	public void run() {
		String imVA = System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.IntermediateVA");
		String maxParallelCPUcount = System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxUsableCPUs");
		myLogger.info("Intermediate VA creation is " + (imVA == null ? "disabled" : "enabled"));
		ShrinkingContext sc = getContext();
		myLogger.finest("Context acquired");
		Itemizer itemizer = new Itemizer(getThreadGroup());
		myLogger.finest("Itemizer created");
		GroupManager dgm = GroupManager.getGrouperInstance();
		myLogger.finest("GroupManager acquired");

		ItemPool pool = ItemPool.getInstance();
		myLogger.finest("ItemPool acquired");
		pool.addGroupManager(dgm);
		myLogger.finest("Group manager added");
		pool.processItemSource(itemizer, getThreadGroup());

		// We pool some extra VMs for effectiveness
		String sshSetup = System.getProperty(Shrinker.noSSHSetup);
		myLogger.info("SSH is " + (sshSetup == null ? "" : "not") + " used for shrinking");
		VMInstanceManager vmim = new VMInstanceManager(getThreadGroup(), sshSetup == null
				? Math.min((int) (ParallelValidatorThread.parallelVMs * 1.25), Integer.parseInt(maxParallelCPUcount))
				: 0);
		while (!pool.isPoolFull()) {
			try {
				sleep(100);
			} catch (InterruptedException e1) {
			}
		}
		Shrinker.myLogger.info("###phase: initial grouping");
		if (dgm instanceof DirectoryGroupManager) {
			dgm.getGroup(sc.getMountPoint().toString()).setTestState(Group.GroupState.CORE_GROUP);
			// dgm.getGroup(mountPoint.toString() + "/dev").setTestState(
			// Group.GroupState.CORE_GROUP);
			try {
				dgm.getGroup(sc.getMountPoint().toString() + "/bin").setTestState(Group.GroupState.CORE_GROUP);
			} catch (NullPointerException x) {
			} // no such path
			try {
				dgm.getGroup(sc.getMountPoint().toString() + "/var").setTestState(Group.GroupState.CORE_GROUP);
			} catch (NullPointerException x) {
			} // no such path
			try {
				dgm.getGroup(sc.getMountPoint().toString() + "/lib/modules").setTestState(Group.GroupState.CORE_GROUP);
			} catch (NullPointerException x) {
			} // no such path
			try {
				dgm.getGroup(sc.getMountPoint().toString() + "/usr/bin").setTestState(Group.GroupState.CORE_GROUP);
			} catch (NullPointerException x) {
			} // no such path
			try {
				dgm.getGroup(sc.getMountPoint().toString() + "/usr/sbin").setTestState(Group.GroupState.CORE_GROUP);
			} catch (NullPointerException x) {
			} // no such path
			try {
				dgm.getGroup(sc.getMountPoint().toString() + "/sbin").setTestState(Group.GroupState.CORE_GROUP);
			} catch (NullPointerException x) {
			} // no such path
		}
		try {
			dgm.loadGroupStates();
		} catch (IOException e) {
			Shrinker.myLogger.info("Group states were not loaded: " + e.getMessage());
		}
		int iterationCounter = 0;
		dgm.evaluateStatistics();
		Shrinker.myLogger.info("Stats before shrinking operation: " + dgm);
		long initialSize = dgm.getTotalSize();
		Shrinker.myLogger.info("###stats: " + iterationCounter + " " + dgm.getTotalSize() + " " + initialSize + " " + System.currentTimeMillis()); // ###stats: <iteration> <current image size> <initial image size> <timestamp>
		List<Group> nextGroups;
		long totalSize = Long.MAX_VALUE;
		Shrinker.myLogger.info("###phase: optimizing file system");

		boolean stoppingCriterion = false;
		while (((nextGroups = dgm.getGroups()).size() > 0) 
				&& ((double) dgm.getRemainingSize() / (double) (dgm.getTotalSize())) > 0.01
				&& !stoppingCriterion) {
			ParallelValidatorThread matureThread = new ParallelValidatorThread(nextGroups);
			matureThread.start();
			try {
				matureThread.join();
			} catch (InterruptedException e1) {
			}
			try {
				dgm.saveGroupStates();
			} catch (IOException e) {
				Shrinker.myLogger.severe("Cannot save current group states: " + e.getMessage());
				break;
			}
			dgm.evaluateStatistics();
			if (imVA != null && (dgm.getTotalSize() * 1.1 < totalSize)) {
				totalSize = dgm.getTotalSize();
				createIntermediateVM("" + iterationCounter);
			}
			Shrinker.myLogger.info("Stats after current shrinking iteration (" + (iterationCounter++) + "): " + dgm);
			Shrinker.myLogger.info("###stats: " + iterationCounter + " " + dgm.getTotalSize() + " " + initialSize + " " + System.currentTimeMillis()); // ###stats: <iteration> <current image size> <initial image size> <timestamp>
			Shrinker.myLogger.info("###vms: " + VirtualMachine.vmsStarted.get());
			// evaluate stopping criteria
			String property = System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxIterationsNum");
			if (property != null) {
				int value = Integer.MAX_VALUE;
				try {
					value = Integer.parseInt(property);
				} catch (NumberFormatException x) {
					Shrinker.myLogger.info("ERROR: Invalid maxIterationsNum: " + property);
				}
				if (iterationCounter + 1 > value) {
					stoppingCriterion = true;
					Shrinker.myLogger.info("STOPPING: maxIterationsNum reached");
				}
			}
			property = System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxNumberOfVMs");
			if (property != null) {
				int value = Integer.MAX_VALUE;
				try {
					value = Integer.parseInt(property);
				} catch (NumberFormatException x) {
					Shrinker.myLogger.info("ERROR: Invalid maxNumberOfVMs: " + property);
				}
				if (VirtualMachine.vmsStarted.get() >= value) {
					stoppingCriterion = true;
					Shrinker.myLogger.info("STOPPING: maxNumberOfVMs reached: " + VirtualMachine.vmsStarted.get());
				}
			}
			property = System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.aimedReductionRatio");
			if (property != null) {
				float value = 0.0f;
				try {
					value = Float.parseFloat(property);
				} catch (NumberFormatException x) {
					Shrinker.myLogger.info("ERROR: Invalid aimedReductionRatio: " + property);
				}
				if ((double) dgm.getRemainingSize() / (double) (dgm.getTotalSize()) <= value) { // means:
																								// totalsize
																								// now/totalsize
																								// before
																								// any
																								// iterations
					stoppingCriterion = true;
					Shrinker.myLogger.info("STOPPING: aimedReductionRatio reached");
				}
			}
			property = System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.aimedSize");
			if (property != null) {
				long value = 0l;
				try {
					value = Long.parseLong(property);
				} catch (NumberFormatException x) {
					Shrinker.myLogger.info("ERROR: Invalid aimedSize: " + property);
				}
				if (dgm.getTotalSize() <= value) {
					stoppingCriterion = true;
					Shrinker.myLogger.info("STOPPING: aimedSize reached");
				}
			}
			property = System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxRunningTime"); // in
																											// seconds
			if (property != null) {
				long value = Long.MAX_VALUE;
				try {
					value = Long.parseLong(property);
				} catch (NumberFormatException x) {
					Shrinker.myLogger.info("ERROR: Invalid maxRunningTime: " + property);
				}
				if ((System.currentTimeMillis() - startTime) >= value * 1000l) {
					stoppingCriterion = true;
					Shrinker.myLogger.info("STOPPING: maxRunningTime reached");
				}
			}

			if (new File("/root/stop").exists()) {
				stoppingCriterion = true;
				Shrinker.myLogger.info("STOPPING: user initiated stop");
			}
		}
		Shrinker.myLogger.info("Shutdown request!");
		sc.running = false;
		VMFactory.instance.terminateFactory();
		// createIntermediateVM("FINAL"); NOTE: it invokes final image creation,
		// now it is invoked from outside
		Shrinker.myLogger.info("###phase: done");
	}

	private void createIntermediateVM(String itID) {
		ExecHelper eh = new ExecHelper();
		ShrinkingContext sc = getContext();
		// String newVAid = Obsolete.genVAid(itID);
		Shrinker.myLogger.info("###phase: final image creation");
		Shrinker.myLogger.info("Creating new partially optimized VA with VAid: " /* + newVAid */);
		try {
			// The below script creates a new VA and places it in the 'repo'
			// parameters: 1. source image filename, 2. rmme script, 3. target
			// image filename
			int error = eh.execProg(intermediateVAcreator + " "
					+ System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.sourceimagefilename")
					/* sc.getVaid().substring(Obsolete.rshPrefix.length()) */ + " "
					+ new File(System.getProperty("user.dir"), Shrinker.removeScript) + " "
					+ System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.targetimagefilename"), // TODO:
																												// single
																												// target
																												// file
																												// name
																												// breaks
																												// intermediate
																												// VA
																												// creation
					true, null, true).getRetcode();
			if (error == 0) {
				// sc.vaid = Obsolete.genVAFileLoc(newVAid);
			} else {
				Shrinker.myLogger.info("Optimized VA was not created, cause: " + ScriptError.mapError(error));
			}
		} catch (Exception e) {
			Shrinker.myLogger
					.warning("Optimized VA cannot be created, progressing with the unoptimized version. Reason: "
							+ e.getMessage());
		}
	}

	public static void main(final String[] args) throws Exception {
		Shrinker.myLogger.info("###phase: starting");
		final ThreadGroup tg = new ThreadGroup("Shrinking");
		final Thread[] shrinkerThread = new Thread[1];
		final Vector<Exception> ex = new Vector<Exception>();
		// Ensures that the threadgroup for shrinking has the rankers and
		// groupers
		Thread init = new Thread(tg, "initthread") {
			public void run() {
				try {
					shrinkerThread[0] = new Shrinker(tg,
							new File(args[0] /* Mounted FS */),
							args[1] /* repository id */,
							args[2] /* test script */);
					Class.forName(
							System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ranking.RankerToUse"))
							.getConstructor((Class<?>[]) null).newInstance((Object[]) null);
					Class.forName(
							System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.GrouperToUse"))
							.getConstructor((Class<?>[]) null).newInstance((Object[]) null);
				} catch (Exception e) {
					ex.add(e);
				}
			}
		};
		init.start();
		init.join();
		// Now that we have set up the basic classes to be used during grouping
		// and ranking we can actually start the shrinking process
		if (ex.isEmpty()) {
			shrinkerThread[0].start();
			shrinkerThread[0].join();
			Shrinker.getContext(tg, null).running = false;
		} else {
			Exception e = ex.get(0);
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}
}
