package hu.mta.sztaki.lpds.entice.virtualimagedecomposer.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// this class allows to allocate and release an nbd device number between 0..7
// devices: <allocated_device_number> and <allocated_device_number>+8 form a pair of devices available for mounting source and target image files  
public class NBDAllocation {
	private static final Logger log = LoggerFactory.getLogger(VirtualImageDecomposer.class); 
	static final int SIZE = 8;
	private static boolean allocations[] = new boolean [SIZE];
	
	// returns -1 if no free device
	static synchronized int allocate() {
		int allocation = -1;
		int free = 0;
		// allocate and count free
		for (int i = 0; i < SIZE; i++ ) {
			if (allocations[i] == false) {
				free++;
				if (allocation == -1) {
					allocations[i] = true;
					allocation = i;
				}
			}
		}
		if (allocation != -1) log.debug("NBD allocated: " + allocation + " (free: " + (free - 1) + ")");
		else log.error("NBD allocation without free device");
		return allocation;
	}
	
	static synchronized void release(int allocation) {
		if (allocation < 0 || allocation >= SIZE || allocations[allocation] == false) {
			log.error("Invalid allocation number: " + allocation);
			return;
		}
		allocations[allocation] = false;
		log.debug("NBD released: " + allocation);
	}
	
	static int numberOfAllocations() {
		int numberOfallocations = 0;
		for (int i = 0; i < SIZE; i++ ) if (allocations[i]) numberOfallocations++;
		return numberOfallocations;
	}
}