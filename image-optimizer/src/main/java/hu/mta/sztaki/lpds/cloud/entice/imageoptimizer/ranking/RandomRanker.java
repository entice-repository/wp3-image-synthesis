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

package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ranking;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.Group;

import java.util.HashMap;

public class RandomRanker extends Ranker {
	private class RankPair {
		long size;
		double rank;
	}

	private HashMap<Group, RankPair> assignedRanks = new HashMap<Group, RankPair>();

	@Override
	public double rank(Group g) {
		RankPair rp = assignedRanks.get(g);
		if (rp == null || rp.size != g.getSize()) {
			rp = new RankPair();
			rp.rank = Math.random();
			rp.size = g.getSize();
		}
		return rp.rank;
	}

}
