package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.database;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class DBManagerTest {

	@Before
	public void setUp() throws Exception {
	}

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

	@Test
	public void testGetEntityManager() {
		assertNull(DBManager.getInstance().getEntityManager());
	}

}
