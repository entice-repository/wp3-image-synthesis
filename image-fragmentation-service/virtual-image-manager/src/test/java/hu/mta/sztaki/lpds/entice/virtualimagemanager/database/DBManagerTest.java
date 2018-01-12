package hu.mta.sztaki.lpds.entice.virtualimagemanager.database;

import static org.junit.Assert.*;

import org.junit.Test;

public class DBManagerTest {

	@Test
	public void testGetInstance() {
		assertNotNull(DBManager.getInstance());
	}

	@Test
	public void testShutdown() {
		DBManager.getInstance().shutdown();
	}

	@Test
	public void testGetEntityManagerFactory() {
		assertNull(DBManager.getInstance().getEntityManagerFactory());
	}

	@Test(expected = Exception.class)
	public void testGetEntityManager() throws Exception {
		assertNull(DBManager.getInstance().getEntityManager());
	}

}
