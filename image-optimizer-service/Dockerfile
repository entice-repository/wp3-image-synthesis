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