package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.utils;

import org.junit.Test;

import com.jcraft.jsch.JSchException;

public class SshSessionTest {

	@Test(expected=JSchException.class)
	public void testClose() throws Exception {
		new SshSession("host", "user", null).close();
	}

	@SuppressWarnings("resource")
	@Test(expected=JSchException.class)
	public void testExecuteCommandString() throws Exception {
		new SshSession("host", "user", null).executeCommand("ls");
	}

	@SuppressWarnings("resource")
	@Test(expected=JSchException.class)
	public void testExecuteCommandStringOutputStreamOutputStream() throws Exception {
		new SshSession("host", "user", null).executeCommand("ls", new OutputStreamWrapper(), new OutputStreamWrapper());
	}

}
