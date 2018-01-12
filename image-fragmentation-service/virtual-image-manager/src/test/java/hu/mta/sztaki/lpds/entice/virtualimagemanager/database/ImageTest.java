package hu.mta.sztaki.lpds.entice.virtualimagemanager.database;

import static org.junit.Assert.*;

import org.junit.Test;

import hu.mta.sztaki.lpds.entice.virtualimagemanager.database.Image.ImageType;

public class ImageTest {

	@Test
	public void testId() {
		Image i = new Image();
		assertNotEquals("", i.getId());
	}

	@Test
	public void testType() {
		Image i = new Image();
		assertEquals(ImageType.BASE, i.getType());
	}

	@Test
	public void testCreated() {
		Image i = new Image();
		assertTrue(i.getCreated() >= System.currentTimeMillis());
	}
}
