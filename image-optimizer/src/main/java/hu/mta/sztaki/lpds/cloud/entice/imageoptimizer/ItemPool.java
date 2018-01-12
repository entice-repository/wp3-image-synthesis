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

package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker.ShrinkingContext;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.GroupManager;

import java.io.File;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

public class ItemPool {
	public static final HashMap<String, ItemPool> itemPools = new HashMap<String, ItemPool>();

	public static final int POOLSIZE = 300000;

	private final Vector<File> itemPool = new Vector<File>(POOLSIZE);
	private Itemizer itemSource;
	private boolean poolFull = false;
	private CopyOnWriteArraySet<GroupManager> groupmanagers = new CopyOnWriteArraySet<GroupManager>();
	private long overallSize = 0;

	private class PoolFiller extends Thread {
		public PoolFiller(ThreadGroup tg) {
			super(tg, "PoolFiller");
		}

		public void run() {
			Shrinker.myLogger.info("###phase: source file analysis");
//			System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] ItemPool: reading source file system");
			ShrinkingContext sc = Shrinker.getContext();
			while ((!itemSource.isProcessingCompleted() || !itemSource.itemQueue.isEmpty()) && sc.isRunning()) {
				if (itemPool.size() < POOLSIZE) {
					try {
						File currFile = itemSource.itemQueue.poll(Itemizer.ITEMIZATION_TIMEOUT, TimeUnit.SECONDS);
						if (currFile != null) {
							for (GroupManager g : groupmanagers) {
								g.addFile(currFile);
							}
							itemPool.add(currFile);
							overallSize += currFile.length();
						}
					} catch (InterruptedException e) {
					}
				} else {
					Shrinker.myLogger.info("PoolFilller thread filled the pool");
					poolFull = true;
					try {
						sleep(Itemizer.ITEMIZATION_TIMEOUT * 1000);
					} catch (InterruptedException e) {
					}
				}
			}
			Shrinker.myLogger.info("Poolfiller thread finished");
		}
	};

	private PoolFiller poolFiller = null;

	public long getOverallSize() {
		return overallSize;
	}

	public void removefromPool(File removable) {
		if (removable == null) return;
		poolFull = !itemPool.remove(removable);
		overallSize -= removable.length();
	}

	/*public List<File> getItemList() {
		return Collections.unmodifiableList(itemPool);
	}*/

	public void processItemSource(Itemizer itemSource, ThreadGroup tg) throws IllegalStateException {
		if (poolFiller != null && poolFiller.isAlive())
			throw new IllegalStateException(
					"Cannot add process new itemsource while another processing is in progress");
		Shrinker.myLogger.info("Poolfiller thread starts processing");
		this.itemSource = itemSource;
		poolFiller = new PoolFiller(tg);
		poolFiller.start();
	}

	public void addGroupManager(GroupManager gm) throws IllegalStateException {
		if (poolFiller != null && poolFiller.isAlive())
			throw new IllegalStateException("Cannot add new groupmanagers while processing the current itemsource");
		groupmanagers.add(gm);
	}

	public void removeGroupManager(GroupManager gm) {
		groupmanagers.remove(gm);
	}

	public static ItemPool getInstance() {
		String tgn = Thread.currentThread().getThreadGroup().getName();
		ItemPool currentPool = itemPools.get(tgn);
		if (currentPool == null) {
			currentPool = new ItemPool();
			itemPools.put(tgn, currentPool);
		}
		return currentPool;
	}

	public boolean isPoolFull() {
		return poolFull || (poolFiller != null && !poolFiller.isAlive());
	}
}
