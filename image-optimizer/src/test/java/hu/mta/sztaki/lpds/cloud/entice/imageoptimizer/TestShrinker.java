package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer;

import org.junit.Assert;
import org.junit.Test;

public class TestShrinker {
	@Test public void main() throws Exception {
		String [] args = new String [] {"/nonexistingmountpoint", "imageId", "validatorScript.sh"};
		System.setProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ranking.RankerToUse", "hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ranking.GroupFactorBasedRanker");
		System.setProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.GrouperToUse", "hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.DirectoryGroupManager");
		System.setProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxUsableCPUs", "0");
		System.setProperty("hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxUsableCPUs", "0");
		Shrinker.main(args);
//		Assert.assertEquals(exitCode, 1);
	}

	@Test public void getContext() throws Exception {
		Assert.assertNotNull(Shrinker.getContext());
	}
}
