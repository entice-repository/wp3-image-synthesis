#!/bin/bash

#	Copyright 2009-16 Gabor Kecskemeti, University of Westminster, MTA SZTAKI
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

set -x

if [ "$#" -lt 3 ]; then
	echo "Illegal number of parameters"
	exit 243
fi

modprobe nbd

SOURCE_IMGFN=$1
RMME_SCRIPT=$2
TARGET_IMGFN=$3
FREE_DISK_SPACE=0
if [ "$#" -gt 3 ]; then
	FREE_DISK_SPACE=$4
fi

#this below could be created with losetup
SOURCE_DEVNAME=/dev/nbd0
TARGET_DEVNAME=/dev/nbd1

basedir=`mktemp -td appliancecreator.XXXXXXXXX`
cd $basedir
IMGFN_BASE=$basedir/`basename $SOURCE_IMGFN`
TEMP_SUFFIX=modified
OPT_SUFFIX=optimised
PART_SUFFIX=partitions
VG_SUFFIX=groups
MNT_SUFFIX=mnt
IMGFN_TEMP=${IMGFN_BASE}.${TEMP_SUFFIX}
IMGFN_OPT=${IMGFN_BASE}.${OPT_SUFFIX}
PARTLAYOUT=${IMGFN_BASE}.${PART_SUFFIX}
PARTLAYOUT_NEW=${IMGFN_BASE}.${OPT_SUFFIX}.${PART_SUFFIX}
VGLAYOUT=${IMGFN_BASE}.${VG_SUFFIX}
VGLAYOUT_NEW=${IMGFN_BASE}.${OPT_SUFFIX}.${VG_SUFFIX}
MP_TEMP=${IMGFN_BASE}.${TEMP_SUFFIX}.${MNT_SUFFIX}
MP_OPT=${IMGFN_BASE}.${OPT_SUFFIX}.${MNT_SUFFIX}
DMAP=$MP_OPT/boot/grub2/device.map
DMAP_TEMP=${basedir}/dmap.temp


#Original VA handling
file $SOURCE_IMGFN | grep QCOW || { echo "ERROR: QCOW input image expected"; exit 243 ; }
mkdir $MP_TEMP
mkdir $MP_OPT
#Keep the original file and maintain an internal copy to be modified
cp $SOURCE_IMGFN $IMGFN_TEMP
qemu-nbd -c $SOURCE_DEVNAME  $IMGFN_TEMP || { echo "Could not attach original qcow2 file" ; exit 243 ; }
TEMP_NBDPID=`ps ux | awk "!/awk/&&/$TEMP_SUFFIX/ {print \\$2}"`
ORIG_SECCOUNT=`blockdev --getsize $SOURCE_DEVNAME`
ORIG_SIZE=$(((512*ORIG_SECCOUNT+999999)/1000000)) # original image in mb, ceil
sfdisk -d $SOURCE_DEVNAME > $PARTLAYOUT
ORIG_LASTPARTLEN=`awk '/start/&&!/Id= 0/ {lastplen=$6 }; END {print lastplen}' $PARTLAYOUT | cut -d, -f1` # size of last partition (to be resized!)
VOLID=`vgscan | awk -F\" '/Found volume/&&!/ubuntu-vg/ { print $2 }'` # optimizer vm is ubuntu (otherwise change ububtu-vg!)
VOLID_TEMP=${VOLID}-TEMP
vgcfgbackup -f $VGLAYOUT $VOLID
vgchange -ay $VOLID
# Here we should list Volumes and mount all that is mountable. now we assume just root's presence
mount /dev/$VOLID/root $MP_TEMP
freespace=`df -Pm $MP_TEMP | tail -1 | awk '{print $4}'`

#Removal script execution
cp $RMME_SCRIPT $MP_TEMP
rmmeBN=`basename $RMME_SCRIPT`
chmod +x $MP_TEMP/$rmmeBN
$MP_TEMP/$rmmeBN $MP_TEMP/ || { umount $MP_TEMP ; vgchange -an $VOLID ; kill $TEMP_NBDPID; rm $IMGFN_TEMP ; exit 243 ; }
rm $MP_TEMP/$rmmeBN
usedspace=`df -Pm $MP_TEMP | tail -1 | awk '{print $3}'`
freespafter=`df -Pm $MP_TEMP | tail -1 | awk '{print $4}'`

echo Original image size: $ORIG_SIZE MB
echo Free disk space before optimization: $freespace MB
echo Free disk space after optimization: $freespafter MB

#New disk image creation
if [ "$FREE_DISK_SPACE" -ne 0 ]; then 
	freespace=$FREE_DISK_SPACE
	echo User-defined free disk space: $FREE_DISK_SPACE MB
fi

OPT_SIZE=$((ORIG_SIZE-freespafter+freespace)) 
echo Image size after optimzation: $OPT_SIZE MB
qemu-img create -f qcow2 -o preallocation=off $IMGFN_OPT ${OPT_SIZE}000000
qemu-nbd -c $TARGET_DEVNAME $IMGFN_OPT  || { echo "Could not attach just created qcow2 file" ; exit 243 ; }
#dd if=$SOURCE_DEVNAME of=$TARGET_DEVNAME bs=512 count=1 #substituted below with the larger dd
IMGFN_OPT_MARSH=`echo $IMGFN_OPT | sed s+/+\\\\\\\\/+g`
OPT_NBDPID=`ps ux | awk "!/awk/&&/$IMGFN_OPT_MARSH/ {print \\$2}"`
NEW_SECCOUNT=`blockdev --getsize $TARGET_DEVNAME`
#Nasty stuff begins (wp image specific)
NEW_LASTPARTLEN=`awk -v totlen=$NEW_SECCOUNT '/start/&&!/Id= 0/ {tot=totlen-$4 }; END {print tot}' $PARTLAYOUT`
NEW_LASTPARTSTART=`awk '/start/&&!/Id= 0/ {lastplen=$4 }; END {print lastplen}' $PARTLAYOUT | cut -d, -f1`
sed s/$ORIG_LASTPARTLEN/$NEW_LASTPARTLEN/ $PARTLAYOUT > $PARTLAYOUT_NEW
dd if=$SOURCE_DEVNAME of=$TARGET_DEVNAME bs=512 count=$NEW_LASTPARTSTART conv=noerror,sync #Overwrites mbr + first partition
sfdisk --force $TARGET_DEVNAME < $PARTLAYOUT_NEW
kill $OPT_NBDPID
qemu-nbd -c $TARGET_DEVNAME $IMGFN_OPT  || { echo "Could not attach just created qcow2 file" ; exit 243 ; }
OPT_NBDPID=`ps ux | awk "!/awk/&&/$OPT_SUFFIX/ {print \\$2}"`
ls -l ${TARGET_DEVNAME}*
DDPARTSOURCE=`awk '/bootable/ {print $1}' $PARTLAYOUT`
DDPARTTARGET=`echo $DDPARTSOURCE | sed s+$SOURCE_DEVNAME+$TARGET_DEVNAME+`
VGPARTTARGET=`echo $DDPARTTARGET | sed s/p1$/p2/`
#dd if=$DDPARTSOURCE of=$DDPARTTARGET bs=512 conv=noerror,sync # substituted above with the larger dd
SWAPLEN=`lvdisplay $VOLID --units m | awk '/LV Name/ && !/swap/ {swapmarker=0}; /LV Name/ && /swap/ {swapmarker=1}; swapmarker==1&&/LV Size/ { print $3 }'`
vgscan
pvcreate $VGPARTTARGET
vgcreate $VOLID_TEMP $VGPARTTARGET
lvcreate -L ${SWAPLEN}m -n swap $VOLID_TEMP
lvcreate -l 100%FREE -n root $VOLID_TEMP
mkswap /dev/$VOLID_TEMP/swap
mkfs.ext4 -qF /dev/$VOLID_TEMP/root
mount /dev/$VOLID_TEMP/root $MP_OPT
cp -ax $MP_TEMP/* $MP_OPT
#Done copying. now we just need to clean up end ensure bootability

umount $MP_OPT
umount $MP_TEMP
vgchange -an $VOLID
vgchange -an $VOLID_TEMP
kill $TEMP_NBDPID
rm $IMGFN_TEMP

vgscan
vgrename $VOLID_TEMP $VOLID

vgchange -ay $VOLID
mount /dev/$VOLID/root $MP_OPT
mount $DDPARTTARGET ${MP_OPT}/boot
mount --bind /dev ${MP_OPT}/dev
cp $DMAP $DMAP_TEMP
echo "(hd0) $DEVNAMEFIX" > $DMAP
chroot $MP_OPT grub-install $DEVNAMEFIX 
cp $DMAP_TEMP $DMAP
umount ${MP_OPT}/dev
umount ${MP_OPT}/boot
umount $MP_OPT
vgchange -an $VOLID

kill $OPT_NBDPID

cd -
#Saving the image
mv $IMGFN_OPT $TARGET_IMGFN
#Dropping temporary data
rm -r $basedir
