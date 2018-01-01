FROM ubuntu:14.04

#
# !!! There is no suppor for KVM/QEMU as we are running in a container
#
# Volume:
# - /tmp/datadir

ARG GIT_BRANCH=develop
ARG GIT_REPO=https://github.com/entice-repository/wp3-image-synthesis.git

ENV GIT_EXPORT_DIR /root/wp3-image-synthesis

RUN \
	apt-get update && \
	apt-get -y upgrade && \
	apt-get -y install libyaml-0-2 libyaml-dev qemu-utils && \
	apt-get -y install git

RUN \ 
	apt-get -y install python2.7 python2.7-dev python-pip 

# Workaround for caching git clone
RUN \
	dd if=/dev/urandom bs=16 count=1 >/dev/null 2>&1 | md5sum > /md5sum-noncache && \
	git clone -b ${GIT_BRANCH} ${GIT_REPO} ${GIT_EXPORT_DIR}

# Workaround for 'error in cryptography setup command: Invalid environment marker: python_version < '3''
RUN \
	pip install --upgrade setuptools && \
	apt-get install -y libffi-dev libssl-dev

RUN cd ${GIT_EXPORT_DIR}/recipe-based/frontend/ && \
	cp -r config-sample.py config.py && \
	pip install --upgrade pip && \
	pip install -r requirements.txt

# Application is always running on port 4000 in the container
EXPOSE 4000
COPY docker-entrypoint.sh /
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD  ["./run-builder-fe-dev.py"]
