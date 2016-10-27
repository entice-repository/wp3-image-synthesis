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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.Vector;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.Group;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.Group.GroupState;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ranking.Ranker;

public class ParallelValidatorThread extends Thread {
	public static final int parallelVMs;
	public static final String threadPrefix = "ParallelValidator";

	private static TreeMap<String, Vector<ParallelValidatorThread>> parallelvalidators = new TreeMap<String, Vector<ParallelValidatorThread>>();

	private ParallelValidatorThread newthread = null;
	private final List<Group> availableGroups;
	private List<Group> unprocessedRemovables = null;

	static {
		String pvmNum = System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.validator.ParallelVMNum");
		int localpvmval = 128;
		try {
			localpvmval = Integer.parseInt(pvmNum);
		} catch (Exception ex) {
			// Numberformat && NullPointer
			Shrinker.myLogger.info("Using the default number of Parallel VMs:");
		}
		parallelVMs = localpvmval;
		Shrinker.myLogger.info("Number of parallel VMs used: " + parallelVMs);
	}

	public ParallelValidatorThread(List<Group> availableGroups) {
		super(currentThread().getThreadGroup(), threadPrefix + Math.random());
		this.availableGroups = availableGroups;
	}

	public void setNewThread(ParallelValidatorThread p) {
		newthread = p;
	}

	// purge available groups: keep top directories and files having no parent group 
	@SuppressWarnings("unused")
	private void purge() {
		boolean mod;
		do {
			mod = false;
			for (Group g : availableGroups) {
				if (purgeChildrenGroups(g)) {
					mod = true;
					break;
				}
			}
		} while (mod);
	}
	
	private boolean purgeChildrenGroups(Group g) {
		boolean ret = false;
		for (Group child : g.children) {
			ret |= purgeChildrenGroups(child);
			ret |= availableGroups.remove(child);
		}
		return ret;
	}

	@Override
	public void run() {
		String tgname = getThreadGroup().getName();
		Vector<ParallelValidatorThread> siblings = null;
		
		synchronized (parallelvalidators) {
			siblings = parallelvalidators.get(tgname);
			if (siblings == null) {
				siblings = new Vector<ParallelValidatorThread>();
				parallelvalidators.put(tgname, siblings);
			} else {
				if (!siblings.isEmpty()) {
					if (siblings.size() == 1) {
						ParallelValidatorThread other = siblings.get(0);
						for (Group g : other.unprocessedRemovables) {
							availableGroups.remove(g);
						}
					} else {
						Shrinker.myLogger.severe("More than two threads were instantiated for parallel validation!");
					}
				}
			}
			siblings.add(this);
		}
		List<Group> groups = availableGroups; // copy of the list of groups not in final state
		if (groups.size() > 0) {
			Shrinker.myLogger.info("Parallel validation started!");
			Shrinker.myLogger.info("Number of available groups before purge: " + groups.size());
			
//			for (Group g: groups) g.print();
			
			System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] ParallelValidatorThread: purging initial groups: " + groups.size() + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");

			// purge: keeps top-level groups only (delete all others having children)
			// purge();
			boolean mod;
			do {
				mod = false;
				for (Group g : groups) {
					if (purgeChildrenGroups(g)) {
						mod = true;
						break;
					}
				}
			} while (mod);
			
			Shrinker.myLogger.info("Number of available groups after purge: " + groups.size());
			System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] ParallelValidatorThread: number of groups after purge: " + groups.size() + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");

			Comparator<Group> comp = new Comparator<Group>() {
				public int compare(Group o1, Group o2) {
					Ranker r = Ranker.getRankerInstance();
					return (int) Math.signum(r.rank(o2) - r.rank(o1));
				}
			};
			Collections.sort(groups, comp);
			List<Group> removables = groups.subList(0, groups.size() > parallelVMs ? parallelVMs : groups.size());
			Shrinker.myLogger.info(removables.toString());
			
			System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] ParallelValidatorThread: groups to remove:");
			for (Group i: removables) {
				System.out.println("  " + i.getId() + " (" + (i.children.isEmpty() ? "F" : "D") + ") " + i.getSize() + " bytes");
			}
			
			unprocessedRemovables = removables;
			ArrayList<SingleValidatorThread> validators = new ArrayList<SingleValidatorThread>();
			for (Group removable : removables) {
				validators.add(new SingleValidatorThread(getThreadGroup(), Collections.singletonList(removable)));
			}
			for (SingleValidatorThread validator : validators) {
				try {
					validator.join();
				} catch (InterruptedException ex) {
				}
			}
			Shrinker.myLogger.info("***************************All validation threads have finished");
			System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] ParallelValidatorThread: all SingleValidatorThreads finished");
			for (SingleValidatorThread validator : validators) {
				SingleValidatorThread.ValidationState currentState = validator.getValidationState();
				if (!currentState.equals(SingleValidatorThread.ValidationState.SUCCESS)) {
					Group failedGroup = validator.removables.get(0);
					if (SingleValidatorThread.ValidationState.FAILURE.equals(currentState)) {
						failedGroup.setTestState(Group.GroupState.REMOVAL_FAILURE);
					}
					if (!removables.remove(failedGroup)) {
						Shrinker.myLogger.severe("Could not remove failed group.");
					}

				}
			}
			if (newthread != null) {
				newthread.start();
			}
			if (removables.size() > 1) {
				SingleValidatorThread.ValidationState valstate;
				do {
					Shrinker.myLogger.info("Final validation required");
			
					StringBuilder sb = new StringBuilder();
					for (Group g : removables) sb.append(g.getId() + " ");
					String removablesString  = sb.toString();

					System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] ParallelValidatorThread: performing combined validation on: " + removablesString);
					SingleValidatorThread finalValidator = new SingleValidatorThread(getThreadGroup(), removables);
					try {
						finalValidator.join();
					} catch (InterruptedException ex) {
					}
					Shrinker.myLogger.info("Final validation finished");
					valstate = finalValidator.getValidationState();
				} while (SingleValidatorThread.ValidationState.NOTVALIDATED.equals(valstate));
				if (!SingleValidatorThread.ValidationState.SUCCESS.equals(valstate)) {
					for (Group g : removables) {
						g.setTestState(GroupState.FINAL_VALIDATION_FAILURE);
					}
					Shrinker.myLogger
							.info("Parallel validation failed, using the highest ranked successful group, dropping "
									+ (removables.size() - 1));
					removables = Collections.singletonList(removables.get(0));
					// SingleValidatorThread svt = new SingleValidatorThread(
					// getThreadGroup(), removables);
					// try {
					// svt.join();
					// } catch (InterruptedException e) {
					// // TODO Auto-generated catch block
					// e.printStackTrace();
					// }
					// if (!svt.getValidationState().equals(
					// ValidationState.SUCCESS)) {
					// Shrinker.myLogger
					// .severe("Not successfull second validation!!!");
					// removables = Collections.emptyList();
					// }
				}
			}
			if (removables.size() > 0) {
				for (Group g : removables) {
					try {
						RandomAccessFile raf = new RandomAccessFile(Shrinker.removeScript, "rwd");
						raf.seek(raf.length());
						raf.writeBytes(g.genRemover());
						raf.close();
					} catch (IOException e) {
						Shrinker.myLogger.severe("Failed to update the remover script: " + e.getMessage());
					}
					g.setTestState(Group.GroupState.REMOVAL_SUCCESS);
					Shrinker.myLogger.info("###removable: " + g.getId());

				}
			}
		}
		synchronized (parallelvalidators) {
			siblings.remove(this);
			if (siblings.isEmpty()) {
				parallelvalidators.remove(tgname);
			}
		}
	}
}
