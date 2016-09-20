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
package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ec2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AttachVolumeRequest;
import com.amazonaws.services.ec2.model.AttachVolumeResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DetachVolumeRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStateChange;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.ec2.model.VolumeAttachment;

public class VM {

	private static final Logger log = LoggerFactory.getLogger(VM.class);

	public static final String ACCESS_KEY = "accessKey";
	public static final String SECRET_KEY = "secretKey";
	public static final String ENDPOINT = "endpoint";
	public static final String INSTANCE_TYPE = "instanceType";

	private final String accessKey;
	private final String secretKey;
	private final String endpoint;   
	private final String instanceType; 
	private final String imageId;
	
	public static final String UNKNOWN = "unknown";
	public static final String PENDING = "pending";
	public static final String BOOTING = "booting";
	public static final String RUNNING = "running";
	public static final String SHUTDOWN = "shutting-down";
	public static final String STOPPING = "stopping";
	public static final String STOPPED = "stopped";
	public static final String TERMINATED = "terminated";
	public static final int REPEAT_TERMINATE = 3; // retry terminate 3 times
	public static final int REPEAT_TERMINATE_DELAY = 10000; // 10 seconds
	
	private AmazonEC2Client amazonEC2Client;
//	private Reservation reservation;
	private String instanceId = null;
	public String getInstanceId() {	return instanceId; }
	public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

	public String publicDnsName = null;
	public String privateDnsName = null;
	public String status = UNKNOWN;
	
	static {
		try {
			// suppress: WARNING: Unable to parse date '2016-06-09T09:47:30+02:00':  Unparseable date: "2016-06-09T09:47:30+02:00"
			java.util.logging.Logger.getLogger(com.amazonaws.transform.SimpleTypeStaxUnmarshallers.class.getName()).setLevel(java.util.logging.Level.SEVERE); // java.util.logging
			// -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog // org.apache.commons.logging 
		} catch (Throwable x) {} // ignore any exception
	}

	public VM(String endpoint, String accessKey, String secretKey, String instanceType, String imageId, String keyPairName) throws Exception {
		this.endpoint = endpoint;
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		this.instanceType = instanceType;
		this.imageId = imageId; 
				
		AWSCredentials awsCredentials = new BasicAWSCredentials(this.accessKey, this.secretKey);
		ClientConfiguration clientConfiguration = new ClientConfiguration();
		amazonEC2Client = new AmazonEC2Client(awsCredentials, clientConfiguration);
		amazonEC2Client.setEndpoint(this.endpoint);
	}

	// just to reconnect to a VM, not for starting it (run throws exception)
	public VM(String endpoint, String accessKey, String secretKey, String instanceId) throws Exception {
		this.endpoint = endpoint;
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		this.instanceId = instanceId;
		this.instanceType = null;
		this.imageId = null; 
				
		AWSCredentials awsCredentials = new BasicAWSCredentials(this.accessKey, this.secretKey);
		ClientConfiguration clientConfiguration = new ClientConfiguration();
		amazonEC2Client = new AmazonEC2Client(awsCredentials, clientConfiguration);
		amazonEC2Client.setEndpoint(this.endpoint);
//		describeInstance(); // update IPs
	}
	
	public void run() throws Exception {
		run(null, null);
	}
	
	public void run(String keyPairName, String userDataBase64) throws Exception {
		if (this.imageId == null) throw new Exception("VM.run exception: no imageId provided");
		try {
			if (this.instanceId == null) {
				log.debug(this.imageId + " " + this.instanceType + " " + keyPairName);
				RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
				runInstancesRequest.withImageId(this.imageId).withInstanceType(this.instanceType).withMinCount(1).withMaxCount(1);
				if (keyPairName != null) runInstancesRequest.withKeyName(keyPairName);
				if (userDataBase64 != null) runInstancesRequest.withUserData(userDataBase64);
				RunInstancesResult runInstancesResult = this.amazonEC2Client.runInstances(runInstancesRequest);
				Reservation reservation = runInstancesResult.getReservation();
				List<Instance> instances = reservation.getInstances();
				if (instances.size() != 1) throw new Exception("Too few or too many instances started");
				this.instanceId = instances.get(0).getInstanceId();
			}
		} catch (AmazonServiceException x) {
			x.printStackTrace();
			throw new Exception("VM.run exception: " + x.getMessage() + " " + x.getCause(), x);
		} catch (AmazonClientException x) {
			x.printStackTrace();
			throw new Exception("VM.run exception: " + x.getMessage() + " " + x.getCause(), x);
		} catch (Exception x) {
			x.printStackTrace();
			throw new Exception("VM.run exception: " + x.getMessage() + " " + x.getCause(), x);
		}
	}

	public void describeInstance() throws Exception {
		if (this.instanceId == null) throw new Exception("describeInstance without instanceId");
		try {
			boolean found = false;
			DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
			DescribeInstancesResult describeInstancesResult = this.amazonEC2Client.describeInstances(describeInstancesRequest.withInstanceIds(this.instanceId));
			for (Reservation reservation : describeInstancesResult.getReservations()) {
				for (Instance instance : reservation.getInstances()) {
					if (this.instanceId.equals(instance.getInstanceId())) {
						this.status = instance.getState().getName();
						if (RUNNING.equals(this.status)) {
							this.publicDnsName = instance.getPublicDnsName();
							this.privateDnsName = instance.getPrivateDnsName();
							found = true;
							break; // assuming one such
						}
					}
				}
			}
			if (!found) {
				log.debug("Instance " + this.instanceId + " not found"); 
				// if queried too fast VM is not yet found, leave its state unchanged
			}
		} catch (AmazonServiceException x) {
			log.warn("describeInstance error: " + x.getMessage());
			throw new Exception(x);
		} catch (AmazonClientException x) {
			log.warn("describeInstance error: " + x.getMessage());
			throw new Exception(x);
		}
	}
	
	public void terminate() throws Exception {
		try {
			TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest();
			terminateInstancesRequest.withInstanceIds(this.instanceId);
			TerminateInstancesResult terminateInstancesResult = this.amazonEC2Client.terminateInstances(terminateInstancesRequest);
			
			List<InstanceStateChange> instanceStateChanges = terminateInstancesResult.getTerminatingInstances();
			
			InstanceStateChange instanceStateChange = null;
			for (InstanceStateChange stateChange: instanceStateChanges) {
				if (this.instanceId.equals(stateChange.getInstanceId())) instanceStateChange = stateChange;
				break;
			}
			if (instanceStateChange != null) {
				log.debug("State change of instance " + this.instanceId + ": " + instanceStateChange.getPreviousState().getName() + " -> " + instanceStateChange.getCurrentState().getName());
				this.status = instanceStateChange.getCurrentState().getName();
			} else log.warn("null state change for instance " + this.instanceId);
			
			// resend terminate if needed
			if (instanceStateChange == null || !TERMINATED.equals(instanceStateChange.getCurrentState().getName())) {
				int counter = 1;
				while (counter <= REPEAT_TERMINATE) {
					try { Thread.sleep(REPEAT_TERMINATE_DELAY); } catch (Exception x) {} 
					log.debug("Re-sending terminate request to instance " + getInstanceId() + " (" + counter + "x)");
					terminateInstancesResult = this.amazonEC2Client.terminateInstances(terminateInstancesRequest);
					instanceStateChanges = terminateInstancesResult.getTerminatingInstances();
					for (InstanceStateChange stateChange: instanceStateChanges) {
						if (this.instanceId.equals(stateChange.getInstanceId())) instanceStateChange = stateChange;
						break;
					}
					if (instanceStateChange != null) {
						log.debug("State change of instance " + this.instanceId + ": " + instanceStateChange.getPreviousState().getName() + " -> " + instanceStateChange.getCurrentState().getName());
						this.status = instanceStateChange.getCurrentState().getName();
					} else log.warn("null state change for instance " + this.instanceId);
					if (instanceStateChange != null && TERMINATED.equals(instanceStateChange.getCurrentState().getName())) break;
					counter++;
				}
				if (counter == REPEAT_TERMINATE) log.warn("ERROR: Could not terminate instance in time: " + getInstanceId());
			}
			
			this.discard();
		} catch (AmazonServiceException x) {
			throw new Exception("VM.terminate exception", x);
		} catch (AmazonClientException x) {
			throw new Exception("VM.terminate exception", x);
		}
	}

	public void discard() {
		this.instanceId = null;
		this.publicDnsName = null;
		this.privateDnsName = null;
		try { this.amazonEC2Client.shutdown(); this.amazonEC2Client = null; } catch (Exception x) {}
	}
	
	/*
	 * Not supported in OpenNebula
	 */
	public void attachVolume(String volumeId, String device) throws Exception {
		try {
			AttachVolumeRequest attachVolumeRequest = new AttachVolumeRequest()
				.withInstanceId(this.instanceId)
				.withDevice(device) // '/dev/vdb'
				.withVolumeId(volumeId); // 'vol-00001459'
			AttachVolumeResult attachVolumeResult = this.amazonEC2Client.attachVolume(attachVolumeRequest);
			VolumeAttachment volumeAttachment = attachVolumeResult.getAttachment();
			log.debug("Volume attached to " + volumeAttachment.getInstanceId() + volumeAttachment.getDevice() + " " + volumeAttachment.getState() + " " + volumeAttachment.getVolumeId() + " " + volumeAttachment.getAttachTime());
		} catch (AmazonServiceException x) {
			x.printStackTrace();
			throw new Exception("VM.attachVolume exception", x);
		} catch (AmazonClientException x) {
			throw new Exception("VM.attachVolume exception", x);
		}
	}

	public void detachVolume(String device) throws Exception {
		try {
			DetachVolumeRequest detachVolumeRequest = new DetachVolumeRequest();
			detachVolumeRequest.setDevice(device); // '/dev/vdb'
			detachVolumeRequest.setInstanceId(this.instanceId);
			this.amazonEC2Client.detachVolume(detachVolumeRequest);
		} catch (AmazonServiceException x) {
			throw new Exception("VM.attachVolume exception", x);
		} catch (AmazonClientException x) {
			throw new Exception("VM.attachVolume exception", x);
		}
	}
	
	public static void main(String[] args) throws Exception {
//		VM vm = new VM("http://cfe2.lpds.sztaki.hu:4567", "ahajnal@sztaki.hu", "60a...", "m1.medium", "ami-00001082", "ahajnal_keypair");
//		vm.run(null, ResourceUtils.getResorceBase64Encoded("optimizer.cloud-init"));
//		vm.run(null, ResourceUtils.getFileBase64Encoded("c:/LPDS/Entice/optimizer-cloud-init.txt"), null);
//		vm.run(null, null, "ami-00001459");
	}
}
