<%@page import="hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.rest.*" %>
<html><head><title>ENTICE Image Optimizer Service</title></head>
<style>
	body {
		font-family: "Arial"
	}
	table {
		border: 0px solid black; 
		min-width: 700px;
		border-collapse: collapse;
	}
	td {
		border:1px solid gray; 
	}
</style>
<body>
<h2>ENTICE Image Optimizer Service</h2>
<h3>version: <%=Configuration.version%>, codename: 'github'</h3>

<h4>Configuration:</h4>
<table>
	<tr><th>Property name</th><th>Value</th></tr>
	<tr><td>localEc2Endpoint</td><td><%=Configuration.localEc2Endpoint%></td></tr>
	<tr><td>cloudInterface</td><td><%=Configuration.cloudInterface%></td></tr>
	<tr><td>optimizerImageId</td><td><%=Configuration.optimizerImageId%></td></tr>
	<tr><td>optimizerInstanceType</td><td><%=Configuration.optimizerInstanceType%></td></tr>
	<tr><td>workerInstanceType</td><td><%=Configuration.workerInstanceType%></td></tr>
	<tr><td>rankerToUse</td><td><%=Configuration.rankerToUse%></td></tr>
	<tr><td>grouperToUse</td><td><%=Configuration.grouperToUse%></td></tr>
	<tr><td>maxUsableCPUs</td><td><%=Configuration.maxUsableCPUs%></td></tr>
	<tr><td>parallelVMNum</td><td><%=Configuration.parallelVMNum%></td></tr>
	<tr><td>vmFactory</td><td><%=Configuration.vmFactory%></td></tr>
	<tr><td>scriptPrefix</td><td><%=Configuration.scriptPrefix%></td></tr>
	<tr><td>optimizerRootLogin</td><td><%=Configuration.optimizerRootLogin%></td></tr>
</table>


<h4>Service request example (POST):</h4>
<pre>
curl -k -X POST --upload-file input.json https://entice.lpds.sztaki.hu:8443/image-optimizer-service/rest/optimizer
>> 9edd8e87-e2b4-4eee-b7fd-6e18a7c1a47c

input.json:
{
	imageId: 'ami-00001483',
	imageURL: 'https://images.s3.lpds.sztaki.hu/wordpress.qcow2',
	validatorScriptURL: 'https://images.s3.lpds.sztaki.hu/wordpress.sh',
	imageUserName: 'root',
	fsPartition: 'vg0 root',
	imageKeyPair: 'ahajnal_keypair',
	imagePrivateKey: 'LS0...',

	cloudEndpointURL: 'http://cfe2.lpds.sztaki.hu:4567',
	cloudAccessKey: 'ahajnal@sztaki.hu',
	cloudSecretKey: '***',
	cloudOptimizerVMInstanceType: 'm1.medium',
	cloudWorkerVMInstanceType: 'm1.small',

	freeDiskSpace: 100,
	numberOfParallelWorkerVMs: 8,
	maxIterationsNum: 1,
	XmaxNumberOfVMs: 8,
	XaimedReductionRatio: 0.8,
	XaimedSize: 1073741824,
	XmaxRunningTime: 36000,

	s3EndpointURL: 'https://s3.lpds.sztaki.hu',
	s3AccessKey: 'ahajnal',
	s3SecretKey: '***',
	s3Path: 'images/optimized-image.qcow2'
}
</pre>

<h4>Status response example (GET):</h4>
<pre>
curl -k -X GET https://entice.lpds.sztaki.hu:8443/image-optimizer-service/rest/optimizer/9edd8e87-e2b4-4eee-b7fd-6e18a7c1a47c
>>

{
	"maxNumberOfVMs": 0,
	"status": "DONE",
	"optimizerPhase": "Done",
	"optimizerVMStatus": "terminated",
	"shrinkerPhase": "done",
	"failure": "",
	"optimizedImageURL": "https://s3.lpds.sztaki.hu/images/optimized-image.qcow2",

	"started": 1472466552949,
	"ended": 1472467475553,
	"runningTime": 922,
	"maxRunningTime": 0,

	"originalImageSize": 2333278208,
	"optimizedImageSize": 1387986944,
	"originalUsedSpacee": 1206305662,
	"optimizedUsedSpace": 927930660,
	"removables": "lib/modules/3.2.0-4-amd64, /lib/modules/3.2.0-4-686-pae, /lib/modules/3.2.0-4-486, /usr/src",
	
	"chart":[[0,1473322556474,1206305662,1206305662],[1,1473323347132,927930660,1206305662]],

	"aimedReductionRatio": 0,
	"aimedSize": 0,
	"iteration": 1,
	"maxIterationsNum": 1,
	"numberOfVMsStarted": 8,
}
</pre>

</body></html>