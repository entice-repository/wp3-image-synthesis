package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.validator;

import static org.junit.Assert.*;

import java.util.Vector;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.Group;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMInstanceManager;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.amazontarget.EC2VirtualMachine;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.validator.SingleValidatorThread.ValidationState;

public class TestSingleValidatorThread {

//	private SingleValidatorThread t = new SingleValidatorThread(Thread.currentThread().getThreadGroup(), new Vector<Group> ());
	
	@Before public void init() {
		System.setProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxUsableCPUs", "10");
		new VMInstanceManager(Thread.currentThread().getThreadGroup(), 0); 
	}
	
	@Test(expected=IllegalStateException.class) public void executeTest() throws Exception {
		SingleValidatorThread.executeTest(new EC2VirtualMachine("vaid", null,  false), null);
	}
	
	@Test public void setNewThread() {
		SingleValidatorThread t = new SingleValidatorThread(Thread.currentThread().getThreadGroup(), new Vector<Group> ());
		assertEquals(t.getValidationState(), ValidationState.PRE);
	}
	
	@Ignore // impossible to test without running VMs 
	@Test(expected=IllegalStateException.class) public void run() {
		SingleValidatorThread t = new SingleValidatorThread(Thread.currentThread().getThreadGroup(), new Vector<Group> ());
		t.run();
		t.interrupt();
	}

}
