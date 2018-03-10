#!/bin/bash -eux

export DEBIAN_FRONTEND=noninteractive

apt -y install \
	apache2 \
	php \
	libapache2-mod-php \
	php-{bcmath,bz2,intl,gd,mbstring,mcrypt,mysql,zip}


