package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping;

import java.io.IOException;
import org.junit.Test;

public class TestGroupManager {
	private GroupManager t = new DirectoryGroupManager();

//	@Test public void addFile() {} see TestDirectoryGroupManager

	@Test public void evaluateStatistics() {
		t.evaluateStatistics();
	}

//	@Test public void getGroup() {} see TestDirectoryGroupManager

	@Test public void getGroups() {
		t.getGroups();
	}

	@Test public void getNecessaryGroups() {
		t.getNecessaryGroups();
	}

	@Test public void getOtherGroups() {
		t.getOtherGroups();
	}
	
	@Test public void getRemainingSize() {
		t.getRemainingSize();
	}

	@Test public void getRemovedGroups() {
		t.getRemovedGroups();
	}

	@Test public void getTotalSize() {
		t.getTotalSize();
	}

	@Test public void loadGroupStates() throws Exception {
		try { t.loadGroupStates(); } 
		catch (IOException x) {}
	}

	@Test public void saveGroupStates() throws Exception  {
		try { t.saveGroupStates(); } 
		catch (IOException x) {}
	}

}
