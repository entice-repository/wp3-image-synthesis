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

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.Group;

import java.util.HashMap;

public abstract class Ranker {
	private static final HashMap<ThreadGroup, Ranker> rankers = new HashMap<ThreadGroup, Ranker>();

	public static Ranker getRankerInstance() {
		for (ThreadGroup tg : rankers.keySet()) {
			if (tg.activeCount() == 0) {
				rankers.remove(tg);
			}
		}
		return rankers.get(Thread.currentThread().getThreadGroup());
	}

	protected Ranker() throws IllegalStateException {
		ThreadGroup tg = Thread.currentThread().getThreadGroup();
		if (rankers.get(tg) != null) {
			Shrinker.myLogger.severe("This threadgroup already has a ranker!");
		} else {
			rankers.put(tg, this);
		}
		Shrinker.myLogger.info(tg.getName() + " uses "
				+ this.getClass().getName());
	}

	public abstract double rank(Group g);
}
