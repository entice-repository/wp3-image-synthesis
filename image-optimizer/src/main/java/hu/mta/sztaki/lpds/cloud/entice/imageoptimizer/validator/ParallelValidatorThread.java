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

import javax.ws.rs.core.MediaType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.Group;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.Group.GroupState;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ranking.Ranker;

public class ParallelValidatorThread extends Thread {
	public static final int parallelVMs;
	public static final String threadPrefix = "ParallelValidator";
	public static final String REMOVABLES_HISTORY_URL = "REMOVABLES_HISTORY_URL";
	public static final int REMOVABLES_HISTORY_LIMIT = 0; // 0: no limit, query stats of all removables, x: query only the first x best removables
	public static final String REMOVABLES_HISTORY_QUERY_POSTFIX = "/history";
	public static final String REMOVABLES_HISTORY_SUCCESS_POSTFIX = "/success";
	public static final String REMOVABLES_HISTORY_FAILURE_POSTFIX = "/failure";

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
		if (groups != null && groups.size() > 0) {
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

			// query previous trials for groups and alter ranks accordingly
			queryKBHistoryAndAlterWeights(groups);
			
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
			// start single validator threads for each Group in removables list 
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
						// report failure for this group to KB
						reportRemovableStatusToKB(failedGroup.getId(), false);
					}
					if (!removables.remove(failedGroup)) {
						Shrinker.myLogger.severe("Could not remove failed group.");
					}

				} else {
					// report success for this group to KB
					if (validator.removables != null && validator.removables.size() > 0)
						reportRemovableStatusToKB(validator.removables.get(0).getId(), true);
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
	
	private void reportRemovableStatusToKB(String path, boolean status) {
		if (path == null || "".equals(path)) return;
		String queryUrl = getSystemProperty(REMOVABLES_HISTORY_URL, null);
		if (queryUrl == null) return; // Shrinker.myLogger.info("Removable status not reported. Environment variable is not defined: " + REMOVABLES_HISTORY_URL);
		Client client = null;
		try {
			client = Client.create();
			WebResource webResource = client.resource(queryUrl + (status ? REMOVABLES_HISTORY_SUCCESS_POSTFIX : REMOVABLES_HISTORY_FAILURE_POSTFIX));
			ClientResponse response = webResource
					.type(MediaType.TEXT_PLAIN_TYPE)
					.post(ClientResponse.class, path);
			if (response.getStatus() != 200) {
				Shrinker.myLogger.severe(queryUrl + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
			} 
		} catch (ClientHandlerException x) { // thrown at get
			Shrinker.myLogger.severe("KB API exzeption");
		} finally {
			if (client != null) client.destroy();
		}
	}
	
	private void queryKBHistoryAndAlterWeights(List<Group> groups) {
		if (groups == null || groups.size() == 0) return;
		String queryUrl = getSystemProperty(REMOVABLES_HISTORY_URL, null);
		if (queryUrl == null) {
			Shrinker.myLogger.info("Removables history not queried. Environment variable is not defined: " + REMOVABLES_HISTORY_URL);
			return;
		}
		
		@SuppressWarnings("unused")
		final int limit = REMOVABLES_HISTORY_LIMIT > 0 ? Math.min(REMOVABLES_HISTORY_LIMIT, groups.size()) : groups.size(); // set limit if not to send all groups, or send all
		
		JSONArray queryList = new JSONArray();
		for (int i = 0; i < Math.min(groups.size(),  limit); i++) {
			queryList.put(groups.get(i).getId());
		}
		Shrinker.myLogger.info("Paths to query: " + queryList.toString());
		
		Client client = null;
		try {
			client = Client.create();
			WebResource webResource = client.resource(queryUrl + REMOVABLES_HISTORY_QUERY_POSTFIX);
			ClientResponse response = webResource
					.type(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class, queryList.toString());
			if (response.getStatus() != 200) {
				Shrinker.myLogger.severe(queryUrl + " returned HTTP error code: " + response.getStatus() + " " + response.getEntity(String.class));
			} else {
				String responseString = response.getEntity(String.class);
				JSONObject responseJSON = null;
		    	try { 
		    		responseJSON = new JSONObject(new JSONTokener(responseString));
		    		// response is like: {"path1": [3,4], "path2":[0,0], ...} where 3 is successful deletions, 4 failed deletions
		    		for (int i = 0; i < Math.min(groups.size(),  limit); i++) {
		    			Group g = groups.get(i);
		    			String path = g.getId();
		    			try {
		    				JSONArray trials = responseJSON.getJSONArray(path);
		    				if (trials.length() == 2) {
		    					try {
		    						int successes = trials.getInt(0);
		    						int failures = trials.getInt(1);
		    						g.setWeight(getWeigth(successes, failures));
		    					} catch (JSONException x) {
		    						Shrinker.myLogger.severe("Invalid JSON: value must be int array [2]");
		    						break;
		    					}
		    				} else {
		    					Shrinker.myLogger.severe("Invalid JSON: value must be int array [2]");
		    					break;
		    				}
		    			} catch (JSONException x) {
		    				// key not found == no remove attempts so far
		    			}
		    		}
		    	} catch (JSONException e) { 
		    		Shrinker.myLogger.severe("Invalid JSON: " + e.getMessage());
		    	}
			}
		} catch (ClientHandlerException x) { // thrown at get
			Shrinker.myLogger.severe("KB API exzeption");
		} finally {
			if (client != null) client.destroy();
		}
	}
	float getWeigth(int successes, int failures) {
		float weight;
		float defaultValue = 1.0f;
		if (failures == successes) {
			weight = defaultValue;
		} else if (failures > successes) {
			// more failures
			int delta = failures - successes + 1; 
			if (delta > 10) delta = 10; // delta in [2, 3, ..., 10]
			weight = defaultValue / delta; // decrease weight: 0.5, 0.3, ..., 0.1
		} else {
			// more successes
			int delta = successes - failures;
			if (delta > 9) delta = 9; // delta in [1, 2, ..., 9]
			weight = defaultValue + delta; // increase weight: 2.0, 3.0, ..., 10.0 
		} 
		return weight;
	}
	
	private static String getSystemProperty(String propertyName, String defaultValue) {
		return System.getProperty(propertyName) != null ? System.getProperty(propertyName) : 
			(System.getenv(propertyName) != null ? System.getenv(propertyName) : defaultValue); 
	}
}
