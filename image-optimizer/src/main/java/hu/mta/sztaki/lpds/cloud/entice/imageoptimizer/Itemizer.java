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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker.ShrinkingContext;

public class Itemizer {

	public static final int ITEMIZATION_TIMEOUT = 5;
	// TODO: the item pool and queue could be connected
	public final ArrayBlockingQueue<File> itemQueue = new ArrayBlockingQueue<File>(500);

	private File processingState;
	private boolean stopprocessing = false;
//	private int offercounter = 0;

	private class ItemizingThread extends Thread {
		public ItemizingThread(ThreadGroup tg) {
			super(tg, "ItemizingThread");
		}

		public void run() {
//			System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Itemizer thread started (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
			ShrinkingContext sc = Shrinker.getContext();
			while (!stopprocessing && processFiles() && sc.isRunning()) {
				try {
					sleep(ITEMIZATION_TIMEOUT * 1000);
				} catch (InterruptedException e) {
				}
			}
			Shrinker.myLogger.info("Itemizer thread finished");
//			System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] Itemizer thread ended (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
		}
	}

	private final ItemizingThread processingThread;

	public Itemizer(ThreadGroup tg) {
		Shrinker.myLogger.finest("Itemizer creation");
		processingThread = new ItemizingThread(tg);
		Shrinker.myLogger.finest("Thread created");
		ShrinkingContext sc = Shrinker.getContext();
		Shrinker.myLogger.finest("Acquired context: " + sc);
		File mp = sc.getMountPoint();
		Shrinker.myLogger.finest("Acquired mount point: " + mp);
		File[] list = mp.listFiles();
		Shrinker.myLogger.finest("Received list: " + list);
		if (list != null && list.length > 0) processingState = list[0]; // processingState left null - when no files or subdirectories under mountpoint
		else Shrinker.myLogger.warning("No files or subdirectories found at mount point");
		Shrinker.myLogger.finest("Starting point set as " + processingState);
		processingThread.start();
		Shrinker.myLogger.finest("Itemizer thread started");
	}

	public boolean isProcessingCompleted() {
		return processingState == null || stopprocessing;
	}

	public File stopProcessing() {
		stopprocessing = true;
		while (processingThread.isAlive()) {
			try {
				Thread.sleep(ITEMIZATION_TIMEOUT * 10);
			} catch (InterruptedException e) {
			}
		}
		return processingState;
	}

	public File basicProcessFiles(File processFile) {
		try {
			if (!processFile.getCanonicalPath().equals(processFile.getAbsolutePath()))
				return null; // skip symbolic links
			
			if (processFile.isDirectory()) {
				for (File curr : processFile.listFiles()) {
					File nextFiletoProcess = basicProcessFiles(curr);
					if (nextFiletoProcess != null) {
						return nextFiletoProcess;
					}
				}
			} else {
				if (!itemQueue.offer(processFile)) {
					return processFile;
				} else {
//					offercounter++;
				}
			}
		} catch (IOException e) {
			Shrinker.myLogger.warning("Cannot resolve canonical path for: " + processFile);
		}
		return null;
	}

	private File findNextUnprocessed(File lastProcessed) {
		ShrinkingContext sc = Shrinker.getContext();
		if (lastProcessed.equals(sc.getMountPoint()))
			return null;
		File parentDir = lastProcessed.getParentFile();
		File[] filelist = parentDir.listFiles();
		int nextIndex = Arrays.asList(filelist).indexOf(lastProcessed) + 1;
		if (nextIndex == filelist.length) {
			if (parentDir.equals(sc.getMountPoint()))
				return null;
			return findNextUnprocessed(parentDir);
		}
		return filelist[nextIndex];
	}

	private File processFilesFrom(File nextFiletoProcess) {
		File parentDir = nextFiletoProcess.getParentFile();
		boolean tailing = false;
		for (File currentFile : parentDir.listFiles()) {
			tailing |= currentFile.equals(nextFiletoProcess);
			if (tailing) {
				File newnext = basicProcessFiles(currentFile);
				if (newnext != null)
					return newnext;
			}
		}
		File furtherProcessing = findNextUnprocessed(parentDir);
		if (furtherProcessing != null) {
			return processFilesFrom(furtherProcessing);
		}
		return null;
	}

	public boolean processFiles() {
		if (processingState != null)
			processingState = processFilesFrom(processingState);
		return processingState != null;
	}
}
