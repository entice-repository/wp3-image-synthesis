package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.fcotarget;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMFactory;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VirtualMachine;

public class SOAP extends VMFactory  {
	private static Logger log = Shrinker.myLogger;
	
	@Override protected String[] listSPToLookup() {
		return new String[] { FCOVM.ACCESS_KEY, FCOVM.ENDPOINT, FCOVM.INSTANCE_TYPE, FCOVM.SECRET_KEY };
	}

	@Override protected void prepareVMFactory() {
		// do nothing
	}
	
	@Override public void terminateFactory() {
		// do nothing
	}

	@Override protected VirtualMachine createNewVM(String vaId, Map<String, List<String>> contextandcustomizeVA) {
		try { return new FCOVM(contextandcustomizeVA, true, vaId); } 
		catch (Exception x) { log.severe(x.getMessage()); throw new RuntimeException(x); } 
	}
}