#!/bin/bash

#	Copyright 2009-2010 Gabor Kecskemeti, University of Westminster, MTA SZTAKI
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


#set -x
#echo $*
PRIVKEY=$1
IP=$2
PORT=$3
OUTPUTREQUIRED=$4
REALIP=$5
SCRIPT=$6
LOGIN=root
if [ $# -gt 6 ] ; then 
	LOGIN=$7
	shift 
fi
shift 6
SCRIPTLOC=`basename $SCRIPT` 
#.$RANDOM
SSHOPTS="-o ForwardX11=no -o ServerAliveInterval=5 -o ServerAliveCountMax=5 -o BatchMode=yes -o ConnectTimeout=30 -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i $PRIVKEY"

chmod +x $SCRIPT
TEEFILE=`mktemp -t remoteifconfig.XXXXXXXX`
[ "$REALIP" = "NOIPCHECK" ] || { ssh -Cp $PORT $SSHOPTS $LOGIN@$IP /sbin/ifconfig 2>&1 | tee $TEEFILE | grep $REALIP || { cat $TEEFILE ; rm $TEEFILE ; exit 253 ; } ; }
rm $TEEFILE

RETRIES=0
while ! rsync --log-file=/dev/null -qLpz -e "ssh -qp $PORT $SSHOPTS" $SCRIPT $LOGIN@$IP:/root/$SCRIPTLOC 2>&1 >> /dev/null
do
	sleep 1
	RETRIES=$((RETRIES+1))
	[ $RETRIES -gt 5 ] && exit 254
done
#scp -qCP $PORT $SSHOPTS $SCRIPT root@$IP:$SCRIPTLOC || exit 254

EXECPARAM="/root/$SCRIPTLOC $@"
[ $OUTPUTREQUIRED = "no" ] && EXECPARAM="$EXECPARAM 2>&1 >> /dev/null"
ssh -qCp $PORT $SSHOPTS $LOGIN@$IP "$EXECPARAM"
RETCODE=$?
if [ $RETCODE -eq 255 ]
then
	ssh -qCp $PORT $SSHOPTS $LOGIN@$IP "$EXECPARAM"
	RETCODE=$?
#	ssh -qCp $PORT $SSHOPTS $LOGIN@$IP rm /root/$SCRIPTLOC
	exit $RETCODE
else
#	ssh -qCp $PORT $SSHOPTS $LOGIN@$IP rm /root/$SCRIPTLOC
	exit $RETCODE
fi
