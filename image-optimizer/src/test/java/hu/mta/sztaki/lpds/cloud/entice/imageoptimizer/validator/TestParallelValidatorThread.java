package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.validator;

import org.junit.Test;

public class TestParallelValidatorThread {
	private ParallelValidatorThread t = new ParallelValidatorThread(null);
	
	@Test public void constructor() {
		new ParallelValidatorThread(null);
	}
	
	@Test public void setNewThread() {
		t.setNewThread(t);
	}
	
	@Test public void run() {
		t.run();
		t.interrupt();
	}
}
