package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.database;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;

@Entity 
public class Task {
	@Id
	private String id = UUID.randomUUID().toString();
	public void setId(String id) { this.id = id; }
	public String getId() { return this.id; }
	
	private long created = System.currentTimeMillis();
	public void setCreated(long created) { this.created = created; }
	public long getCreated() { return this.created; }

	private long ended;
	public void setEnded(long ended) { this.ended = ended; }
	public long getEnded() { return this.ended; }

	private String instanceId;
	public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
	public String getInstanceId() { return this.instanceId; }
	
	private String endpoint;
	private String accessKey;
	private String secretKey;
	
	// running, stopping, done, failed, aborted
	private String status;
	public void setStatus(String status) { this.status = status; }
	public String getStatus() { return this.status; }

	private String optimizerPhase = "";
	private String shrinkerPhase = "";
	
	private int maxIterationsNum; 
	private int maxNumberOfVMs; 
	private float aimedReductionRatio;
	private long aimedSize; 
	private long maxRunningTime;

	@Lob
	private String removables = "";

	private String vmStatus = "";
	private int numberOfVMsStarted;
	@Lob
	private String failure = "";
	private int iteration;
	private long originalImageSize;
	private long optimizedImageSize;
	private long originalUsedSpace;
	private long optimizedUsedSpace;
	private String optimizedImageURL = "";;
	
	@Lob
	private String chart = "[]";
	
	public long getOriginalUsedSpace() {
		return originalUsedSpace;
	}
	public void setOriginalUsedSpace(long originalUsedSpace) {
		this.originalUsedSpace = originalUsedSpace;
	}
	public void setOriginalUsedSpace(String size) throws NumberFormatException {
		if (size == null || "".equals(size)) return;
		this.originalUsedSpace = Long.parseLong(size);
	}
	public long getOptimizedUsedSpace() {
		return optimizedUsedSpace;
	}
	public void setOptimizedUsedSpace(long optimizedUsedSpace) {
		this.optimizedUsedSpace = optimizedUsedSpace;
	}
	public void setOptimizedUsedSpace(String size) throws NumberFormatException {
		if (size == null || "".equals(size)) return;
		this.optimizedUsedSpace = Long.parseLong(size);
	}
	public String getEndpoint() {
		return endpoint;
	}
	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}
	public String getAccessKey() {
		return accessKey;
	}
	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}
	public String getSecretKey() {
		return secretKey;
	}
	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}
	
	public String getVmStatus() {
		return vmStatus;
	}
	public void setVmStatus(String vmStatus) {
		this.vmStatus = vmStatus;
	}
	public int getNumberOfVMsStarted() {
		return numberOfVMsStarted;
	}
	public void setNumberOfVMsStarted(int numberOfVMsStarted) {
		this.numberOfVMsStarted = numberOfVMsStarted;
	}
	public String getFailure() {
		return failure;
	}
	public void setFailure(String failure) {
		this.failure = failure;
	}
	public int getIteration() {
		return iteration;
	}
	public void setIteration(int iteration) {
		this.iteration = iteration;
	}
	public void setIteration(String iteration) throws NumberFormatException {
		if (iteration == null || "".equals(iteration)) return;
		this.iteration = Integer.parseInt(iteration);
	}

	public long getOriginalImageSize() {
		return originalImageSize;
	}
	public void setOriginalImageSize(long originalImageSize) {
		this.originalImageSize = originalImageSize;
	}
	public void setOriginalImageSize(String originalImageSize) throws NumberFormatException {
		if (originalImageSize == null || "".equals(originalImageSize)) return;
		this.originalImageSize = Long.parseLong(originalImageSize);
	}
	public long getOptimizedImageSize() {
		return optimizedImageSize;
	}
	public void setOptimizedImageSize(long optimizedImageSize) {
		this.optimizedImageSize = optimizedImageSize;
	}
	public void setOptimizedImageSize(String optimizedImageSize) throws NumberFormatException {
		if (optimizedImageSize == null || "".equals(optimizedImageSize)) return;
		this.optimizedImageSize = Long.parseLong(optimizedImageSize);
	}
	
	public String getOptimizedImageURL() {
		return optimizedImageURL;
	}
	public void setOptimizedImageURL(String optimizedImageURL) {
		this.optimizedImageURL = optimizedImageURL;
	}
	public String getChart() {
		return chart;
	}
	public void setChart(String chart) {
		this.chart = chart;
	}
		
	public int getMaxIterationsNum() {
		return maxIterationsNum;
	}
	public void setMaxIterationsNum(int maxIterationsNum) {
		this.maxIterationsNum = maxIterationsNum;
	}
	public void setMaxIterationsNum(String maxIterationsNum) throws NumberFormatException {
		if (maxIterationsNum == null || "".equals(maxIterationsNum)) return;
		this.maxIterationsNum = Integer.parseInt(maxIterationsNum);
	}
	public int getMaxNumberOfVMs() {
		return maxNumberOfVMs;
	}
	public void setMaxNumberOfVMs(int maxNumberOfVMs) {
		this.maxNumberOfVMs = maxNumberOfVMs;
	}
	public void setMaxNumberOfVMs(String maxNumberOfVMs) throws NumberFormatException {
		if (maxNumberOfVMs == null || "".equals(maxNumberOfVMs)) return;
		this.maxNumberOfVMs = Integer.parseInt(maxNumberOfVMs);
	}
	public float getAimedReductionRatio() {
		return aimedReductionRatio;
	}
	public void setAimedReductionRatio(float aimedReductionRatio) {
		this.aimedReductionRatio = aimedReductionRatio;
	}
	public void setAimedReductionRatio(String aimedReductionRatio) throws NumberFormatException {
		if (aimedReductionRatio == null || "".equals(aimedReductionRatio)) return;
		this.aimedReductionRatio = Float.parseFloat(aimedReductionRatio);
	}
	public Long getAimedSize() {
		return aimedSize;
	}
	public void setAimedSize(long aimedSize) {
		this.aimedSize = aimedSize;
	}
	public void setAimedSize(String aimedSize)  throws NumberFormatException {
		if (aimedSize == null || "".equals(aimedSize)) return;
		this.aimedSize = Long.parseLong(aimedSize);
	}
	public long getMaxRunningTime() {
		return maxRunningTime;
	}
	public void setMaxRunningTime(long maxRunningTime) {
		this.maxRunningTime = maxRunningTime;
	}
	public void setMaxRunningTime(String maxRunningTime) throws NumberFormatException {
		if (maxRunningTime == null || "".equals(maxRunningTime)) return;
		this.maxRunningTime = Long.parseLong(maxRunningTime);
	}

	public String getOptimizerPhase() {
		return optimizerPhase;
	}
	public void setOptimizerPhase(String optimizerPhase) {
		this.optimizerPhase = optimizerPhase;
	}
	public String getShrinkerPhase() {
		return shrinkerPhase;
	}
	public void setShrinkerPhase(String shrinkerPhase) {
		this.shrinkerPhase = shrinkerPhase;
	}
	public String getRemovables() {
		return removables;
	}
	public void setRemovables(String removables) {
		this.removables = removables;
	}
}