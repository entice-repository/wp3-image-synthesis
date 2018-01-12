package hu.mta.sztaki.lpds.entice.virtualimagedecomposer.rest;

import static org.junit.Assert.*;

import org.junit.Test;

public class NBDAllocationTest {
	NBDAllocation c = new NBDAllocation();
	
	@Test
	public void testAllocate() {
		int a = NBDAllocation.allocate();
		assertEquals(NBDAllocation.numberOfAllocations(), 1);
		NBDAllocation.release(a);
		assertEquals(NBDAllocation.numberOfAllocations(), 0);
	}

	@Test
	public void testFailedRelease() {
		int a = NBDAllocation.allocate();
		NBDAllocation.release(a + 1); // logs error
		assertEquals(NBDAllocation.numberOfAllocations(), 1);
		NBDAllocation.release(a);
	}
	
	@Test
	public void testFailedRelease2() {
		NBDAllocation.release(0); // logs error
		assertEquals(NBDAllocation.numberOfAllocations(), 0);
	}
}
