package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.rest;

import org.junit.Before;
import org.junit.Test;

public class WebApplicationContextListenerTest {

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testContextInitialized() {
		WebApplicationContextListener wacl = new WebApplicationContextListener();
		wacl.contextInitialized(null);
		wacl.contextDestroyed(null);
	}

	@Test
	public void testContextDestroyed() {
		new WebApplicationContextListener().contextDestroyed(null);
	}

}
