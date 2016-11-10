#!/bin/sh

#	Copyright 2009-2010 Gabor Kecskemeti, University of Westminster, MTA SZTAKI, Akos Hajnal, MTA SZTAKI
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

IP=$1
PORT=$2
INTIP=$3
PRIVKEY=$4
LOGIN=root
if [ $# -gt 4 ] ; then LOGIN=$5 ; fi

SSHOPTS="-o ForwardX11=no -o ServerAliveInterval=5 -o ServerAliveCountMax=5 -o BatchMode=yes -o ConnectTimeout=5 -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i $PRIVKEY"

for retries in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18
do
	echo NC start
	if nc -z $IP $PORT
	then
		echo NC done, ssh start
#		note: do not grep $INTIP in ifconfig, because private IP may be a hostname (OpenStack)
		ssh $SSHOPTS $LOGIN@$IP /sbin/ifconfig && exit 0
	fi
	# Wait for some time to allow ssh to come up
	sleep 10
done
exit 1
