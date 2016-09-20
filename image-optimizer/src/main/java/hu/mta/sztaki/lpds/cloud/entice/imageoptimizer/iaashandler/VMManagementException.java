package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;

public class VMManagementException extends Exception {
	private static final long serialVersionUID = 8550469711586641515L;

	public VMManagementException(String m, Exception e) {
		super(m, e);
		StackTraceElement ste = Thread.currentThread().getStackTrace()[2];
		Shrinker.myLogger.warning(ste.getClassName() + "."
				+ ste.getMethodName()
				+ " throws VMManagementException with messsage: '" + m
				+ "', cause: " + (e == null ? "" : e.getMessage()));
	}
}
