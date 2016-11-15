package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping;

import static org.junit.Assert.*;
import java.io.File;
import org.junit.Test;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.Group.GroupState;

public class TestGroup {
	private Group t = new Group("~", null);
	
	@Test public void addToGroup() {
		t.addToGroup(new File("~"));
	}

	@Test public void genRemover() {
		assertNotNull(t.genRemover());
	}

	@Test public void getGroupState() {
		assertEquals(t.getGroupState().toString(), GroupState.NOT_TESTED.toString());
	}
	@Test public void getId() {
		assertNotNull(t.getId());
	}
	@Test public void getList() {
		t.getList();
	}
	@Test public void getSize() {
		assertEquals(0, t.getSize());
	}
	@Test public void getState() {
		assertEquals(t.getState().toString(), GroupState.NOT_TESTED.toString());
	}
	@Test public void isInFinalState() {
		assertFalse(t.isInFinalState());
	}
	@Test public void print() {
//		t.print(); avoid spamming console
	}
	@Test public void setSerializedGroupState() {
		t.setSerializedGroupState("123" + GroupState.NOT_TESTED.toString());
	}
	@Test public void setTestState() {
		t.setTestState(GroupState.CORE_GROUP);
	}
	@Test public void tostring() {
		t.toString();
	}
}