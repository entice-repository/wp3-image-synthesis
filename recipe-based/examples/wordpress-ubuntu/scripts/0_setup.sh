#!/bin/bash -eux

export DEBIAN_FRONTEND=noninteractive

apt -y update && apt-get -y upgrade

sed -i "s/^.*requiretty/#Defaults requiretty/" /etc/sudoers

# Disable daily apt unattended updates.
echo 'APT::Periodic::Enable "0";' >> /etc/apt/apt.conf.d/10periodic