package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer;

import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;

public class TestItemizer {

	private Itemizer itemizer = null;
	
	@Before public void setUp() {
		try { new Shrinker(Thread.currentThread().getThreadGroup(), new File ("~"), "vaid", null); } 
		catch (IOException x) {}
		this.itemizer = new Itemizer(Thread.currentThread().getThreadGroup());
	}
	
	@Test public void basicProcessFiles() {
		itemizer.basicProcessFiles(new File ("~"));
	}

	@Test public void isProcessingCompleted() {
		itemizer.isProcessingCompleted();
	}

	@Test public void stopProcessing() {
		itemizer.stopProcessing();
	}


	@Test public void processFiles() {
		itemizer.processFiles();
	}

}
