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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;
import hu.mta.sztaki.lpds.cloud.entice.util.ExecHelper;
import hu.mta.sztaki.lpds.cloud.entice.util.RemoteExecutor;
/*
 * TODO check why we have had the MMVA Handling exception here included
 * 
import hu.mta.sztaki.lpds.iaas.xentarget.XenVirtualMachine.MMVAHandlingException;
 */

public abstract class VirtualMachine {
	public static final String removeScriptParameterid = "hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.remScripts";
	public static final String keyPairID = "hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.keypair";
	public static final String vmInitCheckScript = "scripts/testBasicVM.sh";
	protected int datacollectorDelay = 100;

	private String instanceid = null;
	private String ip = null;
	private String privateip = null;
	private String port = null;
	private final String imageid;

	public static final String LOGIN_NAME = "loginName"; // hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMFactory.EC2.LoginName
	protected String loginName = "root"; // default
	public String getLoginName() { return this.loginName; }
	
	public static AtomicInteger vmsStarted = new AtomicInteger();

	public static enum VMState {
		PRE, // The VM request is registered locally
		INIT, // The VM request is sent to the infrastructure
		VMREADY, // The VM is ready according to the Infrastructure
		IAASCHECK, // Conformance check for the VM
		REINIT, // Conformance check failed, new VM will be requested
		CONFIG, // The VM is under configuration for the user
		FREE, // The VM is ready for use
		ACQUIRED, // The VM is under use
		DEFUNCT, // The VM is not functional
		INITFAILED // The VM cannot be initiated
	};

	public final EnumSet<VMState> initializingStates = EnumSet.of(VMState.INIT, VMState.REINIT);

	public static class VMSubState {
		private String subStateText = "";

		public String getSubStateText() {
			return subStateText;
		}

		public void setSubStateText(String substate) {
			subStateText = substate;
		}
	}

	private VMState state = VMState.PRE;
	private VMSubState substate = null;
	private final String stateLocker = "VMSTATE_stateLocker" + Math.random();
	private final String preTermLocker = "PRE_TERMINATION_VMSTATE_stateLocker" + Math.random();
	private int repeatCounter = 5;

	@Override
	public String toString() {
		synchronized (stateLocker) {
			return "ST: " + state + (substate == null ? "" : "." + substate.getSubStateText()) + " "
					+ (instanceid != null ? instanceid : "") + " (IP: " + (ip == null ? "-" : ip) + " VMI: "
					+ getImageId() + ")";
		}
	}

	protected void setState(VMState state) {
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] VM " + getInstanceId() + " state: " + state + ", substate: " + (substate == null ? "-" : substate.getSubStateText()) + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");

		synchronized (stateLocker) {
			Shrinker.myLogger.info("VMState change: NEW - " + state + " " + toString());
			if (!state.equals(VMState.ACQUIRED) && (substate != null)) {
				substate = null;
			}
			this.state = state;
		}
	}

	/**
	 * Show the world that the VM is used.
	 * 
	 * @return Externally controllable state variable to reveal the progress of
	 *         the VM's usage after acquiring it
	 */
	public VMSubState setAcquired() throws IllegalStateException {
		synchronized (stateLocker) {
			if (state.equals(VMState.FREE)) {
				setState(VMState.ACQUIRED);
				substate = new VMSubState();
			} else {
				throw new IllegalStateException("A VM can only be acquired if it is FREE! (Current state: " + state + ")");
			}
		}
		return substate;
	}

	public VMSubState getSubState() {
		return substate;
	}
	
	/**
	 * Releases the VM thus sets its state back to free for other uses. </br>
	 * WARNING! After calling this function, the connection between the VM and
	 * the VMSubState object published by the setAcquired function is no longer
	 * maintained.
	 * 
	 **/
	public void releaseVM() throws IllegalStateException {
		synchronized (stateLocker) {
			if (state.equals(VMState.ACQUIRED)) {
				setState(VMState.FREE);
			} else {
				throw new IllegalStateException("A VM can only be released if it is ACQUIRED! (Current state: " + state + ")");
			}
		}
		substate = null;
	}

	protected final void setIP(String newIP) {
		ip = newIP;
	}

	public String getIP() throws VMManagementException {
		return ip;
	}

	protected final void setPrivateIP(String newPrivateIP) {
		privateip = newPrivateIP;
	}

	public String getPrivateIP() throws VMManagementException {
		return privateip;
	}

	protected final void setPort(String newPort) {
		port = newPort;
	}

	public String getPort() throws VMManagementException {
		return port;
	}

	public String getInstanceId() {
		return instanceid;
	}

	public String getImageId() {
		return imageid;
	}

	// constructor
	protected VirtualMachine(String vaid, Map<String, List<String>> parameters, boolean testConformance) {
		long VM_START_TIMEOUT = 20 * 60 * 1000l; // 20s in millis
		this.imageid = vaid;
		int ret = 0;
		if (parameters != null) parseVMCreatorParameters(parameters);
		
		setState(VMState.PRE);
		boolean done = false;
		boolean shutdown = false;
		boolean timeout = false;
		try {
			
			// while we get VM ready or fail to get one
			do {
				
				// kill previous VM if created
				if (instanceid != null) { // if a VM started earlier by failed to start up normally
					Shrinker.myLogger.info("Failed to start VM " + getInstanceId() + ". Killing...");
					setState(VMState.REINIT);
					terminate();
					Shrinker.myLogger.info("VM terminated");
				} else {
					setState(VMState.INIT);
				}
				
				// start a new instance...
				instanceid = runInstance(System.getProperty(keyPairID));
				long vmStartTime = System.currentTimeMillis();

				// wait till IP obtained...
				System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Obtaining IP for VM: " + this.instanceid + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
				int datacounter = 0; // Provides the amount of instance data that is currently available
				while (datacounter < 3) { // sets ip, private ip, port, till 20 mins, setState(VMREADY) if successful
					if (System.currentTimeMillis() > vmStartTime + VM_START_TIMEOUT) {
						timeout = true;
						break;
					}
					if (!Shrinker.getContext().isRunning()) {
						shutdown = true;
						break;
					}
					datacounter = 0;
					datacounter += getIP() == null ? 0 : 1; // calls describe
					datacounter += getPrivateIP() == null ? 0 : 1; // calls describe
					datacounter += getPort() == null ? 0 : 1; // calls describe
					try { Thread.sleep(10000l); } // wait 10 sec
					catch (InterruptedException e) {}
					
				} // while got ip (and port) 

				if (shutdown) break;
				if (timeout) {
					System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Failed to get IP within timeout for VM:" + this.instanceid + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
					setState(VMState.INITFAILED);
					continue;
				}
				
				System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Waiting for state running of VM: " + this.instanceid + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
				// wait until VM gets READY
				while (!getState().equals(VMState.VMREADY)) {
					if (System.currentTimeMillis() > vmStartTime + VM_START_TIMEOUT) {
						timeout = true;
						break;
					}
					if (!Shrinker.getContext().isRunning()) {
						shutdown = true;
						break;
					}

					getIP();
					try { Thread.sleep(10000l); } // wait 10 sec
					catch (InterruptedException e) {}
				}
				
				if (shutdown) break;
				if (timeout) {
					System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Failed to get into RUNNING state within timeout for VM:" + this.instanceid + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
					setState(VMState.INITFAILED);
					continue;
				}
				
				if (getState().equals(VMState.VMREADY)) {
					if (testConformance) {
						setState(VMState.IAASCHECK);
						try {
							long now = System.currentTimeMillis();
							String cmd = ExecHelper.transformScriptsLoc(vmInitCheckScript) + " " + privateip
									+ " 22 " + privateip + " " + RemoteExecutor.keyfile + " " + loginName;
							System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] testBasicVM.sh VM " + getInstanceId() + ": " + cmd + "(@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
							ret = new ExecHelper().execProg(cmd, true, null, false).getRetcode();
							long ellapsed = (System.currentTimeMillis() - now) / 1000l;
							System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] testBasicVM.sh took " + ellapsed + "s. Exit code: " + ret + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
						} catch (IOException e) {
							System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] testBasicVM.sh FAILED " + this.instanceid + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
							Shrinker.myLogger.warning("Initialization check failed because of IOExzeption: " + e.getMessage());
							ret = 1;
						} catch (InterruptedException e) {
							Shrinker.myLogger.warning("Initialization check interrupted because of InterruptedExzeption: " + e.getMessage());
							ret = 1;
						}
						
						// avoid infinite loop of reinits caused by erronous vmInitCheckScript (e.g. wrong login name)
						if (ret != 0) {
							int reinitFailures = VMInstanceManager.reinitFailures.incrementAndGet();
							Shrinker.myLogger.info("VM initialization check failed with exit code: " + ret + " (failures: " + reinitFailures +")");
							setState(VMState.INITFAILED);
							if (reinitFailures > VMInstanceManager.REINIT_LIMIT) {
								Shrinker.myLogger.warning("### Failed VM initialization checks reached limit: " + reinitFailures + " / " + VMInstanceManager.REINIT_LIMIT);
								System.err.println("Exception: REINIT reached limit: " + VMInstanceManager.REINIT_LIMIT + ". Exiting. Manually shut down VMs.");
								System.exit(1);							
							}
						} else {
							done = true;
						}
					}
					
				} 
			} while (!done && --repeatCounter > 0);
			
			if (shutdown) {
				System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Shrinker context down " + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
				Shrinker.myLogger.info("Shrinker context down");
				if (instanceid != null) { 
					// wait bootup before terminate (VMware VMs cannot be killed before boot)
					if (getState().equals(VMState.INIT) || getState().equals(VMState.REINIT)) {
						System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Waiting 60s for VM " + getInstanceId() + " to boot before terminate..." +  " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
						try { Thread.sleep(60000l); } catch (InterruptedException e) {}
					}
					terminate();
				}
				setState(VMState.INITFAILED); 
			} else if (repeatCounter == 0) {
				System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Failed to start VM for 5 times" +  " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
				Shrinker.myLogger.info("Failed to start VM for 5 times");
				if (instanceid != null) { 
					terminate();
				}
				setState(VMState.INITFAILED); 
			} else if (done) {
				setState(VMState.FREE);
			}
			
		} catch (VMManagementException e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			Shrinker.myLogger.finest("Exzeption during VM creation: " + sw.toString());
			System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] VMManagementExzeption at managing VM:" + this.instanceid + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
			try {
				Thread.sleep(datacollectorDelay);
			} catch (InterruptedException e1) {
			}
			try {
				terminate();
			} catch (VMManagementException e1) {
			}
			Throwable curr = e;
			do {
				if (curr instanceof VMManagementException) {
					setState(VMState.INITFAILED);
				}
			} while ((curr = curr.getCause()) != null);
		} catch (Exception e) {
			e.printStackTrace();
			Shrinker.myLogger.severe("Unknown exzeption occured, terminating vm before full init: " + e.getMessage());
			try {
				Thread.sleep(datacollectorDelay);
			} catch (InterruptedException e1) {
			}
			try {
				terminate();
			} catch (VMManagementException e1) {
			}
		}
	}

	public boolean isInFinalState() {
		return EnumSet.of(VMState.REINIT, VMState.DEFUNCT, VMState.INITFAILED).contains(getState());
	}

	public VMState getState() {
		synchronized (stateLocker) {
			return state;
		}
	}

	public void terminate() throws VMManagementException {
		synchronized (preTermLocker) {
			Shrinker.myLogger.info("Terminate request on " + toString());
			VMState newState = VMState.DEFUNCT;
			if (instanceid != null) {
				if (getState().equals(VMState.REINIT)) {
					newState = VMState.REINIT;
				}
				terminateInstance();
				instanceid = null;
			}
			if (!getState().equals(newState)) {
				setState(newState);
			}
		}
	}

	protected abstract String runInstance(String key) throws VMManagementException;

	protected abstract void terminateInstance() throws VMManagementException;

	public abstract void rebootInstance() throws VMManagementException;

	protected abstract void parseVMCreatorParameters(Map<String, List<String>> parameters);

}
