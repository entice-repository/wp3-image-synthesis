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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker.ShrinkingContext;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMManagementException;

public class VMInstanceManager extends Thread {
	private static final TreeMap<String, VMInstanceManager> instanceManagers = new TreeMap<String, VMInstanceManager>();

	private class InstanceAllocationData {
		final VirtualMachine vm;
		private int allocations;

		public InstanceAllocationData(VirtualMachine vm) {
			this.vm = vm;
			allocations = 0;
		}

		public int getAllocations() {
			return allocations;
		}

		public void newAllocation() {
			allocations++;
		}

		@Override
		public String toString() {
			return vm.toString() + " -> Allocations: " + allocations;
		}
	}

	private final Vector<InstanceAllocationData> vms = new Vector<InstanceAllocationData>();
	private boolean terminated = false;
	private final int maxvm;
	private String previousVMlisting;
	private ShrinkingContext sc;

	public static VMInstanceManager getManagerInstance() {
		String threadgroupname = Thread.currentThread().getThreadGroup().getName();
		VMInstanceManager returner = instanceManagers.get(threadgroupname);
		if (returner == null) {
			throw new IllegalStateException("This threadgroup does not have an initiated instance manager.");
		}
		return returner;
	}

	public static AtomicInteger reinitFailures = new AtomicInteger();
	public static final int REINIT_LIMIT = 100; // maximum number of unsuccessful VM starts
	
	public VMInstanceManager(ThreadGroup tg, int maxvm) {
		super(tg, "VMInstanceManager");
		this.maxvm = maxvm;
		instanceManagers.put(tg.getName(), this);
		sc=Shrinker.getContext();
		if (maxvm != 0) {
			start();
		}
	}

	@Override
	protected void finalize() throws Throwable {
		instanceManagers.remove(getThreadGroup().getName());
		super.finalize();
	}

	/*
	@SuppressWarnings("unused")
	private VirtualMachine getNewVM() throws VMManagementException {
		String threadName = Thread.currentThread().getName();
		Shrinker.myLogger.info("New VM request " + threadName);
		while (sc.isRunning() && isAlive()) {
			synchronized (vms) {
				boolean createvm = false;
				Collections.shuffle(vms);
				for (InstanceAllocationData iad : vms) {
					if (iad.vm.getState().equals(VirtualMachine.VMState.FREE)) {
						createvm = true;
						if (iad.getAllocations() == 0) {
							iad.newAllocation();
							return iad.vm;
						}
					}
				}
				if (createvm) {
					InstanceAllocationData maxiad = new InstanceAllocationData(null);
					for (InstanceAllocationData iad : vms) {
						if (iad.vm.getState().equals(VirtualMachine.VMState.FREE)) {
							if (maxiad.getAllocations() <= iad.getAllocations()) {
								maxiad = iad;
							}
						}
					}
					if (maxiad.vm != null) {
						maxiad.vm.terminate();
					}
				}
			}
			int maxtestcount = 1000;
			newvmwaiterloop: while (sc.isRunning() && isAlive()) {
				synchronized (vms) {
					for (InstanceAllocationData iad : vms) {
						if (iad.vm.getState().equals(VirtualMachine.VMState.FREE)) {
							if (iad.getAllocations() == 0) {
								break newvmwaiterloop;
							}
						}
					}
				}
				if (maxtestcount-- < 0) {
					break;
				}
				try {
					sleep(10);
				} catch (InterruptedException e) {
				}
			}
		}
		return null;
	}
	 */
	// get FREE VM with 0 allocations
	/*
	public VirtualMachine getNewVMAndAcquire() throws VMManagementException {
		String threadName = Thread.currentThread().getName();
		Shrinker.myLogger.info("New VM request (with 0 allocations) " + threadName);
		while (sc.isRunning() && isAlive()) {
			synchronized (vms) {
				boolean createvm = false;
				Collections.shuffle(vms);
				for (InstanceAllocationData iad : vms) {
					if (iad.vm.getState().equals(VirtualMachine.VMState.FREE)) {
						createvm = true;
						if (iad.getAllocations() == 0) {
							iad.newAllocation();
							iad.vm.setAcquired(); // acquire immediately
							return iad.vm;
						}
					}
				}
				// if there is free VM but with allocations > 0 
				if (createvm) {
					InstanceAllocationData maxiad = new InstanceAllocationData(null);
					for (InstanceAllocationData iad : vms) {
						if (iad.vm.getState().equals(VirtualMachine.VMState.FREE)) {
							if (maxiad.getAllocations() <= iad.getAllocations()) {
								maxiad = iad;
							}
						}
					}
					if (maxiad.vm != null) {
						maxiad.vm.terminate();
					}
				}
			}
			
			int maxtestcount = 1000;
			newvmwaiterloop: while (sc.isRunning() && isAlive()) {
				synchronized (vms) {
					for (InstanceAllocationData iad : vms) {
						if (iad.vm.getState().equals(VirtualMachine.VMState.FREE)) {
							if (iad.getAllocations() == 0) {
								break newvmwaiterloop;
							}
						}
					}
				}
				if (maxtestcount-- < 0) {
					break;
				}
				try {
					sleep(new java.util.Random().nextInt(1000));
				} catch (InterruptedException e) {
				}
			}
		}
		// FIXME
		Shrinker.myLogger.severe("Returning null VM");
		return null;
	} */

	public VirtualMachine getAndAcquireZeroAllicationsVM() {
		String threadName = Thread.currentThread().getName();
		Shrinker.myLogger.info("VM request (with 0 allocations) " + threadName);
		final long sleep = 10000l; // 10 sec
		int maxtestcount = 20 * 6; // 20 * 6 * 10s = 20 mins
		int testCount = maxtestcount;
		boolean vmKilled = false;
		while (sc.isRunning() && isAlive()) {
			
			synchronized (vms) {
				Collections.shuffle(vms);
				InstanceAllocationData vmToKill = null;
				for (InstanceAllocationData iad : vms) {
					if (iad.vm.getState().equals(VirtualMachine.VMState.FREE)) {
						if (iad.getAllocations() == 0) {
							iad.newAllocation();
							iad.vm.setAcquired(); // acquire immediately
							return iad.vm;
						} else {
							vmToKill = iad;
						}
					}
				}
				
				System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Couldn't find zero-allocation FREE VM. Counter: " + testCount + " " + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
				if (!vmKilled && vmToKill != null) {
					System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] VM " + vmToKill.vm.getInstanceId() + " is FREE with allocations: " + vmToKill.getAllocations() + ". Killing. (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
					try { vmKilled = true; vmToKill.vm.terminate(); }
					catch (VMManagementException x) {
						Shrinker.myLogger.severe("Exzeption at killing zero-allocation FREE VM: " + vmToKill.vm.getInstanceId());
						System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Exzeption at killing zero-allocation FREE VM " + vmToKill.vm.getInstanceId() + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
					}
				}
			}
			try {
				Thread.sleep(sleep); // sleep 10 sec
			} catch (InterruptedException e) {}
			if (testCount-- <= 0) {
				Shrinker.myLogger.severe("Cannot acquire zero-allocation FREE VM for " + (maxtestcount * 10) + " seconds. Returning null");
				System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Cannot acquire zero-allocation FREE VM for " + (maxtestcount * 10 ) + " seconds. Returning null. " + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
				return null;
			}
		}
		Shrinker.myLogger.info("VMInstanceManager getAndAcquireZeroAllicationsVM after context down " + threadName);
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] WARN: Cannot acquire zero-allocation VM after context down, Returning null.  " + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
		return null;
	}

	
	public VirtualMachine getAndAcquireNextAvailableVM() {
		String threadName = Thread.currentThread().getName();
		Shrinker.myLogger.info("VM request (get and acquire) " + threadName);
		final long sleep = 10000l; // 10 sec
		int maxtestcount = 20 * 6; // 20 * 6 * 10s = 20 mins
		int testCount = maxtestcount;
		while (sc.isRunning() && isAlive()) {
			
			synchronized (vms) {
				Collections.shuffle(vms);
				for (InstanceAllocationData iad : vms) {
					if (iad.vm.getState().equals(VirtualMachine.VMState.FREE)) {
						iad.newAllocation();
						iad.vm.setAcquired(); // acquire immediately
						return iad.vm;
					}
				}
			}
			try {
				Thread.sleep(sleep); // sleep 10 sec
			} catch (InterruptedException e) {}
			if (testCount-- <= 0) {
				Shrinker.myLogger.severe("Cannot acquire VM for " + (maxtestcount * 10) + " seconds. Returning null");
				System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Cannot acquire VM for " + (maxtestcount * 10 ) + " seconds. Returning null. " + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
				return null;
			}
		}
		Shrinker.myLogger.info("VMInstanceManager getAndAcquireNextAvailableVM after context down " + threadName);
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] WARN: Cannot acquire VM after context down, Returning null.  " + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
		return null;
	}


	/*
	@SuppressWarnings("unused")
	private VirtualMachine getNextAvailableVM() {
		String threadName = Thread.currentThread().getName();
		Shrinker.myLogger.info("VM request " + threadName);
		while (sc.isRunning() && isAlive()) {
			synchronized (vms) {
				Collections.shuffle(vms);
				for (InstanceAllocationData iad : vms) {
					if (iad.vm.getState().equals(VirtualMachine.VMState.FREE)) {
						iad.newAllocation();
						return iad.vm;
					}
				}
			}
			try {
				sleep(10);
			} catch (InterruptedException e) {
			}
		}
		return null;
	} */
	
	@Override
	public void run() {
		long beats = 0, lastreport = 0;
		
		class VMCreatorThread extends Thread {
			private boolean terminated = false;

			public VMCreatorThread(ThreadGroup tg) {
				super(tg, "VMC" + Math.random());
			}

			@Override
			public void run() {
				Shrinker.myLogger.info("START:" + getName());
				// creates VM with currently used VA and no (null) modification.
				// (ie. mods are to be applied during runtime)
				VirtualMachine vm = null;
				try {
					vm = VMFactory.instance.requestVM(Shrinker.getContext().getVaid(), null);
				} catch (IllegalArgumentException x) {
					System.out.println("Exception: " + x.getMessage());
					Shrinker.myLogger.warning(x.getMessage());
				}
				if (vm != null) {
					if (!terminated) {
						synchronized (vms) {
							vms.add(new InstanceAllocationData(vm));
							Shrinker.myLogger.info("Instance allocation data about instance " + vm.getInstanceId() + " added to vms");
						}
					} else {
						try {
							Shrinker.myLogger.info("Termination after VM init: " + vm.toString());
							vm.terminate();
						} catch (VMManagementException e) {
							Shrinker.myLogger.warning("Cannot terminate just created vm" + e.getMessage());
						}
					}
				}
				Shrinker.myLogger.info("STOP:" + getName());
			}
		}
		
		Vector<VMCreatorThread> vmcs = new Vector<VMCreatorThread>();
		try {
			Shrinker.myLogger.info("VMInstancemanager thread started");
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					Shrinker.myLogger.info("Shutdown hook handler launched");
					Shrinker.myLogger.info("Context.running=" + sc.isRunning());
					sc.stop();
					long time = System.currentTimeMillis();
					// Wait 60 seconds before giving up waiting on VMI to terminate
					while (!terminated && System.currentTimeMillis() - time < 60000)
						yield();
					if(!terminated) Shrinker.myLogger.warning("Exiting without complete termination of managed VMs!");
					else Shrinker.myLogger.info("Managed VMs terminated!");
				}
			});
			for (int i = 0; i < maxvm; i++) {
				try {
					sleep(50);
				} catch (InterruptedException e) {
				}
				VMCreatorThread vmc = new VMCreatorThread(getThreadGroup());
				vmc.start();
				vmcs.add(vmc);
			}
			Shrinker.myLogger.info("Initial set of VMs created.");
			while (sc.isRunning()) {
				int sleeptime = 10;
				// Every five secs we present a vm listing if necessary.
				if (beats - lastreport > 500) {
					lastreport = beats;
					StringBuilder sb = new StringBuilder();
					synchronized (vms) {
						Vector<InstanceAllocationData> vmscopy = new Vector<InstanceAllocationData>(vms);
						Collections.sort(vmscopy, new Comparator<InstanceAllocationData>() {
							public int compare(InstanceAllocationData o1, InstanceAllocationData o2) {
								return o1.hashCode() - o2.hashCode();
							};
						});
						for (InstanceAllocationData iad : vms) {
							sb.append("\t");
							sb.append(iad.toString());
							sb.append("\n");
						}
					}
					sb.append("\tVMs under creation " + vmcs.size());
					String newVMlisting = sb.toString();
					if (!newVMlisting.equals(previousVMlisting)) {
						previousVMlisting = newVMlisting;
						Shrinker.myLogger.info("Current VM listing: \n" + newVMlisting);
					}
				}
				
				Iterator<VMCreatorThread> it = vmcs.iterator();
				while (it.hasNext()) {
					if (!it.next().isAlive()) {
						it.remove();
					}
				}
				synchronized (vms) {
					for (InstanceAllocationData iad : vms) {
						if (iad.vm.isInFinalState()) {
							Shrinker.myLogger.info("Defunct VM detected.");
							vms.remove(iad);
							VMCreatorThread vmc = new VMCreatorThread(getThreadGroup());
							vmc.start();
							vmcs.add(vmc);
							sleeptime = 50;
							break;
						}
					}
				}
				try {
					sleep(sleeptime);
					beats += sleeptime / 10;
				} catch (InterruptedException e) {
				}
			}  // end while (sc.isRunning()) {
			
			// shrinking ended -----------------------------------------
			Shrinker.myLogger.info("Terminating managed VMs...");
			for (VMCreatorThread vmc : vmcs) {
				vmc.terminated = true;
				Shrinker.myLogger.info("VM creator thread " + vmc.getId() + " terminated"); 
			}
			
			for (InstanceAllocationData iad : vms) {
				if (!iad.vm.isInFinalState()) Shrinker.myLogger.info("VM to terminate: " + iad.vm.getInstanceId() + ", status: " + iad.vm.getState());
				else Shrinker.myLogger.info("VM is in final state (no termination): " + iad.vm.getInstanceId() + ", status: " + iad.vm.getState());
				if (!iad.vm.isInFinalState()) {
					try {
						iad.vm.terminate();
					} catch (VMManagementException x) {
						System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Cannot terminate VM: " + iad.vm.getInstanceId() + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
						Shrinker.myLogger.info("Exception: cannot terminate VM: " + iad.vm.getInstanceId() + ""); 
					}
				}
//				if (iad.vm.isInFinalState()) Shrinker.myLogger.warning("VM is in final state, still terminating...");
//				iad.vm.terminate();
			}
			vms.removeAllElements();
			Shrinker.myLogger.info("VM instance manager ended");
			terminated = true;
			
			Shrinker.myLogger.info("###phase: done");
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.out.println("Fatal error: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

}
