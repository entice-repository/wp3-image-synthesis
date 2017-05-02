package hu.mta.sztaki.lpds.entice.virtualimagelauncher.ec2;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStateChange;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;

import hu.mta.sztaki.lpds.entice.virtualimagelauncher.rest.Launcher;

public class EC2VM {
	
	public static final String cloudInterface = "ec2";
	
	private static final Logger log = LoggerFactory.getLogger(Launcher.class); 
	static {
		try { java.util.logging.Logger.getLogger(com.amazonaws.transform.SimpleTypeStaxUnmarshallers.class.getName()).setLevel(java.util.logging.Level.SEVERE); } 
		catch (Throwable x) {} 
	}
	
	public static String runInstance(String endpoint, String accessKey, String secretKey, String imageId, String instanceType, String keypair, String userDataBase64) throws Exception {
		AWSCredentials awsCredentials = new BasicAWSCredentials(accessKey, secretKey);
		ClientConfiguration clientConfiguration = new ClientConfiguration();
		AmazonEC2Client amazonEC2Client = new AmazonEC2Client(awsCredentials, clientConfiguration);
		amazonEC2Client.setEndpoint(endpoint);
		log.debug("endpoint: " + endpoint);
		log.debug("imageId: " + imageId);
		log.debug("accessKey: " + accessKey);
		log.debug("secretKey: " + secretKey.substring(0, 5) + "...");
		log.debug("instanceType: " + instanceType);
		log.debug("keypair: " + keypair);
		
		try {
			RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
			runInstancesRequest.withImageId(imageId).withInstanceType(instanceType).withMinCount(1).withMaxCount(1);
			if (keypair != null && !"".equals(keypair)) runInstancesRequest.withKeyName(keypair);
			if (userDataBase64 != null) runInstancesRequest.withUserData(userDataBase64);
//			if (availabilityZone != null && !"".equals(availabilityZone)) runInstancesRequest.withPlacement(new Placement(availabilityZone));
			RunInstancesResult runInstancesResult = amazonEC2Client.runInstances(runInstancesRequest);
			Reservation reservation = runInstancesResult.getReservation();
			List<Instance> instances = reservation.getInstances();
			if (instances.size() != 1) throw new Exception("Too few or too many instances started");
			String instanceId = instances.get(0).getInstanceId();
			return instanceId;
		} catch (AmazonServiceException x) {
			x.printStackTrace();
			throw new Exception("AWS EC2 run instance exception: " + x.getErrorCode() + " " + x.getMessage());
		} catch (AmazonClientException x) {
			x.printStackTrace();
			throw new Exception("AWS EC2 client exception: "  + x.getMessage());
		} catch (Exception x) {
			x.printStackTrace();
			throw new Exception("AWS EC2 exception: " + x.getMessage());
		} finally {
			try { amazonEC2Client.shutdown(); } catch(Exception x) {}
		}
	}
	
	public static void terminateInstance(String endpoint, String accessKey, String secretKey, String instanceId) throws Exception {
		AWSCredentials awsCredentials = new BasicAWSCredentials(accessKey, secretKey);
		ClientConfiguration clientConfiguration = new ClientConfiguration();
		AmazonEC2Client amazonEC2Client = new AmazonEC2Client(awsCredentials, clientConfiguration);
		amazonEC2Client.setEndpoint(endpoint);
		try {
			TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest();
			terminateInstancesRequest.withInstanceIds(instanceId);
			TerminateInstancesResult terminateInstancesResult = amazonEC2Client.terminateInstances(terminateInstancesRequest);
			List<InstanceStateChange> instanceStateChanges = terminateInstancesResult.getTerminatingInstances();
			InstanceStateChange instanceStateChange = null;
			for (InstanceStateChange stateChange: instanceStateChanges) {
				if (instanceId.equals(stateChange.getInstanceId())) instanceStateChange = stateChange;
				break;
			}
			// re-send terminate if needed
			if (instanceStateChange == null || !"terminated".equals(instanceStateChange.getCurrentState().getName())) {
				try { Thread.sleep(1000); } catch (Exception x) {}
				terminateInstancesResult = amazonEC2Client.terminateInstances(terminateInstancesRequest);
				instanceStateChanges = terminateInstancesResult.getTerminatingInstances();
				for (InstanceStateChange stateChange: instanceStateChanges) {
					if (instanceId.equals(stateChange.getInstanceId())) instanceStateChange = stateChange;
					break;
				}
			}
		} catch (AmazonServiceException x) {
			throw new Exception("terminate exception", x);
		} catch (AmazonClientException x) {
			throw new Exception("terminate exception", x);
		} finally {
			try { amazonEC2Client.shutdown(); } catch(Exception x) {}
		}
	}
}