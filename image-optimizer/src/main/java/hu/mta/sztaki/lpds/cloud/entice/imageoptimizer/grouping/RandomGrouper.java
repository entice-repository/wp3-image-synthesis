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

import java.io.File;

public class RandomGrouper extends GroupManager {
	public static final double newGroupCreatorLimit = 0.1;
	public static final double parentCreatorLimit = 0.5;

	@Override
	public void addFile(File addition) {
		Group randomGroup = null;
		if (!unalterableGroupList.isEmpty()) {
			String[] gids = unalterableGroupList.keySet().toArray(new String[] {});
			do {
				randomGroup = getGroup(gids[(int) (Math.random() * gids.length)]);
				try {
					Double.parseDouble(randomGroup.groupid);
					break;
				} catch (NumberFormatException e) {
				}
			} while (true);
		}
		if (unalterableGroupList.isEmpty() || Math.random() < newGroupCreatorLimit) {
			String groupid = "" + Math.random();
			Group parentGroup = Math.random() < parentCreatorLimit ? randomGroup
					: null;
			randomGroup = getGroup(groupid, parentGroup);
			if (parentGroup != null) {
				parentGroup.children.add(randomGroup);
			}
		}
		Group basegroup = getGroup(addition.toString(), randomGroup);
		basegroup.addToGroup(addition);
		randomGroup.children.add(basegroup);
		while (randomGroup != null) {
			randomGroup.addToGroup(addition);
			randomGroup = randomGroup.parent;
		}
	}
}
