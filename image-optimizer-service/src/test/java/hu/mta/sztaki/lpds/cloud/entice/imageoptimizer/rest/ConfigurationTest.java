package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.rest;

import static org.junit.Assert.*;
import org.junit.Test;

public class ConfigurationTest {

	@Test
	public void test() {
		assertEquals("http://cfe2.lpds.sztaki.hu:4567", Configuration.localEc2Endpoint);
	}

}
