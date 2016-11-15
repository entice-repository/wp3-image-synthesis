package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.amazontarget;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class TestEC2VirtualMachine {
	private EC2VirtualMachine t = null;
	
	@Before public void init() {
		System.setProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxUsableCPUs", "10");
		t = new EC2VirtualMachine("vaid", null, false);
	}

	@Test public void getInstanceId() {
		assertNull(t.getInstanceId());
	}

	@Test public void getImageId() {
		assertNotNull(t.getImageId());
	}
	
	@Test public void getIP() throws Exception {
		assertNull(t.getIP());
	}
	
	@Test public void getPort() throws Exception {
		assertNull(t.getPort());
	}

}
