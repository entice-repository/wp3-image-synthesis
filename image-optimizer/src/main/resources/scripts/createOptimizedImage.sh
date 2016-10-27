#!/bin/bash

#	Copyright 2016 Akos Hajnal, MTA SZTAKI
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

#   Description: 
#   - mounts source-image in writable mode (more precisely, the given partition as /)
#   - executes removal script (rmme) 
#   - invokes zerofree (after re-mounting in read-only mode)
#   - converts image to the same format (qemu-img convert) to get rid of unused blocks
#   - the result is: /mnt/optimized-image.qcow2

#   Note: 
#   - this optimization does not change disk size, nor partition sizes (fdisk)
#   - this optimization does not chage file system size (resize2fs)
#   - the result is increased free space (on the given partition)
#   - less image file size (qcow2)
#   - works on ext2, ext3 and ext4 file systems
#   - frees up previously unused blocks too, not just current removables (rmme)

if [ "$#" -lt 2 ]; then echo "Usage: createOptimizedImageZerofree <image-file> <mount-point> ([partition-number] || <lvm-volume-group> <lvm-logical-volume>)" ; exit 243 ; fi

OPTIMIZED_IMAGE_FILE=/mnt/optimized-image.qcow2
DEVICE=/dev/nbd0
RMME_FILE=/root/rmme

IMAGE_FILE=$1
file $IMAGE_FILE | grep QCOW &> /dev/null || { echo "ERROR: QCOW input image expected"; exit 243 ; }

MOUNT_POINT=$2
mkdir -p $MOUNT_POINT

# load mbd kernel module (server) to allow of attaching image files as network block devices (/dev/nbd0 - /dev/nbd15)
echo Loading mbd kernel module ...
modprobe -a nbd &> /dev/null || { echo "ERROR: Cannot load NBD kernel module" ; exit 243 ; }
echo '  'mbd loaded

echo Attaching image $IMAGE_FILE as device $DEVICE ...
qemu-nbd -c $DEVICE $IMAGE_FILE || { echo "ERROR: Could not attach $DEVICE" ; rmmod nbd &> /dev/null ; exit 243 ; }
echo '  '$DEVICE attached 

DEVICE_PARTITION=$DEVICE
if [ "$#" -lt 4 ]; then
  PARTITION_NUMBER=1
  if [ "$#" -gt 2 ]; then PARTITION_NUMBER=$3 ; fi
  DEVICE_PARTITION=$DEVICE"p"$PARTITION_NUMBER
else
  VOLUME_GROUP=$3
  LOGICAL_VOLUME=$4
  vgscan &> /dev/null
  vgchange -a y $VOLUME_GROUP &> /dev/null
  DEVICE_PARTITION=/dev/$VOLUME_GROUP/$LOGICAL_VOLUME
fi

# phase 1: delete removables from source-image =============================================

# mount (writable) --------------------
echo Mounting $DEVICE_PARTITION on $MOUNT_POINT \(writable\) ...
mount $DEVICE_PARTITION $MOUNT_POINT || { echo "ERROR: Could not mount device $DEVICE_PARTIION on $MOUNT_POINT" ; qemu-nbd -d $DEVICE &> /dev/null ; rmmod nbd &> /dev/null ; exit 243 ; }
echo '  '$MOUNT_POINT mounted

# delete removables --------------------
echo Deleting removables \(/root/rmme\) ...
if [ -f $RMME_FILE ];
then
  $RMME_FILE $MOUNT_POINT
else
  { echo "ERROR: $RMME_FILE not found" ; umount $MOUNT_POINT &> /dev/null ; qemu-nbd -d $DEVICE &> /dev/null ; rmmod nbd &> /dev/null ; exit 243 ; }
fi
echo '  'Removables deleted

# unmount mount point --------------------
echo Unmounting $MOUNT_POINT ...
umount $MOUNT_POINT &> /dev/null || { echo "ERROR: Could not unmount $MOUNT_POINT" ; exit 243 ; }
echo '  '$MOUNT_POINT unmounted

# phase 1: done =============================================
# phase 2: zerofree, convert =============================================

# mount (read-only) --------------------
echo Mounting $DEVICE_PARTITION on $MOUNT_POINT \(read-only\) ...
mount -o ro $DEVICE_PARTITION $MOUNT_POINT || { echo "ERROR: Could not mount device $DEVICE_PARTIION on $MOUNT_POINT" ; qemu-nbd -d $DEVICE &> /dev/null ; rmmod nbd &> /dev/null ; exit 243 ; }
echo '  '$MOUNT_POINT mounted

# zerofree --------------------
echo Running zerofree on $DEVICE_PARTITION ...
zerofree $DEVICE_PARTITION
echo '  'done 

# unmount --------------------
echo Unmounting $MOUNT_POINT ...
umount $MOUNT_POINT &> /dev/null || { echo "ERROR: Could not unmount $MOUNT_POINT" ; exit 243 ; }
echo '  '$MOUNT_POINT unmounted

# unload logical volumes --------------------
if [ "$#" -gt 3 ]; then
  VOLUME_GROUP=$3
  # remove logical volume(s)
  vgchange -a n $VOLUME_GROUP &> /dev/null
fi

# convert "optimized" source image to optimized image --------------------
echo Converting $IMAGE_FILE to $OPTIMIZED_IMAGE_FILE ... 
qemu-img convert -O qcow2 $IMAGE_FILE $OPTIMIZED_IMAGE_FILE
echo '  '$OPTIMIZED_IMAGE_FILE created

# phase 2: done =============================================

# disconnect VM image from device nbd0 
echo Detaching $DEVICE ...
qemu-nbd -d $DEVICE &> /dev/null || { echo "ERROR: Could not detach device $DEVICE" ; exit 243 ; }
echo '  '$DEVICE detached

# unload mbd kernel module 
echo Unloading nbd ...
rmmod nbd &> /dev/null # || { echo "ERROR: Cannot unload NBD kernel module. See: lsmod." ; }
echo '  'nbd unloaded

echo Done