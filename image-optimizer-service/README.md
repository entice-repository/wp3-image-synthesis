# Image Optimizer Service

## Endpoint 
https://IP:8443/image-optimizer-service/rest/

## Resource: 'optimizer'
### Methods: POST, GET, PUT, DELETE

------------------------------------------------------------

### POST
Start new optimization task

- input: entity body application/json (input parameters JSON object)
- output: entity body string (task id)

Example:

> $ curl -k -H "Content-Type: application/json" -d "{imageId: 'ami-00001459', ...}" https://IP:8443/image-optimizer-service/rest/optimizer

> $ curl -k -X POST -H "Content-Type: application/json" --upload-file inputs.json https://IP:8443/image-optimizer-service/rest/optimizer

> 67aaf6c7-37e0-4611-82dd-f9e5436d8c56

Input JSON object fields

> {
> imageURL: 'http://…', # location of the original, un-optimized image   
> 
> imageId: 'ami-00001459',  # image id if it is already in the optimizer's cloud
> 
> imageContextualizationURL: '', # location of cloud-init file to be used to contextualize
> 
> imageLogin: ‘root’, # default user with root permissions, contextualized with cloudKeyPair
> 
> 
> validatorScript: 'BASE64 encoded text', # test script source in encoded form
> 
> validatorScriptURL: 'http://…/validator-script.sh', # location of the validation script
> 
> validatorImageURL: '', # image of the validator VM
> 
> 
> cloudEndpointURL: 'http://cfe2.lpds.sztaki.hu:4567', # EC2 enpoint URL
> 
> cloudAccessKey: 'ahajnal@sztaki.hu', # EC2 access key of the user
> 
> cloudSecretKey: '2af…', # EC2 secret key of the user
> 
> cloudKeyPair: 'mykeypair', # EC2 key pair to be used to contextualize VMs
> 
> cloudPrivateKey: 'BASE64encoded private key', # cloudKeyPair’s private part
> 
> cloudVMInstanceType: 'm1.medium', # worker VM type in (m1.small, m1.medium, …)
> 
> 
> s3EndpointURL: 'https://s3.lpds.sztaki.hu', # S3 endpoint URL where to upload the optimized image
>  
> s3AccessKey: 'ahajnal', # S3 access key of the user
> 
> s3SecretKey: '2af…', # S3 secret key of the user
> 
> s3Path: 'mybucket/myimage', # object name of the optimized image including bucket name
> 
> s3Region: '', # S3 region
> 
> 
> maxIterationsNum: 12, # stop at reaching this iteration number
> 
> maxNumberOfVMs: 20, # stop when the number of started VMs would exceed this limit
> 
> aimedReductionRatio: 0.8, # stop when size of optimized image reaches 0.8X of the original image
> 
> aimedSize: 1073741824, # stop when size of optimized image reaches 1GB,
> 
> maxRunningTime: 36000 # stop optimization no later than this value (in seconds)
> 
> }


------------------------------------------------------------

### GET
Get status of the optimization task

- input: path parameter string (task id)
- output: entity body application/json

Example:

> $ curl -k https://IP:8443/image-optimizer-service/rest/optimizer/67aaf6c7-37e0-4611-82dd-f9e5436d8c56

> {
  status: 'running',
  iterations: 12,
  numberOfVMsStarted: 43,
  originalImageSize: 2473741824,
  currentImageSize: 1073741824,
  runningTime: 145,
  chart: [[0, 2473741824], [1, 2373741824], ..., [12, 1073741824]]
}

> {
  status: 'done',
  iterations: 18,
  numberOfVMsStarted: 49,
  originalImageSize: 2473741824,
  optimizedImageSize: 1073741824,
  runningTime: 245,
  chart: [[0, 2473741824], [1, 2373741824], ..., [12, 1073741824]],
  optimizedImageURL: 'http://s3.lpds.sztaki.hu/images/67aaf6c7-37e0-4611-82dd-f9e5436d8c56'
}

------------------------------------------------------------

### PUT
Stop the optimization task, save sub-optimal image

- input: path parameter string (task id)
- output: HTTP status code

Example:

> curl -k -X PUT https://IP:8443/image-optimizer-service/rest/optimizer/67aaf6c7-37e0-4611-82dd-f9e5436d8c56

> HTTP/1.1 200 OK

------------------------------------------------------------

### DELETE
Abort the optimization task, drop intermediate results

- input: path parameter string (task id)
- output: HTTP status code

Example:

> $ curl -k -X DELETE https://IP:8443/image-optimizer-service/rest/optimizer/67aaf6c7-37e0-4611-82dd-f9e5436d8c56

> HTTP/1.1 200 OK


## DOCKERFILE

See file Dockerfile