package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.utils;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class OutputStreamWrapperTest {

	OutputStreamWrapper osw;
	
	@Before
	public void setUp() throws Exception {
		osw = new OutputStreamWrapper();
	}

	@Test
	public void testWriteInt() throws Exception{
		osw.write(0);
	}

	@Test
	public void testToString() {
		osw.toString();
	}

	@Test
	public void testSize() {
		assertEquals(0, osw.size());
	}

	@Test
	public void testClear() throws Exception {
		osw.write(0);
		osw.clear();
		assertEquals(0, osw.size());
	}

}
