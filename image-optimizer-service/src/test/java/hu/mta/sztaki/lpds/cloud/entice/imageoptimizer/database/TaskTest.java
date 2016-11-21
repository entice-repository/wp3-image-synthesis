package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.database;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class TaskTest {
	private static Task t;
	@Before
	public void setUp() throws Exception {
		t = new Task();
	}

	@Test
	public void testSetId() {
		t.setId("id");
		assertEquals("id", t.getId());
	}

	@Test
	public void testGetId() {
		assertNotNull(t.getId());
	}

	@Test
	public void testSetCreated() {
		t.setCreated(1);
		assertEquals(1, t.getCreated());
	}

	@Test
	public void testGetCreated() {
		assertNotNull(t.getCreated());
	}

	@Test
	public void testSetEnded() {
		t.setEnded(1);
		assertEquals(1, t.getEnded());
	}

	@Test
	public void testGetEnded() {
		assertNotNull(t.getEnded());
	}

	@Test
	public void testSetInstanceId() {
	}

	// TODO
	@Test
	public void testGetInstanceId() {
	}

	@Test
	public void testSetStatus() {
	}

	@Test
	public void testGetStatus() {
	}

	@Test
	public void testGetOriginalUsedSpace() {
	}

	@Test
	public void testSetOriginalUsedSpaceLong() {
	}

	@Test
	public void testSetOriginalUsedSpaceString() {
	}

	@Test
	public void testGetOptimizedUsedSpace() {
	}

	@Test
	public void testSetOptimizedUsedSpaceLong() {
	}

	@Test
	public void testSetOptimizedUsedSpaceString() {
	}

	@Test
	public void testGetEndpoint() {
	}

	@Test
	public void testSetEndpoint() {
	}

	@Test
	public void testGetAccessKey() {
	}

	@Test
	public void testSetAccessKey() {
	}

	@Test
	public void testGetSecretKey() {
	}

	@Test
	public void testSetSecretKey() {
	}
		
	@Test
	public void testGetVmStatus() {
	}

	@Test
	public void testSetVmStatus() {
	}

	@Test
	public void testGetNumberOfVMsStarted() {
	}

	@Test
	public void testSetNumberOfVMsStarted() {
	}

	@Test
	public void testGetFailure() {
	}

	@Test
	public void testSetFailure() {
	}

	@Test
	public void testGetIteration() {
	}

	@Test
	public void testSetIterationInt() {
	}

	@Test
	public void testSetIterationString() {
	}

	@Test
	public void testGetOriginalImageSize() {
	}

	@Test
	public void testSetOriginalImageSizeLong() {
	}

	@Test
	public void testSetOriginalImageSizeString() {
	}

	@Test
	public void testGetOptimizedImageSize() {
	}

	@Test
	public void testSetOptimizedImageSizeLong() {
	}

	@Test
	public void testSetOptimizedImageSizeString() {
	}

	@Test
	public void testGetOptimizedImageURL() {
	}

	@Test
	public void testSetOptimizedImageURL() {
	}

	@Test
	public void testGetChart() {
	}

	@Test
	public void testSetChart() {
	}

	@Test
	public void testGetMaxIterationsNum() {
	}

	@Test
	public void testSetMaxIterationsNumInt() {
	}

	@Test
	public void testSetMaxIterationsNumString() {
	}

	@Test
	public void testGetMaxNumberOfVMs() {
	}

	@Test
	public void testSetMaxNumberOfVMsInt() {
	}

	@Test
	public void testSetMaxNumberOfVMsString() {
	}

	@Test
	public void testGetAimedReductionRatio() {
	}

	@Test
	public void testSetAimedReductionRatioFloat() {
	}

	@Test
	public void testSetAimedReductionRatioString() {
	}

	@Test
	public void testGetAimedSize() {
	}

	@Test
	public void testSetAimedSizeLong() {
	}

	@Test
	public void testSetAimedSizeString() {
	}

	@Test
	public void testGetMaxRunningTime() {
	}

	@Test
	public void testSetMaxRunningTimeLong() {
	}

	@Test
	public void testSetMaxRunningTimeString() {
	}

	@Test
	public void testGetOptimizerPhase() {
	}

	@Test
	public void testSetOptimizerPhase() {
	}

	@Test
	public void testGetShrinkerPhase() {
	}

	@Test
	public void testSetShrinkerPhase() {
	}

	@Test
	public void testGetRemovables() {
	}

	@Test
	public void testSetRemovables() {
	}
}
