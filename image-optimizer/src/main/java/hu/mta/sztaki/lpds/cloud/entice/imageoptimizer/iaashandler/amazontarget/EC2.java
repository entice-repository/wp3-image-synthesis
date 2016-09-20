package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.amazontarget;

import java.util.TreeMap;
import java.util.Vector;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMFactory;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VirtualMachine;

public class EC2 extends VMFactory {
	@Override
	protected String[] listSPToLookup() {
		return new String[] { EC2VirtualMachine.ACCESS_KEY, EC2VirtualMachine.ENDPOINT, EC2VirtualMachine.INSTANCE_TYPE,
				EC2VirtualMachine.SECRET_KEY };
	}

	@Override
	protected void prepareVMFactory() {
		// do nothing
	}
	
	@Override
	public void terminateFactory() {
		// do nothing
	}

	@Override
	protected VirtualMachine createNewVM(String vaId, TreeMap<String, Vector<String>> contextandcustomizeVA) {
		return new EC2VirtualMachine(vaId, contextandcustomizeVA, true);
	}
	
}
