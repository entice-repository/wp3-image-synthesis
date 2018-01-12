package hu.mta.sztaki.lpds.entice.virtualimagemanager.database;

import static org.junit.Assert.*;

import org.junit.Test;

public class EdgeTest {

	@Test
	public void testId() {
		Edge i = new Edge();
		assertNotEquals("", i.getId());
	}

	@Test
	public void testType() {
		Edge i = new Edge();
		assertTrue(i.getFragmentSize() == 0l);
	}

	@Test
	public void testCreated() {
		Edge i = new Edge();
		assertTrue(i.getCreated() >= System.currentTimeMillis());
	}

}
