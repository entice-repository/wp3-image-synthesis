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
	apt-get -y install libyaml-0-2 libyaml-dev qemu-utils git

RUN git clone -b ${GIT_BRANCH} ${GIT_REPO} ${GIT_EXPORT_DIR}

RUN \ 
	apt-get -y install python2.7 python2.7-dev python-pip 

RUN cd ${GIT_EXPORT_DIR}/recipe-based/backend/ && \
	cp -r config-sample.py config.py && \
	pip install --upgrade pip && \
	pip install -r requirements.txt

CMD  cd /root/wp3-image-synthesis/recipe-based/backend/ && ./run-builder-be-dev.py
