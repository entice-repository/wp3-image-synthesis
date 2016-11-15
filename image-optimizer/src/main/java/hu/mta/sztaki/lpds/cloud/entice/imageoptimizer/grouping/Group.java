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

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ItemPool;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ranking.Ranker;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

public class Group {
	public static enum GroupState {
		NOT_TESTED, TEST_NOT_NECESSARY, REMOVAL_SUCCESS, FINAL_VALIDATION_FAILURE, CORE_GROUP, REMOVAL_FAILURE
	};

	public static final EnumSet<GroupState> groupRemovalPossible = EnumSet.of(
			GroupState.REMOVAL_SUCCESS, GroupState.TEST_NOT_NECESSARY);
	public static final EnumSet<GroupState> groupReadyForValidation = EnumSet
			.of(GroupState.NOT_TESTED, GroupState.FINAL_VALIDATION_FAILURE);
	public final String groupid;
	public final Group parent;
	public final Set<Group> children = new HashSet<Group>();
	private List<File> groupped = new Vector<File>();
	private long size = 0;
	private GroupState state = GroupState.NOT_TESTED;

	public void print() {
		System.out.println("GROUP: " + groupid);
		System.out.println("  size: " + size);
		System.out.println("  state: " + state);
		if (Ranker.getRankerInstance() != null)	System.out.println("  rank: " + Ranker.getRankerInstance().rank(this));
		System.out.print("  children: "); for (Group child: children) System.out.print(child.groupid + " ");
		System.out.println();
		System.out.println();
	}
	
	public GroupState getGroupState() {
		return state;
	}

	public boolean isInFinalState() {
		return !groupReadyForValidation.contains(state);
	}

	public void setSerializedGroupState(String state) {
		String[] stateinfo = state.split(" ");
		String unparsedstate = stateinfo[stateinfo.length - 1];
		setTestState(GroupState.valueOf(unparsedstate.substring(3)));
		if (state.equals(GroupState.REMOVAL_SUCCESS)) {
			try {
				RandomAccessFile raf = new RandomAccessFile(
						Shrinker.removeScript, "rwd");
				raf.seek(raf.length());
				raf.writeBytes(genRemover());
				raf.close();
			} catch (IOException e) {
				Shrinker.myLogger.severe("Failed to updte the remover script: "
						+ e.getMessage());
			}
		}
	}

	public static String getGroupidFromSerialized(String serialized) {
		String returner = serialized.split(":")[0];
		return returner.substring(0, returner.length() - 4);
	}

	public void setTestState(GroupState newState) {
		Shrinker.myLogger.info("Group: '" + groupid + "' Statechange from "
				+ state + " to " + newState);
		if (isInFinalState()) {
			Shrinker.myLogger.warning("Already in final state.");
			return;
		}
		switch (newState) {
			case REMOVAL_SUCCESS:
			case TEST_NOT_NECESSARY:
				if (children.size() > 0) {
					for (Group child : children) {
						child.setTestState(newState);
					}
				} else {
					if (groupped.size() != 1)
						throw new IllegalStateException(
								"Childless group has more than a single file!");
					removeFromGroup(groupped.get(0));
				}
				break;
			case CORE_GROUP:
			case REMOVAL_FAILURE:
				if (parent != null && !parent.isInFinalState()) {
					parent.setTestState(newState);
				}
			default:
				break;
		}
		state = newState;
	}

	public Group(String id, Group p) {
		groupid = id;
		parent = p;
	}

	public long getSize() {
		return size;
	}

	public void addToGroup(File f) {
		if (!GroupState.CORE_GROUP.equals(state)) {
			state = GroupState.NOT_TESTED;
		}
		groupped.add(f);
		size += f.length();
	}

	private void removeFromGroup(File f) {
		if (children.isEmpty()) {
			ItemPool.getInstance().removefromPool(f);
		}
		if (parent != null) {
			parent.removeFromGroup(f);
		}
		groupped.remove(f);
		size -= f.length();
	}

	public List<File> getList() {
		return Collections.unmodifiableList(groupped);
	}

	public String genRemover() {
		String touchtest = "\n touch /tmp/rmtestfile ; rm /tmp/rmtestfile || { exec 1>&5 2>&6 ; echo PROBLEMATIC_REMLIST: \"$REMLIST\" ; exit 248 ; }";
		String remlistStr="\n REMLIST=`echo ";
		String rmStr="`\n rm -r";
		StringBuilder sb = new StringBuilder();
		StringBuilder remlist = new StringBuilder();
		int newlinemarker = 0;
		for (File f : groupped) {
			if (newlinemarker % 10 == 0) {
				sb.append(touchtest);
				if (newlinemarker != 0) {
					sb.append(remlistStr);
					sb.append(remlist);
					sb.append(rmStr);
					sb.append(remlist);
					remlist = new StringBuilder();
				}
			}
			remlist.append(" ");
			remlist.append("$ROOT'"+f.toString().substring(
					Shrinker.getContext().getMountPoint().toString().length()+1));
			remlist.append("'");
			newlinemarker++;
		}
		sb.append(remlistStr);
		sb.append(remlist);
		sb.append(rmStr);
		sb.append(remlist);
		sb.append(touchtest);
		return sb.toString();
	}

	@Override
	public String toString() {
		return groupid + " (" + (children.isEmpty() ? "F" : "D") + "): S-"
				+ size + " R-" + (Ranker.getRankerInstance() != null ? Ranker.getRankerInstance().rank(this) : "?") + " ST-"
				+ state;
	}
	
	public String getId() { return groupid; }
	public String getState() { return state.name(); }
}
