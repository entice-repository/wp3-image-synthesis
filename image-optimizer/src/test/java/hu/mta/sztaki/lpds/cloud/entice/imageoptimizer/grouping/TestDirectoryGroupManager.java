package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping;

import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;

public class TestDirectoryGroupManager {

	private static DirectoryGroupManager testClass = null;
	
	@Before public void setUp() {
		try { new Shrinker(Thread.currentThread().getThreadGroup(), new File ("~"), "vaid", null); } 
		catch (IOException x) {}
		if (testClass != null) return; 
		testClass = new DirectoryGroupManager();
	}
	
	@Test public void addFile() {
		testClass.addFile(new File("~"));
	}

	
	@Test public void getGroup() {
		testClass.getGroup(new File("~"));
	}

}
