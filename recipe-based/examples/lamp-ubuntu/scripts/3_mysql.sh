#!/bin/bash -eux

export DEBIAN_FRONTEND=noninteractive

apt -y install \
	mysql-server \
	mysql-client