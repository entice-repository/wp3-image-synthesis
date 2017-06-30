package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.rest;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.VM;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.database.DBManager;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.database.Task;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ec2.EC2VM;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.fco.FCOVM;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.utils.OutputStreamWrapper;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.utils.ResourceUtils;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.utils.SshSession;
import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.wt.WTVM;

import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.ClientResponse.Status;

@Path("/optimizer") 
public class Optimizer {
	
	private static final Logger log = LoggerFactory.getLogger(Optimizer.class); 
	private void logRequest(final String method, final HttpHeaders httpHeaders, final HttpServletRequest request) {
		log.info("" + method /* + ", from: " + request.getRemoteAddr() */);
	}
	
	private static ConcurrentHashMap <String, Task> taskCache = new ConcurrentHashMap<String, Task>(); // contains tasks of running/stopping state
	private static ConcurrentHashMap <String, VM> optimizerVMCache = new ConcurrentHashMap<String, VM>();
	
	public static final String OPTIMIZER_CLOUD_INIT_RESOURCE = "optimizer.cloud-init";
	public static final String OPTIMIZER_CLOUD_INIT_WRITE_FILES_PLACEHOLDER = "### write_files-placeholder";
	public static final String OPTIMIZER_CLOUD_INIT_RUNCMD_PLACEHOLDER = "### runcmd-placeholder";
	public static final String OPTIMIZER_SSH_PRIVATE_KEY_RESOURCE = "image-optimizer-service_priv.rsa";
	
	private static final String SHRINKER_STDOUT = "Shrinker.out";
	private static final String SOURCE_FILE_SYSTEM_DIR = "/mnt/source-file-system/";
	private static final String SOURCE_IMAGE_FILE = "/mnt/source-image.qcow2";
	private static final String OPTIMIZED_IMAGE_FILE = "/mnt/optimized-image.qcow2";
	private static final String VALIDATOR_SCRIPT_FILE = "/root/validator_script.sh";
	
	// post request fields
	public static final String ID = "id"; // REQUIRED by optimized-image upload
	public static final String IMAGE_URL = "imageURL"; // REQUIRED
	public static final String IMAGE_ID = "imageId"; // REQUIRED
	public static final String OVF_URL = "ovfURL"; // OPTIONAL (required in VMware)
	public static final String IMAGE_KEY_PAIR = "imageKeyPair"; // OPTIONAL (will use image wired public key if absent)
	public static final String IMAGE_PRIVATE_KEY = "imagePrivateKey"; // REQUIRED
	public static final String IMAGE_USER_NAME = "imageUserName"; // OPTIONAL (default: root, properties file)
	public static final String IMAGE_CONTEXTUALIZATION = "imageContextualization"; // OPTIONAL 
	public static final String IMAGE_CONTEXTUALIZATION_URL = "imageContextualizationURL"; // OPTIONAL 
	public static final String IMAGE_ROOT_FILE_SYSTEM_PARTITION = "fsPartition"; // OPTIONAL (default: 1st partition, no LVM), partno || volgroup logvolume
	public static final String IMAGE_ROOT_FILE_SYSTEM_TYPE = "fsType"; // OPTIONAL (default: ext2-ext4)
	public static final String IMAGE_FORMAT = "imageFormat"; // OPTIONAL (default: qcow2) vmdk, vdi, vhd, vhdx, qcow1, qed, raw
	
	public static final String VALIDATOR_SCRIPT = "validatorScript"; // REQUIRED one of VALIDATOR_*
	public static final String VALIDATOR_SCRIPT_URL = "validatorScriptURL"; // REQUIRED one of VALIDATOR_*
	public static final String VALIDATOR_SERVER_URL = "validatorServerURL"; // REQUIRED one of VALIDATOR_* 
	public static final String CLOUD_ENDPOINT_URL = "cloudEndpointURL"; // OPTIONAL (default: sztaki cloud, properties file)
	public static final String CLOUD_INTERFACE = "cloudInterface"; // OPTIONAL (default: properties file || ec2)
	public static final String CLOUD_ACCESS_KEY = "cloudAccessKey"; // REQUIRED
	public static final String CLOUD_SECRET_KEY = "cloudSecretKey"; // REQUIRED
	public static final String CLOUD_OPTIMIZER_VM_INSTANCE_TYPE = "cloudOptimizerVMInstanceType"; // OPTIONAL (default: m1.medium, properties file)
	public static final String CLOUD_WORKER_VM_INSTANCE_TYPE = "cloudWorkerVMInstanceType"; // OPTIONAL (default: m1.small, properties file)
	public static final String NUMBER_OF_PARALLEL_WORKER_VMS = "numberOfParallelWorkerVMs";  // OPTIONAL (default: 8, properties file)
	public static final String AVAILABILITY_ZONE = "availabilityZone";  // OPTIONAL
	
	public static final String S3_ENDPOINT_URL = "s3EndpointURL"; // OPTIONAL (but the optimized image will not be uploaded, neither can download source image, validator script from s3 URLs)
	public static final String S3_ACCESS_KEY = "s3AccessKey"; // OPTIONAL
	public static final String S3_SECRET_KEY = "s3SecretKey"; // OPTIONAL
	public static final String S3_PATH = "s3Path"; // OPTIONAL (bucket/filename)
	public static final String S3_REGION = "s3Region"; // OPTIONAL (Amazon requires)
	public static final String S3_SIGNATURE_VERSION = "s3SignatureVersion"; // OPTIONAL (Amazon requires)
	
	public static final String MAX_ITERATIONS_NUM = "maxIterationsNum"; // OPTIONAL (algorithm stops at reaching the plateau)
	public static final String MAX_NUMBER_OF_VMS = "maxNumberOfVMs"; // OPTIONAL
	public static final String AIMED_REDUCTION_RATIO = "aimedReductionRatio"; // OPTIONAL
	public static final String AIMED_SIZE = "aimedSize"; // OPTIONAL
	public static final String MAX_RUNNING_TIME = "maxRunningTime"; // OPTIONAL
	public static final String FREE_DISK_SPACE = "freeDiskSpace"; // OPTIONAL
	
	public static final String OPTIMIZER_IMAGE_ID = "optimizerImageId"; // OPTIONAL (for testing new optimizers)

	// status response fields
	public static final String RESPONSE_STATUS = "status";
	public static final String RESPONSE_OPTIMIZER_VM_STATUS = "optimizerVMStatus";
	public static final String RESPONSE_OPTIMIZER_PHASE = "optimizerPhase";
	public static final String RESPONSE_SHRINKER_PHASE = "shrinkerPhase";
	
	public static final String RESPONSE_STARTED = "started";
	public static final String RESPONSE_ENDED = "ended";
	public static final String RESPONSE_RUNNING_TIME = "runningTime";
	public static final String RESPONSE_MAX_RUNNING_TIME = "maxRunningTime";

	public static final String RESPONSE_REMOVABLES = "removables";

	public static final String RESPONSE_NUMBER_OF_VMS_STARTED = "numberOfVMsStarted";
	public static final String RESPONSE_MAX_NUMBER_OF_VMS = "maxNumberOfVMs";

	public static final String RESPONSE_ITERATION = "iteration";
	public static final String RESPONSE_MAX_ITERATIONS_NUM = "maxIterationsNum";
	
	public static final String RESPONSE_ORIGINAL_IMAGE_SIZE = "originalImageSize";
	public static final String RESPONSE_OPTIMIZED_IMAGE_SIZE = "optimizedImageSize";
	public static final String RESPONSE_AIMED_SIZE = "aimedSize";
	public static final String RESPONSE_AIMED_REDUCTION_RATIO = "aimedReductionRatio";

	public static final String RESPONSE_ORIGINAL_USED_SPACE = "originalUsedSpace";
	public static final String RESPONSE_OPTIMIZED_USED_SPACE = "optimizedUsedSpace";

	
	public static final String RESPONSE_FAILURE = "failure";
	
	public static final String RESPONSE_OPTIMIZED_IMAGE_URL = "optimizedImageURL";
	public static final String RESPONSE_CHART = "chart";
	
	@POST @Consumes(MediaType.APPLICATION_JSON)
	public Response optimize(
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			String body) {
		logRequest("POST", headers, request);

		// parse input JSON ====================================
		log.debug("Parsing JSON entity body...");
		JSONObject requestBody = null;
        if (body != null && body.length() > 0) {
    		try { requestBody = new JSONObject(new JSONTokener(body)); }
    		catch (JSONException e) { return Response.status(Status.BAD_REQUEST).entity("Invalid JSON content: " + e.getMessage()).build(); }
        } else { return Response.status(Status.BAD_REQUEST).entity("Missing entity body!").build(); }
        
        // create optimizer VM ====================================
        log.info("Creating optimizer VM...");
        
        String endpoint = !"".equals(requestBody.optString(CLOUD_ENDPOINT_URL)) ? requestBody.getString(CLOUD_ENDPOINT_URL) : Configuration.localEc2Endpoint;
        try {
        	requestBody.getString(CLOUD_ACCESS_KEY);
        	requestBody.getString(CLOUD_SECRET_KEY);
        } catch (JSONException x) {
        	return Response.status(Status.BAD_REQUEST).entity("Missing parameters: " + CLOUD_ACCESS_KEY + ", " + CLOUD_SECRET_KEY).build();
        }
        String accessKey = requestBody.getString(CLOUD_ACCESS_KEY);
        String secretKey = requestBody.getString(CLOUD_SECRET_KEY);

        // create optimizer VM contextualization ====================================
        log.info("Creating optimizer VM contextualization...");
        
        // parameters for contextualization
        Map<String,String> parameters = new HashMap<String,String>(); 

        parameters.put(CLOUD_ENDPOINT_URL, endpoint); // OPTIONAL
        parameters.put(CLOUD_ACCESS_KEY, accessKey); // REQUIRED
        parameters.put(CLOUD_SECRET_KEY, secretKey); // REQUIRED
        
        String cloudInit = null;
        try { cloudInit = ResourceUtils.getResorceAsString(OPTIMIZER_CLOUD_INIT_RESOURCE); }
        catch (Exception x) { return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Missing resource: " + OPTIMIZER_CLOUD_INIT_RESOURCE).build(); }

        if ("".equals(requestBody.optString(ID))) log.warn("No parameter " + ID + " provided for knowledge base. Optimized image will not be uploaded.");
        if (Configuration.knowledgeBaseURL == null) log.warn("knowledgeBaseURL not defined in properties file: " + Configuration.PROPERTIES_FILE_NAME + ". Optimized image will not be uploaded.");
        parameters.put(ID, requestBody.optString(ID)); // REQUIRED
        
        if ("".equals(requestBody.optString(IMAGE_ID))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + IMAGE_ID + "").build();
        parameters.put(IMAGE_ID, requestBody.getString(IMAGE_ID)); // REQUIRED
        
        if ("".equals(requestBody.optString(IMAGE_URL))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + IMAGE_URL + "").build();
        parameters.put(IMAGE_URL, requestBody.getString(IMAGE_URL)); // REQUIRED

        String imageFormat = requestBody.optString(IMAGE_FORMAT); 
        if (!"".equals(imageFormat)) {
        	if (!"vmdk".equals(imageFormat) &&
       			!"qcow".equals(imageFormat) &&
       			!"qcow2".equals(imageFormat) &&
       			!"raw".equals(imageFormat))
				log.error("Unsupported image fomat: " + imageFormat); 
			else 
				parameters.put(IMAGE_FORMAT, imageFormat); 
        }
        
        if ("".equals(requestBody.optString(VALIDATOR_SCRIPT)) && "".equals(requestBody.optString(VALIDATOR_SCRIPT_URL)) && "".equals(requestBody.optString(VALIDATOR_SERVER_URL)))
        	return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + VALIDATOR_SCRIPT + " or " + VALIDATOR_SCRIPT_URL + "").build();
        parameters.put(VALIDATOR_SCRIPT, requestBody.optString(VALIDATOR_SCRIPT)); // REQUIRED one of them
        parameters.put(VALIDATOR_SCRIPT_URL, requestBody.optString(VALIDATOR_SCRIPT_URL)); // REQUIRED one of them
        parameters.put(VALIDATOR_SERVER_URL, requestBody.optString(VALIDATOR_SERVER_URL)); // REQUIRED one of them
        
        String cloudInterface = requestBody.optString(CLOUD_INTERFACE, Configuration.cloudInterface);
        parameters.put(CLOUD_INTERFACE, cloudInterface); 

        String ovfURL = requestBody.optString(OVF_URL);
        if (WTVM.CLOUD_INTERFACE.equals(cloudInterface) && "".equals(ovfURL)) 
        	return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + OVF_URL).build();
        parameters.put(OVF_URL, ovfURL);
        
        parameters.put(CLOUD_OPTIMIZER_VM_INSTANCE_TYPE, requestBody.optString(CLOUD_OPTIMIZER_VM_INSTANCE_TYPE, Configuration.optimizerInstanceType)); // OPTIONAL
       	parameters.put(CLOUD_WORKER_VM_INSTANCE_TYPE, requestBody.optString(CLOUD_WORKER_VM_INSTANCE_TYPE, Configuration.workerInstanceType)); // OPTIONAL
        parameters.put(NUMBER_OF_PARALLEL_WORKER_VMS, requestBody.optString(NUMBER_OF_PARALLEL_WORKER_VMS, Configuration.maxUsableCPUs)); // OPTIONAL
        try { int x = Integer.parseInt(parameters.get(NUMBER_OF_PARALLEL_WORKER_VMS)); if (x < 2) return Response.status(Status.BAD_REQUEST).entity("Parameter " + NUMBER_OF_PARALLEL_WORKER_VMS + " must be greater than or equal to 2.").build(); } catch (NumberFormatException x) { log.warn("Parameter " + NUMBER_OF_PARALLEL_WORKER_VMS + " is of invalid syntax."); }
        
        if ("".equals(requestBody.optString(IMAGE_PRIVATE_KEY))) return Response.status(Status.BAD_REQUEST).entity("Missing parameter: " + IMAGE_PRIVATE_KEY).build();
        parameters.put(IMAGE_KEY_PAIR, requestBody.optString(IMAGE_KEY_PAIR)); // OPTIONAL (wired public key)
        parameters.put(IMAGE_PRIVATE_KEY, requestBody.optString(IMAGE_PRIVATE_KEY)); // REQUIRED
        parameters.put(IMAGE_USER_NAME, requestBody.optString(IMAGE_USER_NAME, Configuration.workerVMRootLogin)); // OPTIONAL
        
        parameters.put(IMAGE_CONTEXTUALIZATION, requestBody.optString(IMAGE_CONTEXTUALIZATION)); // OPTIONAL
        parameters.put(IMAGE_CONTEXTUALIZATION_URL, requestBody.optString(IMAGE_CONTEXTUALIZATION_URL)); // OPTIONAL
        parameters.put(IMAGE_ROOT_FILE_SYSTEM_PARTITION, requestBody.optString(IMAGE_ROOT_FILE_SYSTEM_PARTITION)); // OPTIONAL
        parameters.put(IMAGE_ROOT_FILE_SYSTEM_TYPE, requestBody.optString(IMAGE_ROOT_FILE_SYSTEM_TYPE)); // OPTIONAL 
        
        parameters.put(S3_ENDPOINT_URL, requestBody.optString(S3_ENDPOINT_URL)); // REQUIRED
        parameters.put(S3_ACCESS_KEY, requestBody.optString(S3_ACCESS_KEY)); // REQUIRED
        parameters.put(S3_SECRET_KEY, requestBody.optString(S3_SECRET_KEY)); // REQUIRED
        parameters.put(S3_PATH, requestBody.optString(S3_PATH)); // REQUIRED
        parameters.put(S3_REGION, requestBody.optString(S3_REGION));
        parameters.put(S3_SIGNATURE_VERSION, requestBody.optString(S3_SIGNATURE_VERSION));
        
        // check potential s3 parameters and required credentials
        if (	(!"".equals(requestBody.optString(IMAGE_URL)) && requestBody.optString(IMAGE_URL).startsWith("s3://")) ||
        		(!"".equals(requestBody.optString(VALIDATOR_SCRIPT_URL)) && requestBody.optString(VALIDATOR_SCRIPT_URL).startsWith("s3://")) ||
        		(!"".equals(requestBody.optString(S3_PATH))) ) {
    		if (!"".equals(parameters.get(S3_ENDPOINT_URL)) && !"".equals(parameters.get(S3_ACCESS_KEY)) && !"".equals(parameters.get(S3_SECRET_KEY))) {}
    		else return Response.status(Status.BAD_REQUEST).entity("S3 access is required by either " + IMAGE_URL + " or " + VALIDATOR_SCRIPT_URL + " or " + S3_PATH + ", but no credentials are provided: " + S3_ENDPOINT_URL + " and " + S3_ACCESS_KEY + " and " + S3_SECRET_KEY + ".").build(); 
        }
        
        parameters.put(MAX_ITERATIONS_NUM, requestBody.optString(MAX_ITERATIONS_NUM)); // OPTIONAL
        parameters.put(MAX_NUMBER_OF_VMS, requestBody.optString(MAX_NUMBER_OF_VMS)); // OPTIONAL
        parameters.put(AIMED_REDUCTION_RATIO, requestBody.optString(AIMED_REDUCTION_RATIO)); // OPTIONAL
        parameters.put(AIMED_SIZE, requestBody.optString(AIMED_SIZE)); // OPTIONAL
        parameters.put(MAX_RUNNING_TIME, requestBody.optString(MAX_RUNNING_TIME)); // OPTIONAL
        parameters.put(FREE_DISK_SPACE, requestBody.optString(FREE_DISK_SPACE)); // OPTIONAL
        
        if (!"".equals(requestBody.optString(FREE_DISK_SPACE))) {
        	try {
        		Integer.parseInt(requestBody.optString(FREE_DISK_SPACE));
        	} catch (NumberFormatException x) {
        		return Response.status(Status.BAD_REQUEST).entity("Invalid value for parameter " + FREE_DISK_SPACE + ": " + requestBody.optString(FREE_DISK_SPACE)).build();
        	}
        }

       	// create task ====================================
		Task task = new Task();

        // create cloud-init
       	String writeFilesSection = generateCloudInitWriteFiles(parameters);
       	cloudInit = cloudInit.replace(OPTIMIZER_CLOUD_INIT_WRITE_FILES_PLACEHOLDER, writeFilesSection);

       	String runCmdSection = generateCloudInitRuncmd(task.getId());
       	cloudInit = cloudInit.replace(OPTIMIZER_CLOUD_INIT_RUNCMD_PLACEHOLDER, runCmdSection);
        
       	// set optimizer image id if set in request
       	String optimizerImageId = !"".equals(requestBody.optString(OPTIMIZER_IMAGE_ID)) ? requestBody.optString(OPTIMIZER_IMAGE_ID) : Configuration.optimizerImageId;
       			
       	// create optimizer VM (not yet started)
        VM optimizerVM = null; 
        try { 
        	if (EC2VM.CLOUD_INTERFACE.equals(cloudInterface)) {
        		optimizerVM = new EC2VM(endpoint, accessKey, secretKey, parameters.get(CLOUD_OPTIMIZER_VM_INSTANCE_TYPE), optimizerImageId, null); 
        	} else if (FCOVM.CLOUD_INTERFACE.equals(cloudInterface)) {
        		// read parameters from request
        		String userEmailAddressSlashCustomerUUID = parameters.get(CLOUD_ACCESS_KEY);
        		String password = parameters.get(CLOUD_SECRET_KEY);
        		optimizerVM = new FCOVM.Builder(endpoint, userEmailAddressSlashCustomerUUID, password, optimizerImageId)
        				.withInstanceType(parameters.get(CLOUD_OPTIMIZER_VM_INSTANCE_TYPE))
        				.withDiskSize(16) // GB
        				.build();  
        	} else if (WTVM.CLOUD_INTERFACE.equals(cloudInterface)) {
        		String username = parameters.get(CLOUD_ACCESS_KEY);
        		String password = parameters.get(CLOUD_SECRET_KEY);
        		optimizerVM = new WTVM.Builder(endpoint, username, password)
        				.build(); 
        	} else return Response.status(Status.BAD_REQUEST).entity("Invalid cloud interface: " + cloudInterface).build(); 
        } catch (Exception x) { return Response.status(Status.BAD_REQUEST).entity("Cannot create optimizer VM: " + x.getMessage()).build(); }

		// set known task properties
		task.setStatus(OptimizerStatus.RUNNING.name()); // task: running
		task.setCloudInterface(cloudInterface);
        task.setEndpoint(endpoint);
        task.setAccessKey(accessKey);
        task.setSecretKey(secretKey);
		
		try { task.setMaxIterationsNum(parameters.get(MAX_ITERATIONS_NUM)); }
		catch (NumberFormatException x) {return Response.status(Status.BAD_REQUEST).entity("NumberFormatException " + MAX_ITERATIONS_NUM + ": " + x.getMessage()).build(); } 
		try { task.setMaxNumberOfVMs(parameters.get(MAX_NUMBER_OF_VMS)); }
		catch (NumberFormatException x) {return Response.status(Status.BAD_REQUEST).entity("NumberFormatException " + MAX_NUMBER_OF_VMS + ": " + x.getMessage()).build(); } 
		try { task.setAimedReductionRatio(parameters.get(AIMED_REDUCTION_RATIO)); }
		catch (NumberFormatException x) {return Response.status(Status.BAD_REQUEST).entity("NumberFormatException " + AIMED_REDUCTION_RATIO + ": " + x.getMessage()).build(); }
		try { task.setAimedSize(parameters.get(AIMED_SIZE)); }
		catch (NumberFormatException x) {return Response.status(Status.BAD_REQUEST).entity("NumberFormatException " + AIMED_SIZE + ": " + x.getMessage()).build(); }
		try { task.setMaxRunningTime(parameters.get(MAX_RUNNING_TIME)); }
		catch (NumberFormatException x) {return Response.status(Status.BAD_REQUEST).entity("NumberFormatException " + MAX_RUNNING_TIME + ": " + x.getMessage()).build(); }
		task.setOptimizedImageURL(parameters.get(S3_ENDPOINT_URL) + "/" + parameters.get(S3_PATH));
		
        parameters.put(AVAILABILITY_ZONE, requestBody.optString(AVAILABILITY_ZONE)); // OPTIONAL

//		log.debug("Parameters: " + parameters);
//		log.debug("Cloud-init: " + cloudInit);
		
        // start optimizer VM  ====================================
        log.info("Starting optimizer VM...");
        try {
        	Map<String, String> pars = new HashMap<String, String>();
        	pars.put(VM.USER_DATA_BASE64, ResourceUtils.base64Encode(cloudInit)); 
        	pars.put(VM.USER_DATA, cloudInit); 
        	pars.put(EC2VM.AVAILABILITY_ZONE, parameters.get(AVAILABILITY_ZONE));
        	pars.put(VM.LOGIN, Configuration.optimizerRootLogin);
        	
        	pars.put(VM.SSH_KEY_PATH, Configuration.sshKeyPath);
        	optimizerVM.run(pars);
        	task.setInstanceId(optimizerVM.getInstanceId());
        	task.setVmStatus(VM.PENDING);
        } catch (Exception x) {
        	optimizerVM.discard();
        	return Response.status(Status.BAD_REQUEST).entity("Cannot start optimizer VM: " + x.getMessage()).build(); 
        }
        
        // store task and VM in cache
		taskCache.put(task.getId(), task);
		optimizerVMCache.put(task.getId(), optimizerVM);
		
		// persist task
		EntityManager entityManager = DBManager.getInstance().getEntityManager();
		if (entityManager != null) {
			try {
				entityManager.getTransaction().begin();
				entityManager.persist(task);
				entityManager.getTransaction().commit();
				entityManager.close();
				log.info("Task stored in database: " + task.getId());
			} catch (Throwable x) { log.error("Database connection problem. Check JPA settings!", x); }
		} else log.error("No database access! (Tasks will be stored in memory; restart will cause loss of this task id: " + task.getId() + ")");
		
		return Response.status(Status.OK).entity(task.getId()).build();
	}
	
	private Task retrieveTask(String id) {
		Task task = taskCache.get(id);
		if (task == null) { // get from database
			log.debug("Getting task " + id + " from database...");
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			if (entityManager != null) {
				try {
					entityManager.getTransaction().begin();
					task = entityManager.find(Task.class, id);
					entityManager.getTransaction().commit();
					entityManager.close();
				} catch (Throwable x) { log.error("Database connection problem. Check JPA settings!", x); }
				if (task == null) log.debug("Task id not found in database: " + id);
			} else log.error("No database!");
			if (task != null) {
				if (task.getStatus().equalsIgnoreCase(OptimizerStatus.DONE.name()) ||
						task.getStatus().equalsIgnoreCase(OptimizerStatus.FAILED.name()) ||
						task.getStatus().equalsIgnoreCase(OptimizerStatus.ABORTED.name())) {
					// do not put task to cache
				} else {
					taskCache.put(id, task);
				}
			}
		} else {
			log.debug("Task found in cache. (Cache size: " + taskCache.size() + ")");
		}
		return task;
	}
	
	private VM retrieveVM(Task task) { // note: no describe
		VM vm = optimizerVMCache.get(task.getId());
		if (vm == null) { // recreate VM object to connect to the instance
			if (task.getInstanceId() == null) log.error("No instanceId in task, cannot recover optimizer VM"); // it should not happen as this case was filtered befor
			else {
				try { 
					if (task.getCloudInterface() == null || EC2VM.CLOUD_INTERFACE.equals(task.getCloudInterface())) {
						vm = new EC2VM(task.getEndpoint(), task.getAccessKey(), task.getSecretKey(), task.getInstanceId()); 
					} else if (FCOVM.CLOUD_INTERFACE.equals(task.getCloudInterface())) {
		        		// read parameters from database
		        		String userEmailAddressSlashCustomerUUID = task.getAccessKey();
		        		String password = task.getSecretKey();
		        		// Configuration.optimizerImageId is used anyway? 
		        		vm = new FCOVM.Builder(task.getEndpoint(), userEmailAddressSlashCustomerUUID, password, Configuration.optimizerImageId)
		        				.withServerUUID(task.getInstanceId())
		        				.build(); 
					} else if (WTVM.CLOUD_INTERFACE.equals(task.getCloudInterface())) {
		        		String username = task.getAccessKey();
		        		String password = task.getSecretKey();
		        		vm = new WTVM.Builder(task.getEndpoint(), username, password)
		        				.build(); 
					} else log.error("Invalid cloud interface in database: " + task.getCloudInterface());
	        		vm.describeInstance();
				} catch (Exception x) { log.error("Cannot recover VM: " + x.getMessage()); }
			}
			if (vm != null) {
				if (task.getStatus().equalsIgnoreCase(OptimizerStatus.DONE.name()) ||
						task.getStatus().equalsIgnoreCase(OptimizerStatus.FAILED.name()) ||
						task.getStatus().equalsIgnoreCase(OptimizerStatus.ABORTED.name())) {
					// do not put VM to cache
				} else {
					optimizerVMCache.put(task.getId(), vm);
				}
			}
		}
		return vm;
	}
	
	private void persistTask(Task task) {
		EntityManager entityManager = DBManager.getInstance().getEntityManager();
		if (entityManager != null) {
			try {
				entityManager.getTransaction().begin();
				Task entity = entityManager.find(Task.class, task.getId());
				if (entity != null) {
					entity.setStatus(task.getStatus());
					entity.setVmStatus(task.getVmStatus());
					entity.setOptimizerPhase(task.getOptimizerPhase());
					entity.setShrinkerPhase(task.getShrinkerPhase());
					entity.setCreated(task.getCreated());
					entity.setEnded(task.getEnded());
					entity.setRemovables(task.getRemovables());
					entity.setMaxRunningTime(task.getMaxRunningTime());
					entity.setNumberOfVMsStarted(task.getNumberOfVMsStarted());
					entity.setMaxNumberOfVMs(task.getMaxNumberOfVMs());
					entity.setIteration(task.getIteration());
					entity.setMaxIterationsNum(task.getMaxIterationsNum());
					entity.setOriginalImageSize(task.getOriginalImageSize());
					entity.setOptimizedImageSize(task.getOptimizedImageSize());
					entity.setOriginalUsedSpace(task.getOriginalUsedSpace());
					entity.setOptimizedUsedSpace(task.getOptimizedUsedSpace());
					entity.setAimedSize(task.getAimedSize());
					entity.setAimedReductionRatio(task.getAimedReductionRatio());
					entity.setFailure(task.getFailure());
					entity.setOptimizedImageURL(task.getOptimizedImageURL());
					entity.setChart(task.getChart());
					entity.setCloudInterface(task.getCloudInterface());
				} else log.warn("Task id not found in database: " + task.getId());
				entityManager.getTransaction().commit();
				entityManager.close();
				log.debug("Task data updated in database");
			} catch (Throwable x) { log.error("Database connection problem. Check JPA settings!", x); }
		} else log.warn("No database!");
	}
	
	private JSONObject renderTaskToJSON(Task task) {
		JSONObject json = new JSONObject();
		json.put(RESPONSE_STATUS, task.getStatus());
		json.put(RESPONSE_OPTIMIZER_VM_STATUS, task.getVmStatus());
		json.put(RESPONSE_OPTIMIZER_PHASE, task.getOptimizerPhase());
		json.put(RESPONSE_SHRINKER_PHASE, task.getShrinkerPhase());
		json.put(RESPONSE_STARTED, task.getCreated());
		json.put(RESPONSE_ENDED, task.getEnded());
		json.put(RESPONSE_RUNNING_TIME, task.getEnded() == 0l ? (System.currentTimeMillis() - task.getCreated()) / 1000 : (task.getEnded() - task.getCreated()) / 1000);
		json.put(RESPONSE_MAX_RUNNING_TIME, task.getMaxRunningTime());
		json.put(RESPONSE_REMOVABLES, task.getRemovables());
		json.put(RESPONSE_NUMBER_OF_VMS_STARTED, task.getNumberOfVMsStarted());
		json.put(RESPONSE_MAX_NUMBER_OF_VMS, task.getMaxNumberOfVMs());
		json.put(RESPONSE_ITERATION, task.getIteration());
		json.put(RESPONSE_MAX_ITERATIONS_NUM, task.getMaxIterationsNum());
		json.put(RESPONSE_ORIGINAL_IMAGE_SIZE, task.getOriginalImageSize());
		json.put(RESPONSE_OPTIMIZED_IMAGE_SIZE, task.getOptimizedImageSize());
		json.put(RESPONSE_ORIGINAL_USED_SPACE, task.getOriginalUsedSpace());
		json.put(RESPONSE_OPTIMIZED_USED_SPACE, task.getOptimizedUsedSpace());
		json.put(RESPONSE_AIMED_SIZE, task.getAimedSize());
		json.put(RESPONSE_AIMED_REDUCTION_RATIO, task.getAimedReductionRatio());
		json.put(RESPONSE_FAILURE, task.getFailure());
		json.put(RESPONSE_OPTIMIZED_IMAGE_URL, task.getOptimizedImageURL());
		json.put(RESPONSE_CHART, new JSONArray(new JSONTokener(task.getChart())));
		json.put(CLOUD_INTERFACE, task.getCloudInterface());
		json.put("id", task.getId());
		return json;
	}

	@GET @Path("{id}") @Produces(MediaType.APPLICATION_JSON)
	public Response status(
			@PathParam("id") String id,
			@Context HttpHeaders headers,
			@Context HttpServletRequest request,
			@HeaderParam("KeepVM") String keepVM) {
		
		Task task = retrieveTask(id);
		if (task == null) return Response.status(Status.BAD_REQUEST).entity("Invalid task id: " + id).build();

		synchronized (task) { // in cache (running/stopping) or not (done/failed/aborted)

		// Task idempotent
		if (task.getStatus().equalsIgnoreCase(OptimizerStatus.DONE.name()) ||
			task.getStatus().equalsIgnoreCase(OptimizerStatus.FAILED.name()) ||
			task.getStatus().equalsIgnoreCase(OptimizerStatus.ABORTED.name())) {
			return Response.status(Status.OK).entity(renderTaskToJSON(task).toString()).build();
		} 

		logRequest("GET", headers, request); // don't log invalid requests or completed tasks (avoid filling log with spam on frequent polls)

		// status = RUNNING || STOPPING
		log.debug("Status: " + task.getStatus());
		
		// get instance id (during RUNNING or STOPPING)
		if (task.getInstanceId() == null) { // it should not happen ever, no chance to recover from this case 
			log.error("No instance id for task: " + task.getId());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("No optimizer instance id for task: " + task.getId()).build(); 
		}
		
		log.debug("Instance: " + task.getInstanceId());
		
		// get optimizer VM
		VM optimizerVM = retrieveVM(task); // it returns VM object from cache (not null) or re-creates the VM object
		if (optimizerVM == null) { // null means: could not create AWS client 
			log.debug("Cannot describe optimizer VM: " + task.getInstanceId() + ". Possible resons: changed AWS credentials or instance id cannot be described.");
			task.setOptimizerPhase("Getting describe information about: " + task.getInstanceId() + "...");
			task.setVmStatus(VM.BOOTING);
			return Response.status(Status.OK).entity(renderTaskToJSON(task).toString()).build(); 
		}

		// describe VM if IP not known or state is not running
		if (optimizerVM.getIP() == null || !optimizerVM.getStatus().equalsIgnoreCase(VM.RUNNING)) {
			log.debug("Describe: " + task.getInstanceId());
			try { optimizerVM.describeInstance(); } 
			catch (Exception x) { // may be temporary, should not happen unless AWS credentials changed
				log.warn("Cannot describe VM " + task.getInstanceId() + ": " + x.getMessage());
				task.setOptimizerPhase("Describing " + task.getInstanceId() + "... (AWS exception)");
				return Response.status(Status.OK).entity(renderTaskToJSON(task).toString()).build(); 
			}
		}
		
		log.debug("VM status: " + optimizerVM.getStatus());
		log.debug("IP: " + optimizerVM.getIP());
		task.setVmStatus(optimizerVM.getStatus());

		// we have VM now yet started (cannot yet be described)
		if (optimizerVM.getStatus().equalsIgnoreCase(VM.UNKNOWN)) { 
			log.debug("Cannot describe optimizer VM: " + task.getInstanceId() + ", state is: " + VM.UNKNOWN +  ". Possible resons: changed AWS credentials or instance id cannot be described.");
			task.setOptimizerPhase("Describing " + task.getInstanceId() + "...");
			return Response.status(Status.OK).entity(renderTaskToJSON(task).toString()).build(); 
		}

		// VM already down (someone externally stopped the optimizer VM), no chance to recover from this situation
		if (optimizerVM.getStatus().equalsIgnoreCase(VM.TERMINATED) 
			|| optimizerVM.getStatus().equalsIgnoreCase(VM.SHUTDOWN) 
			|| optimizerVM.getStatus().equalsIgnoreCase(VM.STOPPED) 
			|| optimizerVM.getStatus().equalsIgnoreCase(VM.STOPPING)) {
			task.setStatus(OptimizerStatus.FAILED.name());
			task.setFailure("Optimizer Orchestrator VM " + task.getInstanceId() + " is down: " + optimizerVM.getStatus() + ". (Terminated externally or unsuccessful launch.)");
			task.setSecretKey(""); // clear passwords from database
			task.setEnded(System.currentTimeMillis()); // it is query time, not task completion time
			persistTask(task);
			taskCache.remove(task.getId()); // remove Task from task cache (assuming the user will not query it again)
			optimizerVMCache.remove(task.getId()); // remove VM from VM cache
			optimizerVM.discard();
			return Response.status(Status.OK).entity(renderTaskToJSON(task).toString()).build();
		}

		// IP not yet available (it has booting or running status, but no IP yet)
		if (optimizerVM.getIP() == null) {
			log.debug("No IP of optimizer instance: " + task.getInstanceId());
			task.setOptimizerPhase("Getting IP to " + task.getInstanceId() + "...");
			return Response.status(Status.OK).entity(renderTaskToJSON(task).toString()).build(); // VM staring up
		}

		log.debug("Opening SSH connection to VM " + optimizerVM.getInstanceId() + " " + optimizerVM.getIP());
		SshSession ssh = null;
		boolean changed = false;
		try {
			
			log.debug("Opening SSH connection to " + optimizerVM.getInstanceId() + " " + Configuration.optimizerRootLogin + "@" + optimizerVM.getIP() + " " + Configuration.sshKeyPath);
			ssh = new SshSession(optimizerVM.getIP(), Configuration.optimizerRootLogin, Configuration.sshKeyPath);
			
			OutputStreamWrapper stdout = new OutputStreamWrapper();
			OutputStreamWrapper stderr = new OutputStreamWrapper();
			int exitCode;
		
			// check id file to filter zombies
			log.debug("Checking file 'id'...");
			boolean zombie = false;
			exitCode = ssh.executeCommand("cat id", stdout, stderr);
			if (exitCode == 0) { 
				 if (stdout.size() > 0) {
					 String content = stdout.toString().trim();
						if (content.length() > 0 && !task.getId().equals(content)) 
								zombie = true;
				 } else log.debug("Empty file 'id'");
			} else log.debug("No such file 'id'");
			
			// zombie
			if (zombie) {
				log.error("ZOMBIE found: queried task id differs from file contents of 'id'. VM: " + task.getInstanceId() + " " + optimizerVM.getIP() + " " + System.currentTimeMillis());
				// return unchanged task details
				
			} else { // task id matches file id, or no id file at all (starting up, legacy)
				
				// update statuses ==========================================
				// status: file "failure" exists || grep file Shrinker.out "Exception" => FAILED,
				// 			file "phase" == Done => DONE
				//			else leave status unchanged (STOPPING, RUNNING)
				// optimizerVMStatus: VM 
				// optimizerPhase: content of file "phase"
				// shrinkerPhase: last line of Shrinker.log containing pattern ###phase
				log.debug("Checking file 'failure'...");
				stdout.clear(); stderr.clear();
				exitCode = ssh.executeCommand("cat failure", stdout, stderr);
				if (exitCode == 0) { 
					 if (stdout.size() > 0) {
						task.setFailure(stdout.toString().trim());
						task.setStatus(OptimizerStatus.FAILED.name());
						log.debug("Failed: " + stdout.toString());
						logOuts(ssh);
						changed = true;
					 } else log.debug("Empty file 'failure'");
				} else log.debug("No such file 'failure'");
				
				log.debug("Grepping file 'Shrinker.out' for 'Exception'...");
				stdout.clear(); stderr.clear();
				exitCode = ssh.executeCommand("grep 'Exception' Shrinker.out", stdout, stderr);
				if (exitCode == 0) { 
					String content = stdout.toString().trim();
					if (content.length() > 0) {
						task.setFailure(stdout.toString().trim());
						task.setStatus(OptimizerStatus.FAILED.name());
						log.debug("Failed: " + stdout.toString());
						changed = true;
					 } else log.debug("No pattern 'Exception' found in 'Shrinker.out'");
				} else log.debug("No such file 'Shrinker.out'");
				
				log.debug("Checking file 'phase'...");
				stdout.clear(); stderr.clear();
				exitCode = ssh.executeCommand("cat phase", stdout, stderr);
				if (exitCode == 0) { 
					String content = stdout.toString().trim();
					if (content.length() > 0) {
						log.debug("Phase: " + stdout.toString());
						
						if (!content.equals(task.getOptimizerPhase())) changed = true;
						// set optimizerPhase
						task.setOptimizerPhase(content);
						// set status DONE if phase is "Done"
						if ("Done".equalsIgnoreCase(content)) // assuming that status cannot be FAILED
							task.setStatus(OptimizerStatus.DONE.name());
						
					} else log.debug("Empty file 'phase'");
				} else log.debug("No such file 'phase'");
	
				// set optimizerVMStatus
				optimizerVM.describeInstance();
				task.setVmStatus(optimizerVM.getStatus());
	
				// set shrinker phase
				log.debug("Grepping file 'Shrinker.log' for lines '###phase: '...");
				stdout.clear(); stderr.clear();
				String pattern = "###phase: ";
				exitCode = ssh.executeCommand("grep '" + pattern + "' Shrinker.log", stdout, stderr);
				if (exitCode == 0) { 
					String content = stdout.toString().trim();
					if (content.length() > 0) {
						log.debug("Shrinker phase: " + content);
						if (content.contains(pattern)) {
							content = content.substring(content.lastIndexOf(pattern) + pattern.length());
							if (!content.equals(task.getShrinkerPhase())) changed = true;
							task.setShrinkerPhase(content);
							changed = true;
						} 
					} else log.debug("No pattern '" + pattern + "' found in file Shrinker.log");
				} else log.debug("No such file 'Shrinker.log'");
				
				// get removables
				log.debug("Grepping file 'Shrinker.log' for lines '###removable: '...");
				stdout.clear(); stderr.clear();
				pattern = "###removable: ";
				exitCode = ssh.executeCommand("grep '" + pattern + "' Shrinker.log", stdout, stderr);
				if (exitCode == 0) { 
					String content = stdout.toString().trim();
					if (content.length() > 0) { 
						log.debug("Removables: " + content);
						
						BufferedReader reader = new BufferedReader(new StringReader(content));
						int maxLength = 1024;
						String line;
						StringBuilder sb = new StringBuilder();
						while ((line = reader.readLine()) != null) {
							if (!line.contains(pattern)) continue;
							if (sb.length() > maxLength) {
								sb.append(", ...");
								break;
							} 
							line = line.substring(line.lastIndexOf(pattern) + pattern.length());
							line = line.replaceAll("/mnt/source-file-system", "");
							if (sb.length() > 0) sb.append(", ");
							sb.append(line.trim());
						}
						String removables = sb.toString();
						if (!removables.equals(task.getRemovables())) {
							task.setRemovables(removables);
							changed = true;
						} 
					} else log.debug("No pattern '" + pattern + "' found in file Shrinker.log");
				} else log.debug("No such file 'Shrinker.log'");
				
				// started (constant)/ended (when finished)/running time (computed at response)/max running time (constant) ==========================================
				if (task.getStatus().equalsIgnoreCase(OptimizerStatus.DONE.name()) ||
					task.getStatus().equalsIgnoreCase(OptimizerStatus.FAILED.name()) ||
					task.getStatus().equalsIgnoreCase(OptimizerStatus.ABORTED.name())) {
						log.debug("Task completed with status: " + task.getStatus());
						task.setEnded(System.currentTimeMillis()); // it is query time, not task completion time
						changed = true;
				}
			
				// set numberOfVMsStarted/maxNumberOfVMs (constant)
				log.debug("Grepping file 'Shrinker.log' for lines '###vms: '...");
				stdout.clear(); stderr.clear();
				pattern = "###vms: ";
				exitCode = ssh.executeCommand("grep '" + pattern + "' Shrinker.log", stdout, stderr);
				if (exitCode == 0) { 
					String content = stdout.toString().trim();
					if (content.length() > 0) {
						log.debug("Vms started: " + content);
						if (content.contains(pattern)) {
							content = content.substring(content.lastIndexOf(pattern) + pattern.length()).trim();
							try {
								int vmsStarted = Integer.parseInt(content);
								if (vmsStarted != task.getNumberOfVMsStarted()) {
									task.setNumberOfVMsStarted(vmsStarted);
									changed = true;
								}
							} catch (NumberFormatException x) {
								log.warn("Cannot parse number of VMs: " + content);
							}
						} 
					} else log.debug("No pattern '" + pattern + "' found in file Shrinker.log");
				} else log.debug("No such file 'Shrinker.log'");
				
				// set iteration/maxIterationsNum (constant)
				// it comes from Shrinker.log grep stats: iterationCounter currentSize initialSize timestamp
				log.debug("Grepping file 'Shrinker.log' for lines '###stats: '...");
				stdout.clear(); stderr.clear();
				pattern = "###stats: ";
				exitCode = ssh.executeCommand("grep '" + pattern + "' Shrinker.log | awk ' {print $6,$7,$8,$9} '", stdout, stderr);
				if (exitCode == 0) { 
					String content = stdout.toString().trim();
					if (content.length() > 0) {
						Scanner scanner = new Scanner(content);
						StringBuilder chart = new StringBuilder();
						chart.append("[");
						while (scanner.hasNextLine()) {
							String line = scanner.nextLine();
							log.debug("Statistics line: " + line);
							// process the line
							String [] parts = line.split(" ");
							if (parts.length < 4) { log.warn("Missing data in chart line: " + line); break; }
							try { task.setIteration(parts[0]); } 
							catch (NumberFormatException x) { log.warn("Invalid iteration number value: " + parts[0]); continue; } 
							try { task.setOptimizedUsedSpace(parts[1]); } 
							catch (NumberFormatException x) { log.warn("Invalid optimizedImageSize value: " + parts[1]); continue; }
							try { task.setOriginalUsedSpace(parts[2]); } 
							catch (NumberFormatException x) { log.warn("Invalid originalImageSize value: " + parts[2]); continue; }
							try { Long.parseLong(parts[3]); } 
							catch (NumberFormatException x) { log.warn("Invalid timestamp value: " + parts[3]); continue; }
							// if there were items, add comma
							 if (chart.length() > 1) chart.append(", "); 
							 // chart data: [[iteration, timestamp, current image size, original image size], ...]
							 chart.append("[" + parts[0] + ", " + parts[3] + ", " + parts[1] + ", " + parts[2] + "]");
						}
						chart.append("]");
						scanner.close();
						String chartString = chart.toString();
						if (!chartString.equals(task.getChart())) changed = true;
						task.setChart(chartString);
					} else log.debug("No pattern '" + pattern + "' found in 'Shrinker.log'");
				} else log.debug("No such file 'Shrinker.log'");
				
				// set source and optimized image size
				// it comes from Shrinker.log grep stats: iterationCounter currentSize initialSize timestamp
				log.debug("Getting source image size...");
				stdout.clear(); stderr.clear();
				exitCode = ssh.executeCommand("ls -ln " + SOURCE_IMAGE_FILE + " | awk '{print $5}'", stdout, stderr);
				if (exitCode == 0) { 
					String content = stdout.toString().trim();
					try { 
						long prev = task.getOriginalImageSize();
						task.setOriginalImageSize(content); 
						if (prev != task.getOriginalImageSize()) changed = true;
					} catch (NumberFormatException x) { log.debug("Invalid file size string for file " + SOURCE_IMAGE_FILE + ": " + content); }
				} else log.debug("File not found: " + SOURCE_IMAGE_FILE);
		
				log.debug("Getting optimized image size...");
				stdout.clear(); stderr.clear();
				exitCode = ssh.executeCommand("ls -ln " + OPTIMIZED_IMAGE_FILE + " | awk '{print $5}'", stdout, stderr);
				if (exitCode == 0) { 
					String content = stdout.toString().trim();
					try { 
						long prev = task.getOptimizedImageSize();
						task.setOptimizedImageSize(content); 
						if (prev != task.getOptimizedImageSize()) changed = true;
					} catch (NumberFormatException x) { log.debug("Invalid file size string for file " + OPTIMIZED_IMAGE_FILE + ": " + content); }
				} else log.debug("File not found: " + OPTIMIZED_IMAGE_FILE);
			
			} // end if not zombie
			
		} // end of open ssh connection  
		catch (Exception x) {
			String error = "Cannot establish SSH connection and/or perform commands on optimizer VM (" + optimizerVM.getInstanceId() + " " + optimizerVM.getIP() + "): " + x.getMessage(); 
			log.warn(error);
			task.setVmStatus(VM.BOOTING);
			task.setOptimizerPhase("Opening SSH connection to optimizer VM: " +  optimizerVM.getInstanceId() + " " + optimizerVM.getIP());
			return Response.status(Status.OK).entity(renderTaskToJSON(task).toString()).build(); 
		} 
		finally { if (ssh!= null) ssh.close(); } 
				
		// if task DONE/FAILED, kill VM, cleanup
		if (task.getStatus().equalsIgnoreCase(OptimizerStatus.DONE.name()) ||
			task.getStatus().equalsIgnoreCase(OptimizerStatus.FAILED.name())) {
			
			if (!"true".equals(keepVM)) {
				log.debug("Shutting down optimizer VM: " + optimizerVM.getInstanceId() + "...");
				try { optimizerVM.terminate(); } // can wait up to 30 seconds
				catch (Exception x) {} 
				task.setVmStatus(optimizerVM.getStatus()); // NOTE: VM state may remain in shutting down but will terminate later
			} else {
				task.setVmStatus(VM.TERMINATED);
				log.warn("VM " + optimizerVM.getInstanceId() + " is in done/failed status but not terminated due to KeepVM header parameter. The VM must be terminated manually!");
			}
			
			log.debug("Removing task and VM from cache...");
			taskCache.remove(task.getId()); // remove Task from task cache (assuming the user will not query it again)
			optimizerVMCache.remove(task.getId()); // remove VM from VM cache
			optimizerVM.discard();
			task.setSecretKey(""); // clear passwords from database
		}
		
		// update database
		if (changed) persistTask(task);  
		
		} // end synchronized(task) 
		return Response.status(Status.OK).entity(renderTaskToJSON(task).toString()).build();
	}

	private void logOuts(SshSession ssh) {
		// download.out, mountImage.out, SHRINKER_STDOUT, unmountImage.out, createOptimizedImage.out, upload.out
		logFile(ssh, "download.out");
		logFile(ssh, "mountImage.out");
		logFile(ssh, SHRINKER_STDOUT + " | grep -A 10 'Exception'");
		logFile(ssh, "unmountImage.out");
		logFile(ssh, "createOptimizedImage.out");
		logFile(ssh, "upload.out");
	}

	private void logFile(SshSession ssh, String file) {
		OutputStreamWrapper stdout = new OutputStreamWrapper();
		try {
			if (ssh.executeCommand("cat " + file, stdout, null) == 0) { 
				String content = stdout.toString().trim();
				if (content.length() > 0) log.debug(file + ": " + content.toString());
			}
		} catch (Exception e) {}	
	}
	
	@PUT
	@Path("{id}")
	public Response stop(
			@PathParam("id") String id,
			@Context HttpHeaders headers,
			@Context HttpServletRequest request) {
		logRequest("PUT", headers, request);

		Task task = retrieveTask(id);
		if (task == null) return Response.status(Status.BAD_REQUEST).entity("Invalid task id: " + id).build();
		synchronized(task) {

		log.info("Stopping task: " + task.getId());

		// only a running task can be stopped
		if (!task.getStatus().equalsIgnoreCase(OptimizerStatus.RUNNING.name()))
			return Response.status(Status.BAD_REQUEST).entity("Task (" + id + ") is not in RUNNING state (" + task.getStatus() + ")").build();

		// we need optimizer VM
		if (task.getInstanceId() == null) return Response.status(Status.FOUND).entity("Optimizer VM not yet started. Retry later!").build();
		
		// get optimizer VM
		VM optimizerVM = retrieveVM(task);
		if (optimizerVM == null) return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Cannot retrieve optimizer VM instance: " + task.getInstanceId()).build();
		
		// get VM IP if not yet set
		if (optimizerVM.getIP() == null) {
			try { optimizerVM.describeInstance(); }
			catch (Exception x) { 
				return Response.status(Status.FOUND).entity("Cannot describe optimizer VM instance: " + task.getInstanceId() + ": " + x.getMessage()).build();
			}
		}
		
		// IP not yet available
		if (optimizerVM.getIP() == null) return Response.status(Status.FOUND).entity("No IP available for optimizer VM: " + task.getInstanceId() + "").build();
		
		log.debug("Opening SSH connection to VM " + optimizerVM.getInstanceId() + " " + optimizerVM.getIP());
		SshSession ssh = null;
		try {
			String sshKeyPath = Thread.currentThread().getContextClassLoader().getResource(OPTIMIZER_SSH_PRIVATE_KEY_RESOURCE).toString();
			if (sshKeyPath != null) { 
				if (sshKeyPath.startsWith("file:\\")) sshKeyPath = sshKeyPath.substring("file:\\".length());
				else if (sshKeyPath.startsWith("file:/")) sshKeyPath = sshKeyPath.substring("file:".length());
			} else throw new Exception("Resource not found: " + OPTIMIZER_SSH_PRIVATE_KEY_RESOURCE);
			ssh = new SshSession(optimizerVM.getIP(), Configuration.optimizerRootLogin, sshKeyPath);
			
			OutputStreamWrapper stdout = new OutputStreamWrapper();
			OutputStreamWrapper stderr = new OutputStreamWrapper();
			int exitCode;

			exitCode = ssh.executeCommand("echo '' > /root/stop ", stdout, stderr);
			if (exitCode == 0) {
				task.setStatus(OptimizerStatus.STOPPING.name());
			} else {
				log.warn(stderr.toString());
				return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Cannot create stop file on VM: " + optimizerVM.getInstanceId() + " " + optimizerVM.getIP()).build(); 
			}
		} // end of open ssh connection  
		catch (Exception x) {
			String error = "Cannot establish SSH connection and/or perform commands on optimizer VM (" + optimizerVM.getInstanceId() + " " + optimizerVM.getIP() + "): " + x.getMessage(); 
			log.warn(error);
			task.setVmStatus(VM.BOOTING);
			task.setOptimizerPhase("Waiting to open SSH connection to optimizer VM...");
			return Response.status(Status.OK).entity(renderTaskToJSON(task).toString()).build(); 
		} 
		finally { if (ssh!= null) ssh.close(); } 

		persistTask(task);

		} // end synchronized(task)
		
		return Response.status(Status.OK).build();
	}
	
	@DELETE
	@Path("{id}")
	public Response abort(
			@PathParam("id") String id,
			@Context HttpHeaders headers,
			@Context HttpServletRequest request) {
		logRequest("DELETE", headers, request);
		
		Task task = retrieveTask(id);
		if (task == null) return Response.status(Status.BAD_REQUEST).entity("Invalid task id: " + id).build();
		synchronized(task) {
	
		log.info("Aborting task: " + task.getId());

		// only a running task can be stopped
		if (!task.getStatus().equalsIgnoreCase(OptimizerStatus.RUNNING.name()) && !task.getStatus().equalsIgnoreCase(OptimizerStatus.STOPPING.name()))
			return Response.status(Status.BAD_REQUEST).entity("Task (" + id + ") is not in RUNNING or STOPPING state (" + task.getStatus() + ")").build();

		
		// we need optimizer VM
		VM optimizerVM = retrieveVM(task);
		if (optimizerVM == null) return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Cannot retrieve optimizer VM instance: " + task.getInstanceId()).build();

		log.debug("Teminating optimizer VM: " + optimizerVM.getInstanceId() + "...");
		try { optimizerVM.terminate(); } // can wait up to 30 seconds
		catch (Exception x) {} 
		task.setVmStatus(optimizerVM.getStatus()); // NOTE: VM state may remain in shutting down but will terminate later
		log.debug("Removing task and VM from cache...");
		taskCache.remove(task.getId()); // remove Task from task cache (assuming the user will not query it again)
		optimizerVMCache.remove(task.getId()); // remove VM from VM cache
		optimizerVM.discard();
		task.setSecretKey("");
		task.setEnded(System.currentTimeMillis());
		task.setStatus(OptimizerStatus.ABORTED.name());
		persistTask(task);

		} // end synchronized(task)
		return Response.status(Status.OK).build();
	}
	
	private String generateCloudInitWriteFiles(Map<String,String> parameters) {
		String vmFactory;
		// set vmFactory according to cloudInterface
		String cloudInterface = parameters.get(CLOUD_INTERFACE); // must not be null
       	if (FCOVM.CLOUD_INTERFACE.equals(cloudInterface)) vmFactory = "hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.fcotarget.SOAP";
       	else if (WTVM.CLOUD_INTERFACE.equals(cloudInterface)) vmFactory = "hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.wttarget.REST";
       	else vmFactory = "hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.amazontarget.EC2";
		String vmFactoryClass = vmFactory.substring(vmFactory.lastIndexOf(".") + 1); // e.g., "EC2";
		
		StringBuilder sb = new StringBuilder();
		sb.append("- path: /root/optimize.sh"); sb.append("\n");
		sb.append("  permissions: \"0700\""); sb.append("\n");
		sb.append("  content: |"); sb.append("\n");
		sb.append("    #!/bin/bash"); sb.append("\n");
		sb.append("    export JAVA_OPTS=\"\\"); sb.append("\n");
		sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ranking.RankerToUse=" + Configuration.rankerToUse + " \\"); sb.append("\n"); 
		sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.grouping.GrouperToUse=" + Configuration.grouperToUse + " \\"); sb.append("\n"); 
		sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxUsableCPUs=" + parameters.get(NUMBER_OF_PARALLEL_WORKER_VMS) /*Configuration.maxUsableCPUs */+ " \\"); sb.append("\n"); 
		sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.validator.ParallelVMNum=" + parameters.get(NUMBER_OF_PARALLEL_WORKER_VMS) /*Configuration.parallelVMNum*/ + " \\"); sb.append("\n"); 
		sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMFactory=" + vmFactory + " \\"); sb.append("\n"); 
		sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMFactory." + vmFactoryClass + ".accessKey=" + parameters.get(CLOUD_ACCESS_KEY) + " \\"); sb.append("\n"); 
		sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMFactory." + vmFactoryClass + ".secretKey=" + parameters.get(CLOUD_SECRET_KEY) + " \\"); sb.append("\n"); 
		sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMFactory." + vmFactoryClass + ".endpoint=" + parameters.get(CLOUD_ENDPOINT_URL) + " \\"); sb.append("\n"); 
		sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMFactory." + vmFactoryClass + ".instanceType=" + parameters.get(CLOUD_WORKER_VM_INSTANCE_TYPE) + " \\"); sb.append("\n");
		if (!"".equals(parameters.get(IMAGE_KEY_PAIR))) {
		sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.keypair=" + parameters.get(IMAGE_KEY_PAIR) + " \\"); sb.append("\n"); 
		}
		sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.util.scriptprefix=" + Configuration.scriptPrefix + " \\"); sb.append("\n"); 
		sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.util.sshkey=/root/.ssh/id_rsa"); sb.append(" \\" + "\n");
		sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.sourceimagefilename=" + SOURCE_IMAGE_FILE); sb.append(" \\" + "\n");
		sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.VMFactory." + vmFactoryClass + ".loginName=" + parameters.get(IMAGE_USER_NAME) + " \\"); sb.append("\n");
		sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.targetimagefilename=" + OPTIMIZED_IMAGE_FILE); sb.append(" \\" + "\n");
		if (!"".equals(parameters.get(OVF_URL))) { sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.ovfURL=" + parameters.get(OVF_URL)); sb.append(" \\" + "\n"); }
		if (!"".equals(parameters.get(MAX_ITERATIONS_NUM))) { sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxIterationsNum=" + parameters.get(MAX_ITERATIONS_NUM)); sb.append(" \\" + "\n"); }
		if (!"".equals(parameters.get(MAX_NUMBER_OF_VMS))) { sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxNumberOfVMs=" + parameters.get(MAX_NUMBER_OF_VMS)); sb.append(" \\" + "\n"); }
		if (!"".equals(parameters.get(AIMED_REDUCTION_RATIO))) { sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.aimedReductionRatio=" + parameters.get(AIMED_REDUCTION_RATIO)); sb.append(" \\" + "\n"); }
		if (!"".equals(parameters.get(AIMED_SIZE))) { sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.aimedSize=" + parameters.get(AIMED_SIZE)); sb.append(" \\" + "\n"); }
		if (!"".equals(parameters.get(MAX_RUNNING_TIME))) { sb.append("    -Dhu.mta.sztaki.lpds.cloud.entice.imageoptimizer.maxRunningTime=" + parameters.get(MAX_RUNNING_TIME)); sb.append(" \\" + "\n"); }
		sb.append("    -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog"); sb.append("\""); sb.append("\n");
		
		// FIXME in the case of WT set signature version
		if (!"".equals(parameters.get(S3_SIGNATURE_VERSION))) {
			sb.append("    ");
			sb.append("aws configure set default.s3.signature_version " + parameters.get(S3_SIGNATURE_VERSION)); // s3v4
			sb.append("\n");
		}
		
		// if HTTP(s)
		if (parameters.get(IMAGE_URL).startsWith("http://") || parameters.get(IMAGE_URL).startsWith("https://")) {
			// download source image from HTTP(s) URL
			sb.append("    echo 'Downloading source image' > phase"); sb.append("\n");
			sb.append("    ");
			sb.append("curl -k -L -s --retry 5 --retry-delay 10 '" + parameters.get(IMAGE_URL) + "' -o " + SOURCE_IMAGE_FILE + " 2> download.out");
			sb.append(" || { ");
			sb.append("echo 'Cannot download source image from HTTP URL: " + parameters.get(IMAGE_URL) + "' > failure");
			sb.append(" ; exit 1 ; }"); sb.append("\n");
		} else if (parameters.get(IMAGE_URL).startsWith("s3://")) { 
			// download source image from S3
			if (!"".equals(parameters.get(S3_ENDPOINT_URL)) && !"".equals(parameters.get(S3_ACCESS_KEY)) && !"".equals(parameters.get(S3_SECRET_KEY))) {
				sb.append("    echo 'Downloading source image' > phase"); sb.append("\n");
				sb.append("    ");
				sb.append("aws --endpoint-url " + parameters.get(S3_ENDPOINT_URL) + " --no-verify-ssl s3 cp " + parameters.get(IMAGE_URL) + " " + SOURCE_IMAGE_FILE + " --quiet 2> download.out");
				sb.append(" || { ");
				sb.append("echo 'Cannot download source image from S3 URL: " + parameters.get(IMAGE_URL) + "' > failure");
				sb.append(" ; exit 1 ; }"); sb.append("\n");
			} else {
				sb.append("    echo 'Cannot download source image S3 URL " + parameters.get(IMAGE_URL) + " due to lack of parameters: " + S3_ENDPOINT_URL + ", " + S3_ACCESS_KEY + ", " + S3_SECRET_KEY + "' > failure "); sb.append("\n");
				sb.append("    exit 1"); sb.append("\n");
			}
		} else {
			sb.append("    echo 'Not supported protocol in source image URL:  " + parameters.get(IMAGE_URL) + "' > failure "); sb.append("\n");
			sb.append("    exit 1"); sb.append("\n");
		}
		
		// convert image format to qcow2
		if (parameters.get(IMAGE_FORMAT) != null && !"".equals(parameters.get(IMAGE_FORMAT)) && !"qcow2".equals(parameters.get(IMAGE_FORMAT))) {
			sb.append("    mv " + SOURCE_IMAGE_FILE + " " + SOURCE_IMAGE_FILE + "." + parameters.get(IMAGE_FORMAT)); sb.append("\n");
			sb.append("    qemu-img convert -f " + parameters.get(IMAGE_FORMAT) + " -O qcow2 " + SOURCE_IMAGE_FILE + "." + parameters.get(IMAGE_FORMAT) + " " + SOURCE_IMAGE_FILE + "");
			sb.append(" || { ");
			sb.append("echo 'Cannot convert source image to qcow2" + "' > failure");
			sb.append(" ; exit 1 ; }"); sb.append("\n");
			sb.append("    rm " + SOURCE_IMAGE_FILE + "." + parameters.get(IMAGE_FORMAT)); sb.append("\n");
		}
		
		// download validator script
		if (!"".equals(parameters.get(VALIDATOR_SCRIPT_URL))) {
			if (parameters.get(VALIDATOR_SCRIPT_URL).startsWith("http://") || parameters.get(VALIDATOR_SCRIPT_URL).startsWith("https://")) {
				sb.append("    echo 'Downloading validator script' > phase"); sb.append("\n");
				sb.append("    ");
				sb.append("curl -k -L -s '" + parameters.get(VALIDATOR_SCRIPT_URL) + "' -o " + VALIDATOR_SCRIPT_FILE + " 2> download.out");
				sb.append(" || { ");
				sb.append("echo 'Cannot download validator script from URL: " + parameters.get(VALIDATOR_SCRIPT_URL) + "' > failure");
				sb.append(" ; exit 1 ; }"); sb.append("\n");
				sb.append("    ");
				sb.append("chmod u+x " + VALIDATOR_SCRIPT_FILE);
				sb.append("\n");
			} else if (parameters.get(VALIDATOR_SCRIPT_URL).startsWith("s3://")) { 
					// download source image from S3
					if (!"".equals(parameters.get(S3_ENDPOINT_URL)) && !"".equals(parameters.get(S3_ACCESS_KEY)) && !"".equals(parameters.get(S3_SECRET_KEY))) {
						sb.append("    echo 'Downloading validator script' > phase"); sb.append("\n");
						sb.append("    ");
						sb.append("aws --endpoint-url " + parameters.get(S3_ENDPOINT_URL) + " --no-verify-ssl s3 cp " + parameters.get(VALIDATOR_SCRIPT_URL) + " " + VALIDATOR_SCRIPT_FILE + " --quiet 2> download.out");
						sb.append(" || { ");
						sb.append("echo 'Cannot download validator script from S3 URL: " + parameters.get(VALIDATOR_SCRIPT_URL) + "' > failure");
						sb.append(" ; exit 1 ; }"); sb.append("\n");
					} else {
						sb.append("    echo 'Cannot download validator script from S3 URL " + parameters.get(VALIDATOR_SCRIPT_URL) + " due to lack of parameters: " + S3_ENDPOINT_URL + ", " + S3_ACCESS_KEY + ", " + S3_SECRET_KEY + "' > failure "); sb.append("\n");
						sb.append("    exit 1"); sb.append("\n");
					}
			} else {
				sb.append("    echo 'Not supported protocol in validator script URL:  " + parameters.get(VALIDATOR_SCRIPT_URL) + "' > failure "); sb.append("\n");
				sb.append("    exit 1"); sb.append("\n");
			}
		}
		
		// export FS type parameters (absent on default ext2-ext4)
		if (!"".equals(parameters.get(IMAGE_ROOT_FILE_SYSTEM_TYPE))) {
			sb.append("    export FS_TYPE=" + parameters.get(IMAGE_ROOT_FILE_SYSTEM_TYPE)); sb.append("\n");
		}

		// mount source image
		sb.append("    echo 'Mounting source image' > phase"); sb.append("\n");
		sb.append("    ");
		sb.append(Configuration.scriptPrefix + "scripts/mountSourceImage.sh " + SOURCE_IMAGE_FILE + " " + SOURCE_FILE_SYSTEM_DIR + " " + parameters.get(IMAGE_ROOT_FILE_SYSTEM_PARTITION) + " &> mountImage.out"); 
		sb.append(" || { ");
		sb.append("echo 'Cannot mount source image " + SOURCE_IMAGE_FILE + " to directory " + SOURCE_FILE_SYSTEM_DIR + "' > failure");
		sb.append(" ; exit 1 ; }"); sb.append("\n");
		
		// start optimizer (optimize + create optimized image)
		sb.append("    echo 'Running shrinker' > phase"); sb.append("\n");
		sb.append("    ");
		sb.append("java $JAVA_OPTS -cp \"./lib/*:.\" hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker " + SOURCE_FILE_SYSTEM_DIR + " " + parameters.get(IMAGE_ID) + " " + VALIDATOR_SCRIPT_FILE + " &> " + SHRINKER_STDOUT);
		sb.append(" || { ");
		sb.append("echo Optimizer Java exit code: $? > failure");
		sb.append(" ; exit 1 ; }"); sb.append("\n");

		// unmount source image
		sb.append("    echo 'Unmounting source image' > phase"); sb.append("\n");
		sb.append("    ");
		sb.append(Configuration.scriptPrefix + "scripts/unmountSourceImage.sh " + SOURCE_IMAGE_FILE + " " + SOURCE_FILE_SYSTEM_DIR + " " + parameters.get(IMAGE_ROOT_FILE_SYSTEM_PARTITION) +  " &> unmountImage.out");
		sb.append(" || { ");
		sb.append("echo 'Cannot unmount source image " + SOURCE_IMAGE_FILE + " from directory " + SOURCE_FILE_SYSTEM_DIR + "' > failure");
		sb.append(" ; exit 1 ; }"); sb.append("\n");
			
		// make optimized image
		sb.append("    echo 'Creating optimized image' > phase"); sb.append("\n");
		sb.append("    ");
		sb.append(Configuration.scriptPrefix + "scripts/createOptimizedImage.sh " + SOURCE_IMAGE_FILE + " " + SOURCE_FILE_SYSTEM_DIR + " " + parameters.get(IMAGE_ROOT_FILE_SYSTEM_PARTITION) + " &> createOptimizedImage.out");
		sb.append(" || { ");
		sb.append("echo 'Cannot create optimized image file " + OPTIMIZED_IMAGE_FILE + " from source image " + SOURCE_IMAGE_FILE + "' > failure");
		sb.append(" ; exit 1 ; }"); sb.append("\n");
		
		String optimizedImageFileName = OPTIMIZED_IMAGE_FILE;
		
		// convert back optimized image to the input format
		if (parameters.get(IMAGE_FORMAT) != null && !"".equals(parameters.get(IMAGE_FORMAT)) && !"qcow2".equals(parameters.get(IMAGE_FORMAT))) {
			sb.append("    echo 'Converting optimized image' > phase"); sb.append("\n");
			sb.append("    qemu-img convert -O " + parameters.get(IMAGE_FORMAT) + " -f qcow2 " + OPTIMIZED_IMAGE_FILE + " " + OPTIMIZED_IMAGE_FILE + "." + parameters.get(IMAGE_FORMAT));
			sb.append(" || { ");
			sb.append("echo 'Cannot convert optimized image to "+  parameters.get(IMAGE_FORMAT) + "' > failure");
			sb.append(" ; exit 1 ; }"); sb.append("\n");
			optimizedImageFileName = OPTIMIZED_IMAGE_FILE + "." + parameters.get(IMAGE_FORMAT); 
			sb.append("    # rm " + OPTIMIZED_IMAGE_FILE); sb.append("\n");
		}
		
		// upload optimized image to S3
		sb.append("    echo 'Uploading optimized image' > phase"); sb.append("\n");
		sb.append("    ");
		if (!"".equals(parameters.get(S3_ENDPOINT_URL)) && !"".equals(parameters.get(S3_ACCESS_KEY)) && !"".equals(parameters.get(S3_SECRET_KEY)) && !"".equals(parameters.get(S3_PATH))) {}
		else sb.append("# "); // comment if no s3Path
		sb.append("aws --endpoint-url " + parameters.get(S3_ENDPOINT_URL) + " --no-verify-ssl s3 cp " + optimizedImageFileName + " s3://" + parameters.get(S3_PATH) + " --quiet 2> upload.out");
		sb.append(" || { ");
		sb.append("echo 'Cannot upload optimized image file " + optimizedImageFileName + " to S3 server " + parameters.get(S3_ENDPOINT_URL) + " with access key: " + parameters.get(S3_ACCESS_KEY) + ", secret key: " + (parameters.get(S3_SECRET_KEY) != null ? parameters.get(S3_SECRET_KEY).substring(0, 3) : parameters.get(S3_SECRET_KEY)) + "...' > failure");
		sb.append(" ; exit 1 ; }"); sb.append("\n");
	
		// econe-upload --access-key ahajnal@sztaki.hu --secret-key 60a... --url http://cfe2.lpds.sztaki.hu:4567 /mnt/optimized-image.qcow2
		sb.append("    ");
		sb.append("# econe-upload --access-key " + parameters.get(CLOUD_ACCESS_KEY) + " --secret-key " + parameters.get(CLOUD_SECRET_KEY) + " --url " + parameters.get(CLOUD_ENDPOINT_URL) + " " + optimizedImageFileName); sb.append("\n");

		sb.append("    ");
		if ("".equals(parameters.get(ID)) || Configuration.knowledgeBaseURL == null) sb.append("# ");
		sb.append("curl -X POST -k -L --retry 5 --retry-delay 10 --upload-file @" + optimizedImageFileName + " " + Configuration.knowledgeBaseURL + "/" + parameters.get(ID) + ""); sb.append("\n");

		// make optimized image
		sb.append("    echo 'Done' > phase"); sb.append("\n");

		// write public key (SSH from service -> optimizer)
		try {
		String pubKey = ResourceUtils.getResorceBase64Encoded(Configuration.SERVICE_SSH_KEY_PUBLIC_PART);
		sb.append("\n");
		sb.append("- path: /root/.ssh/authorized_keys"); sb.append("\n");
		sb.append("  permissions: \"0600\""); sb.append("\n");
		sb.append("  owner: \"root\""); sb.append("\n");
		sb.append("  encoding: \"base64\""); sb.append("\n");
		sb.append("  content: |"); sb.append("\n");
		sb.append("    " + pubKey);
		} catch (Exception x) { log.warn("Cannot read file: " + Configuration.SERVICE_SSH_KEY_PUBLIC_PART, x);} 
		
		// write private key
		sb.append("\n");
		sb.append("- path: /root/.ssh/id_rsa"); sb.append("\n");
		sb.append("  permissions: \"0600\""); sb.append("\n");
		sb.append("  owner: \"root\""); sb.append("\n");
		sb.append("  encoding: \"base64\""); sb.append("\n");
		sb.append("  content: |"); sb.append("\n");
		sb.append("    " + parameters.get(IMAGE_PRIVATE_KEY));

		// write validator script
		if (!"".equals(parameters.get(VALIDATOR_SCRIPT))) {
		sb.append("\n");
		sb.append("- path: " + VALIDATOR_SCRIPT_FILE); sb.append("\n");
		sb.append("  permissions: \"0700\""); sb.append("\n");
		sb.append("  encoding: \"base64\""); sb.append("\n");
		sb.append("  content: |"); sb.append("\n");
		sb.append("    " + parameters.get(VALIDATOR_SCRIPT));
		}
		
		// write aws s3 specific file
		if (!"".equals(parameters.get(S3_ACCESS_KEY)) && !"".equals(parameters.get(S3_SECRET_KEY))) {
		sb.append("\n");
		sb.append("- path: /root/.aws/config"); sb.append("\n");
		sb.append("  permissions: \"0600\""); sb.append("\n");
		sb.append("  owner: \"root\""); sb.append("\n");
		sb.append("  content: |"); sb.append("\n");
		sb.append("    [default]"); sb.append("\n");
		sb.append("    aws_access_key_id = " + parameters.get(S3_ACCESS_KEY)); sb.append("\n");
		sb.append("    aws_secret_access_key = " + parameters.get(S3_SECRET_KEY)); sb.append("\n");
		if (!"".equals(parameters.get(S3_REGION))) sb.append("    region = " + parameters.get(S3_REGION)); // note: don't specify region for s3.lpds.sztaki.hu
		}

		return sb.toString();
	}
	
	private String generateCloudInitRuncmd(String taskId) {
		StringBuilder sb = new StringBuilder();
		// check if runcmd runs in two copies
		sb.append("- test -f /root/.optimizer.lck && exit 0"); sb.append("\n");
		sb.append("- touch /root/.optimizer.lck"); sb.append("\n");

		// update and build sztaki-java-cli-utils and image-optimizer
		sb.append("# - cd /root/wp3-imagesynthesis/sztaki-java-cli-utils/"); sb.append("\n");
		sb.append("# - git pull"); sb.append("\n");
		sb.append("# - mvn install"); sb.append("\n");
		sb.append("# - cd /root/wp3-imagesynthesis/image-optimizer/"); sb.append("\n");
		sb.append("# - git pull"); sb.append("\n");
		sb.append("# - mvn install"); sb.append("\n");
		
		// create mount dirs: /mnt/source-file-system, /mnt/optimized-file-system
		sb.append("- mkdir -p " + SOURCE_FILE_SYSTEM_DIR); sb.append("\n");
		
		// start optimizer.sh
		sb.append("- cd /root"); sb.append("\n");
		sb.append("- echo 'Starting' > phase"); sb.append("\n");
		sb.append("- echo '" + taskId + "' > id"); sb.append("\n"); // write task id into file "id"
		sb.append("- ./optimize.sh &> optimize.out &"); sb.append("\n");
		// 'at' causes misc problems, don't use: sb.append("- at now + " + Configuration.optimizerCloudInitDelay + " min < /root/optimize.sh"); sb.append("\n");
		
		return sb.toString();
	}
	
	// remove task older than one day from cache
	public static void taskCacheCleanup() {
		long currentTime = System.currentTimeMillis();
		
		// pro: no running VMs remain + end time will be ok (+ 1 hour at most) con: VMs will be destroyed (no debugging/logs possible)
//		for (String id: taskCache.keySet()) { // get status of all running/stopping tasks
//			WebTarget webTarget = client.target("http://localhost:8080/image-optimizer-service/rest");
//			WebTarget resourceWebTarget = webTarget.path(id);
//			Invocation.Builder invocationBuilder = helloworldWebTargetWithQueryParam.request(MediaType.MediaType.APPLICATION_JSON);
//			Response response = invocationBuilder.get();
//		}
		
		long cacheLifetime = 24 * 60 * 60 * 1000l; // 24 hours (in millis)
		for (String id: taskCache.keySet()) {
			Task task = taskCache.get(id);
			if (task != null) synchronized(task) {
				if (currentTime - task.getCreated() > cacheLifetime) { 
					taskCache.remove(id);
					VM vm = optimizerVMCache.remove(id);
					if (vm != null) vm.discard();
				}
			}
		}
	}
	
	public static void taskCacheShutdown() {
		for (String id: taskCache.keySet()) {
			Task task = taskCache.get(id);
			if (task != null) synchronized(task) {
				VM vm = optimizerVMCache.remove(id);
				if (vm != null) vm.discard();
			}
		}
		taskCache.clear();
	}
	
	public static void main(String [] args) throws Exception {
	}
}