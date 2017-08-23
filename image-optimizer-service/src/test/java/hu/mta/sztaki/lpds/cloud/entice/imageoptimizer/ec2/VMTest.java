package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ec2;

import static org.junit.Assert.*;

import java.util.Hashtable;

import org.junit.Before;
import org.junit.Test;

public class VMTest {

	EC2VM vm = null;
	
	@Before
	public void setUp() throws Exception {
		vm = new EC2VM("http://localhost:4567", "accessKey", "secretKey", "m1.small", "imageid", "keypair");
	}

	@Test
	public void testGetInstanceId() {
		assertNull(vm.getInstanceId());
	}

	@Test
	public void testSetInstanceId() {
		vm.setInstanceId("id");
		assertEquals("id", vm.getInstanceId());
	}

	@Test
	public void testGetIP() {
		assertNull(vm.getIP());
	}

	@Test
	public void testGetStatus() {
		assertEquals(EC2VM.UNKNOWN, vm.getStatus());
	}

	@Test(expected=Exception.class)
	public void testRun() throws Exception {
		vm.run(new Hashtable<String,String>());
	}

	@Test(expected=Exception.class)
	public void testDescribeInstance() throws Exception {
		vm.describeInstance();
	}

	@Test(expected=Exception.class)
	public void testTerminate() throws Exception {
		vm.terminate();
	}

	@Test
	public void testDiscard() {
		vm.discard();
	}

	@Test
	public void testAttachVolume() {
		// not used, not tested
	}

	@Test
	public void testDetachVolume() {
		// not used, not tested
	}

}
