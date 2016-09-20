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

package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.validator;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.TimeoutException;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.Group;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.Obsolete;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMFactory;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMInstanceManager;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMManagementException;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMTests;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VirtualMachine.VMSubState;
import hu.mta.sztaki.lpds.cloud.entice.util.ExecHelper;
import hu.mta.sztaki.lpds.cloud.entice.util.RemoteExecutor;
import hu.mta.sztaki.lpds.cloud.entice.util.ScriptError;
import hu.mta.sztaki.lpds.cloud.entice.util.ThreadedExec;
import hu.mta.sztaki.lpds.cloud.entice.util.exception.ScriptExecutionError;
import hu.mta.sztaki.lpds.cloud.entice.util.exception.ThreadTimeoutException;

public class SingleValidatorThread extends Thread {

	public static final int maxValidationRetries = 5;
	private static String parValThreadId = null;

	public static enum ValidationState {
		PRE, SUCCESS, FAILURE, NOTVALIDATED
	}

	private ValidationState vs = ValidationState.PRE;
	public final List<Group> removables;

	public ValidationState getValidationState() {
		return vs;
	}

	public SingleValidatorThread(ThreadGroup tg, List<Group> removables) {
		super(tg, "Validator" + Math.random());
		this.removables = Collections.unmodifiableList(removables);
		Shrinker.myLogger.info("Starting validator thread: " + getName());
		start();
	}

	public static ValidationState executeRemovalAndTest(VirtualMachine vm, String localRemoveScript,
			final boolean testRestart) throws VMManagementException, IllegalStateException {
		VMSubState vms = vm.setAcquired();
		SingleValidatorThread.ValidationState returner = SingleValidatorThread.ValidationState.FAILURE;
		try {
			ThreadGroup tg = Thread.currentThread().getThreadGroup();
			Thread[] threads = new Thread[tg.activeCount() * 10];
			tg.enumerate(threads);
			String currPVThread = null;
			for (Thread t : threads) {
				String temp = t.getName();
				if (temp.startsWith(ParallelValidatorThread.threadPrefix)) {
					currPVThread = temp;
					break;
				}
			}
			RemoteExecutor remoteExec = new RemoteExecutor(new ThreadedExec(60000));
			int returncode = 0;
			if (!currPVThread.equals(parValThreadId)) {
				vms.setSubStateText("REMGLOB");
				returncode = remoteExec.remoteExecute(vm.getIP(), vm.getPort(), vm.getLoginName(),
						InetAddress.getByName(vm.getPrivateIP()), null, Shrinker.removeScript, null, true, true)
						.getRetcode(); 
				Shrinker.myLogger.info("Glob ret:" + returncode);
			} else {
				Shrinker.myLogger.info("Ignoring global removal");
			}
			if (!RemoteExecutor.REMEXECERRORS.contains(ScriptError.mapError(returncode))) {
				vms.setSubStateText("REMLOC");
				returncode = remoteExec
						.remoteExecute(vm.getIP(), vm.getPort(), vm.getLoginName(), (InetAddress) null, null, localRemoveScript, null, true, true)
						.getRetcode(); 
				Shrinker.myLogger.info("Loc ret:" + returncode);
				if (returncode != 255) {
					if ((returncode != 127) && (returncode != 254) && (returncode != 248)) {
						vms.setSubStateText("RESTART");
						Shrinker.myLogger.info("Initiating restart");
						int beforeRestart = 0;
						if (testRestart) {
							try {
								beforeRestart = VMTests.checkUptime(vm.getIP(), vm.getPort(), vm.getLoginName());
							} catch (TimeoutException e) {
								Shrinker.myLogger.warning("Cannot determine uptime before restarting VM");
								throw new ThreadTimeoutException("Uptime", e);
							}
							Shrinker.myLogger.info("Uptime before restart:" + beforeRestart);
						}
						try {
							vm.rebootInstance();
							if (testRestart) {
								VMTests.restartTest(vm.getIP(), vm.getPort(), vm.getLoginName(), beforeRestart);
							}
							returner = executeTest(vm, vms);
						} catch (VMManagementException e) {
							Shrinker.myLogger.info("VM restart failed");
							returner = SingleValidatorThread.ValidationState.FAILURE;
						}
					}
				} else {
					returner = SingleValidatorThread.ValidationState.NOTVALIDATED;
				}
			} else {
				if (returncode == 248) {
					Shrinker.myLogger.severe("REMOVAL FAILURE!");
					throw new VMManagementException("Removal failure during previously validated removals!", null);
				}
				returner = SingleValidatorThread.ValidationState.NOTVALIDATED;
			}
		} catch (IOException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		} catch (InterruptedException e) {
		} catch (ThreadTimeoutException e) {
			// In case of global and local removal execution timeouts we simply
			// leave the VM for termination.
		} catch (ScriptExecutionError e) {
			// If some unexpected scripting error arises we pass it further away
			throw new VMManagementException(e.getMessage(), e);
		}
		if (!returner.equals(SingleValidatorThread.ValidationState.SUCCESS)) {
			vm.terminate();
		}
		return returner;
	}

	public static ValidationState executeTest(VirtualMachine vm, VMSubState vms)
			throws IOException, InterruptedException, VMManagementException {
		if (vms == null)
			vms = vm.setAcquired();
		
		ValidationState vs = rsyncTest(vm, vms) ? actuallyExecuteTest(vm, vms) : ValidationState.FAILURE;
		if (ValidationState.SUCCESS.equals(vs)) {
			vm.releaseVM();
		}
		return vs;
	}

	private static boolean rsyncTest(VirtualMachine vm, VMSubState vms) {
		try {
			int repeatCounter = 3;
			int ret = 0;
			String login = vm.getLoginName();
			Shrinker.myLogger.info("rsync test: " + vm.getIP() + ", script: " + Shrinker.rsyncTest + ", login: " + login);
			do {
				try {
					ret = new ExecHelper().execProg(ExecHelper.transformScriptsLoc(Shrinker.rsyncTest) + " " 
							+ vm.getIP()
							+ " " + login, 
							true, null, false)
							.getRetcode();
				} catch (IOException e) {
//					Shrinker.myLogger.info("rsync test trial failed (" + repeatCounter + "): " + e.getMessage());
					ret = 11;
				} catch (InterruptedException e) {
//					Shrinker.myLogger.info("rsync test trial failed (" + repeatCounter + "): " + e.getMessage());
					ret = 12;
				}
				if (ret != 0) {
//					Shrinker.myLogger.info("rsync test trial failed (" + repeatCounter + "), ret code: " + ret + ", sleeping 10 seconds");
					try { Thread.sleep(10000); } catch (InterruptedException e) {}
				}
			} while (ret != 0 && repeatCounter-- > 0);
			if (ret != 0) {
				Shrinker.myLogger.warning("rsync test failed for vm: " + vm.getIP() + ", ret: " + ret);
			} else {
				Shrinker.myLogger.info("rsync test passed for vm: " + vm.getIP());
				return true;
			}
		} catch (Throwable x) {
			Shrinker.myLogger.warning("rsync test failed: " + x.getMessage());
		}
		return false;
	}
	
	private static ValidationState actuallyExecuteTest(VirtualMachine vm, VMSubState vms)
			throws IOException, InterruptedException, VMManagementException {

		ExecHelper regularLocalExecutor = new ExecHelper();
		vms.setSubStateText("TEST");
		int returncode;
		try {
			returncode = regularLocalExecutor
					.execProg(Shrinker.getContext().getTestScript() + " " + vm.getIP() + " " + vm.getLoginName(), true, null,
							false)
					.getRetcode();
		} catch (ScriptExecutionError e) {
			throw new VMManagementException(e.getMessage(), e);
		}
		Shrinker.myLogger.info("Test ret:" + ScriptError.mapError(returncode) + "(" + returncode + ")");
		if (returncode == 0) {
			return ValidationState.SUCCESS;
		} else if (returncode == 255) {
			return ValidationState.NOTVALIDATED;
		}
		return ValidationState.FAILURE;
	}

	@Override
	public void run() {
		// Assuming successful validation on previous FINAL_VALIDATION_FAILURE
		// could lead to unusable VMs...
		boolean fullValidation = true;
		boolean noMMVA = System.getProperty(Shrinker.noSSHSetup) != null;
		for (Group g : removables) {
			fullValidation |= !g.getGroupState().equals(Group.GroupState.FINAL_VALIDATION_FAILURE);
		}
		if (fullValidation) {
			File validatorScript = new File(getName());
			try {
				int retries = 0;
				boolean nosuccess = true;
				validationloop: do {
					VirtualMachine vm = null;
					VMInstanceManager vim = VMInstanceManager.getManagerInstance();
					if (retries == 0) {
						RandomAccessFile raf = new RandomAccessFile(validatorScript, "rw");
						raf.writeBytes(Shrinker.removalScriptPrelude);
						for (Group g : removables) {
							raf.writeBytes(g.genRemover());
						}
						raf.close();
						Shrinker.myLogger.info(getName() + " has generated the local removables");
					}
					if (noMMVA) {
						Vector<String> removableList = new Vector<String>();
						removableList.add(
								Obsolete.rshPrefix + new File(System.getProperty("user.dir"), Shrinker.removeScript));
						removableList.add(Obsolete.rshPrefix + validatorScript.getAbsolutePath());
						TreeMap<String, Vector<String>> tm = new TreeMap<String, Vector<String>>();
						tm.put(VirtualMachine.removeScriptParameterid, removableList);
						vm = VMFactory.instance.requestVM(Shrinker.getContext().getVaid(), tm);
						switch (vm.getState()) {
						case DEFUNCT:
							retries++;
							continue validationloop;
						case INITFAILED:
							vs = ValidationState.FAILURE;
							break validationloop;
						}
					} else {
						vm = removables.size() > 1 ? vim.getNewVM() : vim.getNextAvailableVM();
					}
					if (vm == null) {
						Shrinker.myLogger.severe(getName() + "failed to acquire VM!");
						return;
					}
					Shrinker.myLogger.info(getName() + " acquires " + vm.toString());
					try {
						vs = noMMVA ? executeTest(vm, null)
								: executeRemovalAndTest(vm, validatorScript.toString(), true);
						Shrinker.myLogger
								.info(getName() + " executed validation on " + removables + " with result: " + vs);
						if (!vs.equals(ValidationState.NOTVALIDATED)) {
							nosuccess = false;
						}
					} catch (VMManagementException ebase) {
						retries++;
						if (maxValidationRetries < retries) {
							Shrinker.myLogger.info("Maximum validation retries achieved, terminating without result");
							throw ebase;
						}
					} catch (IllegalStateException e) {
						throw new VMManagementException("Tried to run validation on an unavailable VM", e);
					} catch (InterruptedException ex) {
						Shrinker.myLogger.warning("Catched an unexpected interruptedexception: " + ex.getMessage());
					} finally {
						if (noMMVA) {
							vm.terminate();
						}
					}
				} while (nosuccess);
				/*
				 * TODO: check if MMVAHandlingExceptions are still important
				 * 
				 * } catch (MMVAHandlingException e) { Shrinker.myLogger.severe(
				 * "Cannot create virtual machine: " + e.getMessage()); vs =
				 * ValidationState.FAILURE;
				 */
			} catch (IOException e) {
				Shrinker.myLogger.severe("Cannot create validator file: " + e.getMessage());
				vs = ValidationState.NOTVALIDATED;
			} catch (VMManagementException e) {
				Shrinker.myLogger.warning("Virtual machine access problems during validation");
				vs = ValidationState.NOTVALIDATED;
			} finally {
				validatorScript.delete();
			}
		} else {
			Shrinker.myLogger.info("Reusing previous validation results.");
			vs = ValidationState.SUCCESS;
		}
	}
}
