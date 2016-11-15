package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.amazontarget;

import static org.junit.Assert.*;

import java.util.TreeMap;
import java.util.Vector;

import org.junit.Test;

public class TestEC2 {
	private EC2 t = new EC2();
	
	@Test(expected=IllegalArgumentException.class) public void createNewVM() {
		System.setProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxUsableCPUs", "10");
		t.createNewVM("vaid", new TreeMap<String, Vector<String>>());
	}

	@Test public void listSPToLookup() {
		assertNotNull(t.listSPToLookup());
	}

	@Test public void prepareVMFactory() {
		t.prepareVMFactory();
	}
	
	@Test public void terminateFactory() {
		t.terminateFactory();
	}
}
