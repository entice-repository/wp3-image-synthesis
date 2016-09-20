#!/bin/bash
HOSTNAME=$1
USERNAME=root # defaults to "root"
if [ "$#" -gt 1 ]; then
	USERNAME=$2
fi

PRIVKEY=/root/.ssh/id_rsa
SSHOPTS="-o ForwardX11=no -o ServerAliveInterval=5 -o ServerAliveCountMax=5 -o BatchMode=yes -o ConnectTimeout=30 -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i $PRIVKEY"

touch /tmp/testfile
rsync -qLpz -e "ssh -q $SSHOPTS" /tmp/testfile $USERNAME@$HOSTNAME:/tmp/testfile || exit 30
 