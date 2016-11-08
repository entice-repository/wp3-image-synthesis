#!/bin/bash

#	Copyright 2016 Gabor Kecskemeti, Akos Hajnal  MTA SZTAKI
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

if [ "$#" -lt 2 ]; then	echo "Usage: unmountSourceImage <image-file> <mount-point> ([partition-number] || <lvm-volume-group> <lvm-logical-volume>)" ; exit 243 ; fi

MOUNT_POINT=$2

DEVICE=/dev/nbd0

# unmount mount point
umount $MOUNT_POINT &> /dev/null || { echo "ERROR: Could not unmount $MOUNT_POINT" ; exit 243 ; }

if [ "$#" -gt 3 ]; then

  VOLUME_GROUP=$3

  # remove logical volume(s)
  vgchange -a n $VOLUME_GROUP &> /dev/null
fi

# disconnect VM image from device nbd0 
qemu-nbd -d $DEVICE &> /dev/null || { echo "ERROR: Could not detach device $DEVICE" ; exit 243 ; }

# unload mbd kernel module 
rmmod nbd &> /dev/null # || { echo "ERROR: Cannot unload NBD kernel module. See: lsmod." ; }

if [ "$#" -lt 4 ]; then

  echo $MOUNT_POINT unmounted \($DEVICE\) 
  
else

  LOGICAL_VOLUME=$4
  echo $MOUNT_POINT unmounted \(/dev/$VOLUME_GROUP/$LOGICAL_VOLUME\) 
  
fi
