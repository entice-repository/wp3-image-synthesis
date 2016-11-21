package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.utils;

import static org.junit.Assert.*;
import java.io.FileNotFoundException;
import org.junit.Before;
import org.junit.Test;

public class ResourceUtilsTest {

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testGetResorceAsString() throws Exception {
		assertEquals("version", ResourceUtils.getResorceAsString("image-optimizer-service.properties").substring(0, 7));
	}

	@Test
	public void testGetResorceBase64Encoded() throws Exception {
		assertEquals("dmVyc2l", ResourceUtils.getResorceBase64Encoded("image-optimizer-service.properties").substring(0, 7));
	}

	@Test
	public void testBase64Encode() {
		assertEquals("aGVsbG8=", ResourceUtils.base64Encode("hello"));
	}

	@Test(expected=FileNotFoundException.class)
	public void testGetFileBase64Encoded() throws Exception {
		assertEquals("version", ResourceUtils.getFileBase64Encoded("filenotfound").substring(0, 7));
	}

}
