package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.utils;

import java.io.IOException;
import java.io.OutputStream;

public class OutputStreamWrapper extends OutputStream {
	private StringBuilder sb = new StringBuilder();
	@Override public void write(int arg0) throws IOException { sb.append((char) arg0); }
	@Override public String toString() { return sb.length() == 0 ? "" : sb.toString().trim(); } // truncate last \n
	public int size() { return sb.length(); }
	public void clear() { sb = new StringBuilder(); }
}
