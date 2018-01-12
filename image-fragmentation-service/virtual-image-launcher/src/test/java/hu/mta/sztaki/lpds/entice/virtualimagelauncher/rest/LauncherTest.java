package hu.mta.sztaki.lpds.entice.virtualimagelauncher.rest;

import static org.junit.Assert.*;

import org.junit.Test;

public class LauncherTest {

	@Test
	public void testBase64Encode() {
		assertEquals("aGVsbG8=", Launcher.base64Encode("hello"));
	}

	@Test
	public void testBase64Decode() {
		assertEquals("hello", Launcher.base64Decode("aGVsbG8="));
	}

}
