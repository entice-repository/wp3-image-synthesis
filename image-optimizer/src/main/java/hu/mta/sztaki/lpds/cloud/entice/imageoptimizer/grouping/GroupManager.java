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

package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker.ShrinkingContext;

public abstract class GroupManager {

	final private TreeMap<String, Group> innerGroupList = new TreeMap<String, Group>();
	protected Map<String, Group> unalterableGroupList = Collections.unmodifiableMap(innerGroupList);
	private final String GMBlocker = "GMThreadBlocker";

	private long totalSize = 1;
	private long remainingSize = 1;
	private long removedGroups = 0;
	private long necessaryGroups = 0;
	private long nottestedGroups = 1;
	private long otherGroups = 0;

	private static final HashMap<ThreadGroup, GroupManager> groupers = new HashMap<ThreadGroup, GroupManager>();

	public static GroupManager getGrouperInstance() {
		for (ThreadGroup tg : groupers.keySet()) {
			if (tg.activeCount() == 0) {
				groupers.remove(tg);
			}
		}
		return groupers.get(Thread.currentThread().getThreadGroup());
	}

	public GroupManager() throws IllegalStateException {
		ThreadGroup tg = Thread.currentThread().getThreadGroup();
		if (groupers.get(tg) != null) {
			Shrinker.myLogger.severe("This threadgroup already has a grouper!");
		} else {
			groupers.put(tg, this);
		}
		Shrinker.myLogger.info(tg.getName() + " uses " + this.getClass().getName());
		new Thread(tg, "GroupCounter") {
			@Override
			public void run() {
				ShrinkingContext sc = Shrinker.getContext();
				while (nottestedGroups != 0 && sc.isRunning() && !Thread.currentThread().isInterrupted()) {
					try {
						evaluateStatistics();
						sleep(1000);
					} catch (InterruptedException e) {
						// Ignore
					}
				}
			}
		}.start();
	}

	public void evaluateStatistics() {
		if (innerGroupList.isEmpty())
			return;
		synchronized (GMBlocker) {
			long sum = 0;
			long sumRemain = 0;
			long succ = 0;
			long fail = 0;
			long not = 0;
			long other = 0;
			for (Group g : innerGroupList.values()) {
				Group.GroupState st = g.getGroupState();
				switch (st) {
				case REMOVAL_FAILURE:
					fail++;
					break;
				case REMOVAL_SUCCESS:
					succ++;
					break;
				case NOT_TESTED:
					not++;
					break;
				default:
					other++;
				}
				if (g.children.isEmpty()) {
					long currSize = g.getSize();
					sum += st.equals(Group.GroupState.REMOVAL_SUCCESS) ? 0 : currSize;
					sumRemain += Group.groupReadyForValidation.contains(st) ? currSize : 0;
				}
			}
			totalSize = sum;
			remainingSize = sumRemain;
			removedGroups = succ;
			necessaryGroups = fail;
			nottestedGroups = not;
			otherGroups = other;
		}
	}

	@Override
	public String toString() {
		synchronized (GMBlocker) {
			return "ST: " + totalSize + " SR:" + remainingSize + " GS:" + removedGroups + " GF:" + necessaryGroups
					+ " GU:" + nottestedGroups + " GO:" + otherGroups;
		}
	}

	public long getNecessaryGroups() {
		synchronized (GMBlocker) {
			return necessaryGroups;
		}
	}

	public long getNottestedGroups() {
		synchronized (GMBlocker) {
			return nottestedGroups;
		}
	}

	public long getOtherGroups() {
		synchronized (GMBlocker) {
			return otherGroups;
		}
	}

	public long getRemovedGroups() {
		synchronized (GMBlocker) {
			return removedGroups;
		}
	}

	public long getRemainingSize() {
		synchronized (GMBlocker) {
			return remainingSize;
		}
	}

	public long getTotalSize() {
		synchronized (GMBlocker) {
			return totalSize;
		}
	}

	public Group getGroup(String groupid) {
		return innerGroupList.get(groupid);
	}

	public Group getGroup(String groupid, Group parent) {
		Group currGroup = innerGroupList.get(groupid);
		if (currGroup == null) {
			currGroup = new Group(groupid, parent);
			synchronized (GMBlocker) {
				innerGroupList.put(groupid, currGroup);
			}
		}
		return currGroup;
	}

	/*
	 * return list of groups not in final state
	 */
	public List<Group> getGroups() {
		ArrayList<Group> al = new ArrayList<Group>();
		synchronized (GMBlocker) {
			for (Group g : innerGroupList.values()) {
				if (!g.isInFinalState())
					al.add(g);
			}
		}
		return al;
	}

	public void loadGroupStates() throws IOException {
		Shrinker.ShrinkingContext sc = Shrinker.getContext();
		Shrinker.myLogger.info("Loading group states from: " + sc.getGroupStatesFile());
		if (sc.getGroupStatesFile() == null) throw new IOException("Group states file is null");
//		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] trying to load group states from file: " + sc.getGroupStatesFile().getAbsolutePath() + "");
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(sc.getGroupStatesFile())));
		String line = null;
		while ((line = br.readLine()) != null) {
			String gid = Group.getGroupidFromSerialized(line);
			Group g = innerGroupList.get(gid);
			if (g != null) {
				g.setSerializedGroupState(line);
			} else {
				Shrinker.myLogger.severe("Tried to load groupstate for nonexistent group " + gid);
			}
		}
		br.close();
		Shrinker.myLogger.info("Group states successfully loaded from: " + sc.getGroupStatesFile());
//		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] group states loaded from file");
	}

	public void saveGroupStates() throws IOException {
		Shrinker.ShrinkingContext sc = Shrinker.getContext();
		File groupStatesFile = sc.getGroupStatesFile();
		Shrinker.myLogger.info("Saving group states to: " + groupStatesFile);
		if (sc.getGroupStatesFile() == null) throw new IOException("Group states file is null");
		groupStatesFile.delete();
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(groupStatesFile)));
		synchronized (GMBlocker) {
			for (Group g : innerGroupList.values()) {
				bw.write(g.toString() + "\n");
			}
		}
		bw.close();
		Shrinker.myLogger.info("Group states successfully saved to: " + groupStatesFile);
	}

	public abstract void addFile(File addition);
}
