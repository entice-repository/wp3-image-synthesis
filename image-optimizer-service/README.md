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

# INPUT: image-optimizer-service.properties file with fields 'localEc2Endpoint', 'optimizerImageId' updated (https://github.com/entice-repository/wp3-image-synthesis/blob/master/image-optimizer-service/src/main/resources/image-optimizer-service.properties)
# BUILD: docker build -t ios .
# RUN: docker run -d -p 8080:8080 -p 8443:8443 ios
# TEST: curl --insecure https://localhost:8443/image-optimizer-service/

FROM ubuntu:16.04
RUN apt-get update
MAINTAINER akos.hajnal@sztaki.mta.hu

# install mysql (without prompting for root password)...
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get install -y \
    apt-utils \
    debconf-utils \
    mysql-server \
    mysql-client && \
    usermod -d /var/lib/mysql/ mysql

# install java, git, maven, curl...
RUN apt-get install -y \
    default-jdk \
    git-all \
    maven \
    curl

# install tomcat7...
RUN apt-get install -y \
    tomcat7 \
    tomcat7-admin && \
    ln -s /var/lib/tomcat7/server/ /usr/share/tomcat7/ && ln -s /var/lib/tomcat7/common/ /usr/share/tomcat7/ && ln -s /var/lib/tomcat7/shared/ /usr/share/tomcat7/ && \
    printf "\
    export CATALINA_HOME=\"/usr/share/tomcat7\"\n\
    export CATALINA_BASE=\"/var/lib/tomcat7\"\n\
    export JAVA_OPTS=\"-Xmx1024m -Xms512m -Djava.security.egd=file:///dev/urandom\"\n" > /usr/share/tomcat7/bin/setenv.sh

# download, compile and deploy image optimizer service in tomcat (with HTTPS connector) with the provided properties file...
RUN cd /tmp && git clone https://github.com/entice-repository/wp3-image-synthesis.git
COPY image-optimizer-service.properties /tmp/wp3-image-synthesis/image-optimizer-service/src/main/resources/image-optimizer-service.properties
RUN mvn install -f /tmp/wp3-image-synthesis/image-optimizer-service/pom.xml && \
    cp /tmp/wp3-image-synthesis/image-optimizer-service/target/image-optimizer-service.war /var/lib/tomcat7/webapps/ && \
    curl -L -sS -o /usr/share/tomcat7/lib/mysql-connector-java-5.1.34.jar https://repo1.maven.org/maven2/mysql/mysql-connector-java/5.1.34/mysql-connector-java-5.1.34.jar && \
    keytool -genkey -noprompt -alias tomcat -keyalg RSA -keystore /etc/ssl/certs/java/cacerts/ -dname "CN=entice, OU=ID, O=ENTICE, L=Brussels, S=Belgium, C=BE" -storepass changeit -keypass changeit && \
    sed -i '/<!-- A "Connector" using the shared thread pool-->/a\    <Connector port="8443" protocol="org.apache.coyote.http11.Http11Protocol" maxThreads="150" scheme="https" secure="true" SSLEnabled="true" keystoreFile="/etc/ssl/certs/java/cacerts" keystorePass="changeit" clientAuth="false" sslProtocol="TLS" />' /var/lib/tomcat7/conf/server.xml
#   sed -i '/<tomcat-users>/a\  <user username="optimizer" password="optimizer" roles="manager-gui"/>' /var/lib/tomcat7/conf/tomcat-users.xml

# create optimizer database...
RUN service mysql start && mysql -u root -e "create database optimizer; create user 'optimizer'@'localhost' IDENTIFIED BY 'entice'; grant all privileges on optimizer.* TO 'optimizer'@'localhost'; flush privileges;"

# expose ports, start mysql and tomcat
EXPOSE 8080
EXPOSE 8443
CMD service mysql start > /dev/null && /usr/share/tomcat7/bin/startup.sh > /dev/null && echo Image optimizer service started... && tail -f /dev/null
