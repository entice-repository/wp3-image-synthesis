package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ranking;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestSizeBasedRanker {
	@SuppressWarnings("unused")
	private SizeBasedRanker t = new SizeBasedRanker();
	
	@Test public void getRankerInstance() {
		assertNotNull(SizeBasedRanker.getRankerInstance());
	}

	@Test(expected=NullPointerException.class) public void rank() {
		SizeBasedRanker.getRankerInstance().rank(null);
	}
}
