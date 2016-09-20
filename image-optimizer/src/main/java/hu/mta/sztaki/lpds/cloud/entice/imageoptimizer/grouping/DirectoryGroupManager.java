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

	public Group getGroup(File groupRoot) {
		return getGroup(groupRoot.toString(), groupRoot.equals(Shrinker
				.getContext().getMountPoint()) ? null : getGroup(groupRoot
				.getParentFile()));
	}

	private void addToGroup(File group, File addition) {
		Group currGroup = getGroup(group);
		currGroup.addToGroup(addition);
		if (!group.equals(Shrinker.getContext().getMountPoint())) {
			getGroup(group.getParentFile()).children.add(currGroup);
		}
	}

	@Override
	public void addFile(File addition) {
		addToGroup(addition, addition);
		File parentDir = addition;
		do {
			parentDir = parentDir.getParentFile();
			addToGroup(parentDir, addition);
		} while (!Shrinker.getContext().getMountPoint().equals(parentDir));
	}

}
