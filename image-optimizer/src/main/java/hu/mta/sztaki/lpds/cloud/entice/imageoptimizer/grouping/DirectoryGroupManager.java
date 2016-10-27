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

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;

import java.io.File;

public class DirectoryGroupManager extends GroupManager {

	// returns the Group with id groupRoot.toString(), creates it first if didn't exist (parent set as the parent directory of the file, moutpoint has not parent)  
	public Group getGroup(File groupRoot) {
		return getGroup(groupRoot.toString(), groupRoot.equals(Shrinker
				.getContext().getMountPoint()) ? null : getGroup(groupRoot
				.getParentFile()));
	}

	// adds this file to the group representing the file itself, and add this group as a child of the group corresponding to the parent directory  
	private void addToGroup(File group, File addition) {
		Group currGroup = getGroup(group); // create a group for the file itself
		currGroup.addToGroup(addition); // increments group size with file size
		if (!group.equals(Shrinker.getContext().getMountPoint())) {
			getGroup(group.getParentFile()).children.add(currGroup); // add this group to its parent
		}
	}

	@Override
	// create all groups (the fie itself, all parents), add the file to all these groups (itself, all parents) up to mount point
	public void addFile(File addition) {
		addToGroup(addition, addition); // create group for the container directory (if not yet exists) then add this file to this group
		File parentDir = addition;
		do {
			parentDir = parentDir.getParentFile();
			addToGroup(parentDir, addition); // add the file to all groups of all container directories up to mount point (recursively) 
		} while (!Shrinker.getContext().getMountPoint().equals(parentDir));
	}

}
