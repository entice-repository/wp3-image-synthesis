#!/bin/bash
echo Installing jdk, git, maven, curl, qemu...
apt-get install -y default-jdk git-all maven curl qemu-utils

echo Installing zerofree...
curl http://frippery.org/uml/zerofree-1.0.4.tgz -o zerofree-1.0.4.tgz
tar -xvzf zerofree-1.0.4.tgz
apt-get install -y build-essential e2fslibs-dev
cd zerofree-1.0.4
make
cp zerofree /usr/bin
rm -r zerofree-1.0.4.tgz
rm -r zerofree-1.0.4
cd ..

echo Installing AWS CLI...
curl https://s3.amazonaws.com/aws-cli/awscli-bundle.zip -o awscli-bundle.zip
unzip awscli-bundle.zip
./awscli-bundle/install -i /usr/local/aws -b /usr/local/bin/aws
apt-get install -y python-pip python-dev
pip install --upgrade pip
pip install awscli
rm -r awscli-bundle.zip
rm -r awscli-bundle

echo Downloading, building image optimizer source codes...
echo Building image optizer source codes
git clone https://github.com/entice-repository/wp3-image-synthesis.git
cd wp3-image-synthesis
git checkout -b develop origin/develop
cd ..
mvn install -f wp3-image-synthesis/sztaki-java-cli-utils/pom.xml -Dmaven.test.skip=true
mvn install -f wp3-image-synthesis/image-optimizer/pom.xml -Dmaven.test.skip=true

echo Creating symlinks to scripts...
mkdir -p scripts
cd scripts
CLI_UTILS_SCRIPT_PATH="../wp3-image-synthesis/sztaki-java-cli-utils/src/main/resources/scripts"
OPTIMIZER_SCRIPT_PATH="../wp3-image-synthesis/image-optimizer/src/main/resources/scripts"
ln -s $CLI_UTILS_SCRIPT_PATH/remoteexecute.sh
ln -s $OPTIMIZER_SCRIPT_PATH/createOptimizedImage.sh
ln -s $OPTIMIZER_SCRIPT_PATH/echoer.sh
ln -s $OPTIMIZER_SCRIPT_PATH/mountImage.sh
ln -s $OPTIMIZER_SCRIPT_PATH/testBasicVM.sh
ln -s $OPTIMIZER_SCRIPT_PATH/umountImage.sh
ln -s $OPTIMIZER_SCRIPT_PATH/createOptimizedVA.sh
ln -s $OPTIMIZER_SCRIPT_PATH/makeWPImage.sh
ln -s $OPTIMIZER_SCRIPT_PATH/mountSourceImage.sh
ln -s $OPTIMIZER_SCRIPT_PATH/rsynctest.sh
ln -s $OPTIMIZER_SCRIPT_PATH/testuptime.sh
ln -s $OPTIMIZER_SCRIPT_PATH/unmountSourceImage.sh
cd ..

echo Creating symlinks to libs...
mkdir -p lib
cd lib
CLI_TARGET_SCRIPT_PATH="../wp3-image-synthesis/sztaki-java-cli-utils/target"
OPTIMIZER_TARGET_PATH="../wp3-image-synthesis/image-optimizer/target"
MAVEN_REPO_PATH="../.m2/repository"
ln -s $CLI_TARGET_SCRIPT_PATH/cli-utils-0.0.1-SNAPSHOT.jar
ln -s $OPTIMIZER_TARGET_PATH/FunctionalOptimizer-0.1-SNAPSHOT.jar
ln -s $MAVEN_REPO_PATH/com/amazonaws/aws-java-sdk/1.6.12/aws-java-sdk-1.6.12.jar
ln -s $MAVEN_REPO_PATH/commons-codec/commons-codec/1.3/commons-codec-1.3.jar
ln -s $MAVEN_REPO_PATH/commons-logging/commons-logging/1.1.1/commons-logging-1.1.1.jar
ln -s $MAVEN_REPO_PATH/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar
ln -s $MAVEN_REPO_PATH/org/apache/httpcomponents/httpclient/4.2/httpclient-4.2.jar
ln -s $MAVEN_REPO_PATH/org/apache/httpcomponents/httpcore/4.2/httpcore-4.2.jar
ln -s $MAVEN_REPO_PATH/com/fasterxml/jackson/core/jackson-annotations/2.1.1/jackson-annotations-2.1.1.jar
ln -s $MAVEN_REPO_PATH/com/fasterxml/jackson/core/jackson-core/2.1.1/jackson-core-2.1.1.jar
ln -s $MAVEN_REPO_PATH/com/fasterxml/jackson/core/jackson-databind/2.1.1/jackson-databind-2.1.1.jar
ln -s $MAVEN_REPO_PATH/joda-time/joda-time/2.9.7/joda-time-2.9.7.jar
ln -s $MAVEN_REPO_PATH/extl/jadeclient/1.0/jadeclient-1.0.jar
ln -s $MAVEN_REPO_PATH/com/sun/jersey/jersey-bundle/1.19/jersey-bundle-1.19.jar
ln -s $MAVEN_REPO_PATH/com/sun/jersey/jersey-core/1.19/jersey-core-1.19.jar
ln -s $MAVEN_REPO_PATH/javax/ws/rs/jsr311-api/1.1.1/jsr311-api-1.1.1.jar
ln -s $MAVEN_REPO_PATH/org/json/json/20140107/json-20140107.jar
cd ..
