/*
 *    Copyright 2016 Akos Hajnal, MTA SZTAKI
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
package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.amazontarget;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStateChange;
import com.amazonaws.services.ec2.model.RebootInstancesRequest;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMManagementException;
import static hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VirtualMachine.VMState.*;

public class EC2VirtualMachine extends VirtualMachine {
	private static final int totalReqLimit = Integer
			.parseInt(System.getProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxUsableCPUs"));
	private static AtomicInteger reqCounter = new AtomicInteger();

	public static final String ACCESS_KEY = "accessKey";
	public static final String SECRET_KEY = "secretKey";
	public static final String ENDPOINT = "endpoint";
	public static final String INSTANCE_TYPE = "instanceType";
	
	private String accessKey;
	private String secretKey;
	private String endpoint = "http://cfe2.lpds.sztaki.hu:4567"; // default:
	
	private String state = null;
																	// cloud
	private String instanceType = "m1.small"; // default: small

	
	private AmazonEC2Client amazonEC2Client;
	private Reservation reservation;

	public static final int TERMINATE_TIMEOUT = 10 * 60 * 1000; // 10 mins in millis
	public static final String TERMINATED_STATE = "terminated";
	
	private String ip;
	
	/**
	 * @param vaid
	 *            Image id (e.g., "ami-00001082")
	 * @param parameters
	 *            endpoint, accessKey, secretKey, instanceType (optional)
	 * @param testConformance
	 *            true to do
	 */
	public EC2VirtualMachine(String vaid, Map<String, List<String>> parameters, boolean testConformance) {
		super(vaid, parameters, testConformance);
	}

	@Override
	protected void parseVMCreatorParameters(Map<String, List<String>> parameters) {
		super.datacollectorDelay = 2000; // 2 seconds delay between polls
		disableUnparseableDateWarning();
		if (parameters == null)
			throw new IllegalArgumentException("Missing parameters");
		if (!parameters.containsKey(ACCESS_KEY) || parameters.get(ACCESS_KEY) == null
				|| parameters.get(ACCESS_KEY).size() == 0 || parameters.get(ACCESS_KEY).get(0) == null)
			throw new IllegalArgumentException("Missing parameter: " + ACCESS_KEY);
		if (!parameters.containsKey(SECRET_KEY) || parameters.get(SECRET_KEY) == null
				|| parameters.get(SECRET_KEY).size() == 0 || parameters.get(SECRET_KEY).get(0) == null)
			throw new IllegalArgumentException("Missing parameter: " + SECRET_KEY);
		this.accessKey = parameters.get(ACCESS_KEY).get(0);
		this.secretKey = parameters.get(SECRET_KEY).get(0);
		if (parameters.containsKey(ENDPOINT) && parameters.get(ENDPOINT) != null && parameters.get(ENDPOINT).size() > 0)
			this.endpoint = parameters.get(ENDPOINT).get(0);
		if (parameters.containsKey(INSTANCE_TYPE) && parameters.get(INSTANCE_TYPE) != null
				&& parameters.get(INSTANCE_TYPE).size() > 0)
			this.instanceType = parameters.get(INSTANCE_TYPE).get(0);
		if (parameters.containsKey(LOGIN_NAME) && parameters.get(LOGIN_NAME) != null
				&& parameters.get(LOGIN_NAME).size() > 0)
			super.loginName = parameters.get(LOGIN_NAME).get(0);
		AWSCredentials awsCredentials = new BasicAWSCredentials(accessKey, secretKey);
		ClientConfiguration clientConfiguration = new ClientConfiguration();
		amazonEC2Client = new AmazonEC2Client(awsCredentials, clientConfiguration);
		amazonEC2Client.setEndpoint(endpoint);
	}

	private List<String> getInstanceIds() {
		List<String> result = new Vector<String>();
		if (reservation == null || reservation.getInstances() == null || reservation.getInstances().size() == 0)
			return result;
		for (Instance instance : reservation.getInstances())
			result.add(instance.getInstanceId());
		return result;
	}

	// suppress: WARNING: Unable to parse date '2016-06-09T09:47:30+02:00':
	private void disableUnparseableDateWarning() {
		try {
			// java.util.logging: set logger level to SEVERE
			Logger.getLogger(com.amazonaws.transform.SimpleTypeStaxUnmarshallers.class.getName())
					.setLevel(Level.SEVERE);
			// org.apache.commons.logging:
			// use:
			// -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog
		} catch (Throwable x) {
		} // ignore any exception
	}

	@Override
	// invoked from constructor
	protected String runInstance(String keyName) throws VMManagementException {
		try {
			Shrinker.myLogger.info("Trying to start instance (" + getImageId() + "/" + instanceType + "@" + endpoint + ")");
			int requests = reqCounter.incrementAndGet();
//			System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] VMs up: " + requests);
			if (requests > totalReqLimit) {
				Shrinker.myLogger.severe("Terminating shrinking process, too many non-terminated requests");
				Thread.dumpStack();
				System.exit(1);
			}
			
			RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
			runInstancesRequest.withImageId(getImageId()).withInstanceType(instanceType).withMinCount(1).withMaxCount(1);
			if (keyName != null) runInstancesRequest.withKeyName(keyName);
			RunInstancesResult runInstancesResult = this.amazonEC2Client.runInstances(runInstancesRequest);
			this.reservation = runInstancesResult.getReservation();

			List<String> instanceIds = getInstanceIds();
			if (instanceIds.size() != 1) throw new Exception("No or too many instances started");
			Shrinker.myLogger.info("Started instance (" + getImageId() + "/" + instanceType + "@" + endpoint + "): "+ getInstanceIds());
			
			VirtualMachine.vmsStarted.incrementAndGet();
			
			this.ip = null;
			this.setPrivateIP(null);
			System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] VM started: " + instanceIds.get(0) + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
			return instanceIds.get(0);
		} catch (AmazonServiceException x) {
			Shrinker.myLogger.info("runInstance error: " + x.getMessage());
			throw new VMManagementException("runInstance exception", x);
		} catch (AmazonClientException x) {
			Shrinker.myLogger.info("runInstance error: " + x.getMessage());
			throw new VMManagementException("runInstance exception", x);
		} catch (Exception x) {
			Shrinker.myLogger.info("runInstance error: " + x.getMessage());
			throw new VMManagementException("runInstance exception", x);
		}
	}

	@Override
	public void rebootInstance() throws VMManagementException {
		try {
			Shrinker.myLogger.info("Instance " + getInstanceId() + " received a reboot request");
			describeInstance(true);
			RebootInstancesRequest rebootInstancesRequest = new RebootInstancesRequest();
			rebootInstancesRequest.withInstanceIds(getInstanceIds());
			System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] VM reboot: " + getInstanceId() + " " + this.ip + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
			this.amazonEC2Client.rebootInstances(rebootInstancesRequest);
			Shrinker.myLogger.info("Reboot request dispatched for instance " + getInstanceId());
		} catch (AmazonServiceException x) {
			Shrinker.myLogger.info("rebootInstance error: " + x.getMessage());
			System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] reboot instance AmazonServiceExzeption: " + x.getMessage()); // don't print the word exception
			throw new VMManagementException("runInstance exception", x);
		} catch (AmazonClientException x) {
			Shrinker.myLogger.info("rebootInstance error: " + x.getMessage());
			System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] reboot instance AmazonClientExzeption: " + x.getMessage()); // don't print the word exception
			throw new VMManagementException("runInstance exception", x);
		}
	}

	@Override
	protected void terminateInstance() throws VMManagementException {
		try {
			int requests = reqCounter.decrementAndGet();
			if (requests < 0) {
				Shrinker.myLogger.severe("Terminating shrinking process, too much VM termination requests");
				Thread.dumpStack();
			}

			Shrinker.myLogger.info("Instance " + getInstanceId() + " received a terminate request");
			TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest();
			terminateInstancesRequest.withInstanceIds(getInstanceIds());
			TerminateInstancesResult res = this.amazonEC2Client.terminateInstances(terminateInstancesRequest);
			Shrinker.myLogger.info("Terminate request dispatched for instance " + getInstanceId());
			
			List<InstanceStateChange> stateChanges = res.getTerminatingInstances();
			InstanceStateChange state = null;
			for (InstanceStateChange stateChange: stateChanges) if (getInstanceId().contains(stateChange.getInstanceId())) state = stateChange; 
			if (state != null) Shrinker.myLogger.info("State of instance " + getInstanceId() + ": " + state.getPreviousState().getName() + " -> " + state.getCurrentState().getName());
			else Shrinker.myLogger.info("null state for instance " + getInstanceId());
			
			// re-send terminate
			if (state == null || !TERMINATED_STATE.equals(state.getCurrentState().getName())) {
				int timeout = 0;
				int counter = 1;
				while (timeout < TERMINATE_TIMEOUT) {
					try { Thread.sleep(5000); } catch (Exception x) {}
					Shrinker.myLogger.info("Re-sending (" + counter + "x) terminate request dispatched for instance " + getInstanceId());
					try {
						res = this.amazonEC2Client.terminateInstances(terminateInstancesRequest);
						stateChanges = res.getTerminatingInstances();
						for (InstanceStateChange stateChange: stateChanges) if (getInstanceId().contains(stateChange.getInstanceId())) state = stateChange; 
						if (state != null) Shrinker.myLogger.info("State of instance " + getInstanceId() + ": " + state.getPreviousState().getName() + " -> " + state.getCurrentState().getName());
						else Shrinker.myLogger.info("null state for instance " + getInstanceId());
					} catch (AmazonServiceException x) { // terminated correctly
						// it can happen that terminate seemingly didn't succeed for the first time (remains running), 
						// but then the instance id is gone (correctly) so re-sending terminate will cause exception
						if ("InvalidInstanceID.NotFound".equals(x.getErrorCode())) break;
						else throw x;
					}
					if (state != null && TERMINATED_STATE.equals(state.getCurrentState().getName())) break;
					timeout += 5000; // repeat every 5 second
					counter++;
				}
				
				if (timeout >= TERMINATE_TIMEOUT) {
					Shrinker.myLogger.info("ERROR: Cannot terminate instance: " + getInstanceId());
					System.exit(1);
				}
			}
			
		} catch (AmazonServiceException x) {
			Shrinker.myLogger.info("terminateInstance error: " + x.getMessage());
			throw new VMManagementException("terminateInstance exception", x);
		} catch (AmazonClientException x) {
			Shrinker.myLogger.info("terminateInstance error: " + x.getMessage());
			throw new VMManagementException("terminateInstance exception", x);
		}
		System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] VM terminated: " + getInstanceId() + " " + this.ip + " (@" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + ")");
	}

	@Override
	public String getInstanceId() {
		return this.getInstanceIds().size() > 0 ? this.getInstanceIds().get(0) : null;
	}

	@Override
	public String getIP() throws VMManagementException {
		refreshVMState();
		// pending, running, shutting-down, terminated, stopping, stopped
		if (this.state != null && !"pending".equals(this.state) && !"running".equals(this.state)) {
			throw new VMManagementException("VM failed, state: " + this.state, null);
		}
		String prevIP = this.ip;
		this.ip = super.getIP();
		if (this.ip == null || "".equals(this.ip)) this.ip = super.getPrivateIP();
		if (prevIP == null && this.ip != null) 
			System.out.println("[T" + (Thread.currentThread().getId() % 100) + "] VM " + getInstanceId() + " has IP " + this.ip);
		return this.ip;
	}

	@Override
	public String getPort() throws VMManagementException {
		refreshVMState();
		return super.getPort();
	}

	@Override
	public String getPrivateIP() throws VMManagementException {
		refreshVMState();
		return super.getPrivateIP();
	}

	private long lastrefresh = 0l;

	private void refreshVMState() throws VMManagementException {
		long currTime = System.currentTimeMillis();
		if (currTime - lastrefresh > super.datacollectorDelay) {
			Shrinker.myLogger.info("Describe instances request");
			lastrefresh = currTime;
			boolean isinInitialState = initializingStates.contains(getState());
			if (isinInitialState) {
				super.setIP(null);
				super.setPort(null);
				super.setPrivateIP(null);
			}
			describeInstance(false);
			if (super.getIP() != null && super.getPort() != null && super.getPrivateIP() != null && isinInitialState)
				super.setState(VMREADY);
		}
	}

	private void describeInstance(boolean verbose) throws VMManagementException {
		if (this.getInstanceId() == null) {
			Shrinker.myLogger.severe("null instance id");
			return;
		}
		try {
			DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
			DescribeInstancesResult describeInstancesResult = this.amazonEC2Client
					.describeInstances(describeInstancesRequest.withInstanceIds(this.getInstanceIds()));
			for (Reservation reservation : describeInstancesResult.getReservations()) {
				for (Instance instance : reservation.getInstances()) {
					if (getInstanceIds().contains(instance.getInstanceId())) {
						if (verbose) {
							Shrinker.myLogger.info("Instance " + instance.getInstanceId() + " is at state "
									+ instance.getState().getName());
						}
						
						this.state = instance.getState().getName();
						if ("running".equals(instance.getState().getName())) {
							// its me and I am in running state
							super.setIP(instance.getPublicDnsName());
							super.setPrivateIP(instance.getPrivateDnsName());
							super.setPort("22");
							break; // assuming only one such
						}
					}
				}
			}
		} catch (AmazonServiceException x) {
			Shrinker.myLogger.info("terminateInstance error: " + x.getMessage());
			throw new VMManagementException("terminateInstance exception", x);
		} catch (AmazonClientException x) {
			Shrinker.myLogger.info("terminateInstance error: " + x.getMessage());
			throw new VMManagementException("terminateInstance exception", x);
		}
	}
}
