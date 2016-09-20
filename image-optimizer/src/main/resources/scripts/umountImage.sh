#!/bin/bash

#       Copyright 2009-10 Gabor Kecskemeti, University of Westminster, MTA SZTAKI
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

# Parameters:
#  1. complete path to the VMI to be mounted
#  2. mount point

if [ "$#" -ne 2 ]; then
	echo "Illegal number of parameters"
	exit 243
fi

modprobe nbd

SOURCE_IMGFN=$1
MOUNT_TARGET=$2

#Original VA handling
VOLID=`mount | grep "$MOUNT_TARGET" | cut -f1 -d' ' | cut -f4 -d/ | cut -f1 -d-`
umount $MOUNT_TARGET
vgchange -an $VOLID &> /dev/null
IMGFNBASENAME=`basename $SOURCE_IMGFN`
nbdpid=`ps ux | awk "/qemu-nbd/&&/$IMGFNBASENAME/&&!/awk/ { print \\$2 } "`
kill $nbdpid
