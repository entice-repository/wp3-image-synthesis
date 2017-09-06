package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import org.apache.commons.codec.binary.Base64;

public class ResourceUtils {

	public static String getResorceAsString(String resource) throws Exception {
		InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
		if (in == null) throw new Exception("Cannot find resource: " + resource);  
		else {
			try {
				Scanner s = new Scanner(in).useDelimiter("\\A");
				String content = s.hasNext() ? s.next() : "";
				return content;
			} 
			finally { try { in.close(); } catch (IOException e) {} }
		} 
	}
	
	public static String getResorceBase64Encoded(String resource) throws Exception {
		return base64Encode(getResorceAsString(resource));
	}
	
	public static String base64Encode(String value) {
		return value != null ? new String(Base64.encodeBase64(value.getBytes())) : "";
	}
	
	public static String getFileBase64Encoded(String file) throws Exception {
		InputStream in = new FileInputStream(file);
		try {
			Scanner s = new Scanner(in).useDelimiter("\\A");
			String content = s.hasNext() ? s.next() : "";
			return base64Encode(content);
		} 
		finally { try { in.close(); } catch (IOException e) {} }
	}
	
	public static String base64Decode(String value) {
		return value != null ? new String(Base64.decodeBase64(value.getBytes())) : "";
	}

}