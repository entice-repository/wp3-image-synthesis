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

if [ "$#" -lt 2 ]; then echo "Usage: mountSourceImage <image-file> <mount-point> ([partition-number] || <lvm-volume-group> <lvm-logical-volume>)" ; exit 243 ; fi

IMAGE_FILE=$1
file $IMAGE_FILE | grep QCOW &> /dev/null || { echo "ERROR: QCOW input image expected"; exit 243 ; }

MOUNT_POINT=$2
mkdir -p $MOUNT_POINT

DEVICE=/dev/nbd0

# load mbd kernel module (server) to allow of attaching image files as network block devices (/dev/nbd0 - /dev/nbd15)
modprobe -a nbd &> /dev/null || { echo "ERROR: Cannot load NBD kernel module" ; exit 243 ; }

# connect VM image as device nbd0 using qemu-nbd
qemu-nbd --read-only -c $DEVICE $IMAGE_FILE || { echo "ERROR: Could not attach $DEVICE" ; rmmod nbd &> /dev/null ; exit 243 ; }

if [ "$#" -lt 4 ]; then

  PARTITION_NUMBER=1
  if [ "$#" -gt 2 ]; then PARTITION_NUMBER=$3 ; fi

  # mount device partition to mount point
  mount -o ro,noload $DEVICE"p"$PARTITION_NUMBER $MOUNT_POINT || { echo "ERROR: Could not mount device $DEVICEp$PARTITION_NUMBER onto $MOUNT_POINT" ; qemu-nbd -d $DEVICE &> /dev/null ; rmmod nbd &> /dev/null ; exit 243 ; }

  echo $IMAGE_FILE mounted on $MOUNT_POINT \($DEVICE"p"$PARTITION_NUMBER\)

else

  VOLUME_GROUP=$3
  LOGICAL_VOLUME=$4

  # scan for LVM volumes
  vgscan &> /dev/null
  vgchange -a y $VOLUME_GROUP &> /dev/null

  # mount device partition to mount point
  mount -o ro,noload /dev/$VOLUME_GROUP/$LOGICAL_VOLUME $MOUNT_POINT || { echo "ERROR: Could not mount device /dev/$VOLUME_GROUP/$LOGICAL_VOLUME onto $MOUNT_POINT" ; qemu-nbd -d $DEVICE &> /dev/null ; rmmod nbd &> /dev/null ; exit 243 ; }

  echo $IMAGE_FILE mounted on $MOUNT_POINT \(/dev/$VOLUME_GROUP/$LOGICAL_VOLUME\) 

fi