package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.DirectoryGroupManager;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.GroupManager;

public class TestItemPool {
	private ItemPool itemPool = null;
	
	@Before public void setUp() {
		this.itemPool = ItemPool.getInstance();
	}
	
	@Test public void getInstance() {
		assertNotNull(itemPool);
	}
	
	@Test public void isPoolFull() {
		assertFalse(itemPool.isPoolFull());
	}

	@Test public void removeFromPool() {
		itemPool.removefromPool((File)null);
	}
	
	@Test public void addGroupManager() {
		GroupManager gm = new DirectoryGroupManager();
		itemPool.addGroupManager(gm);
	}

	@Test public void removeGroupManager() {
		GroupManager gm = new DirectoryGroupManager();
		itemPool.removeGroupManager(gm);
	}
}
