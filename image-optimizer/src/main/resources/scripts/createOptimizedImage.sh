#!/bin/bash
set -x

# parameters
if [ "$#" -ne 3 ]; then
	echo "Illegal number of parameters"
	exit 243
fi
SOURCE_IMAGE_FILE=$1
RMME_SCRIPT=$2
OPTIMIZED_IMAGE_FILE=$3
RMME_SCRIPT=/root/rmme
SOURCE_IMAGE_FILE=/mnt/source-image.qcow2
OPTIMIZED_IMAGE_FILE=/mnt/optimized-image.qcow2
SOURCE_FILE_SYSTEM_DIR=/mnt/source-file-system
OPTIMIZED_FILE_SYSTEM_DIR=/mnt/optimized-file-system

# check parameters
file $SOURCE_IMAGE_FILE | grep QCOW || { echo "ERROR: QCOW input image expected"; exit 243 ; }

# load mbd kernel module (server) to allow of attaching image files as network block devices (/dev/nbd0 - /dev/nbd15)
modprobe -av nbd || { echo "ERROR: Cannot load NBD kernel module" ; exit 243 ; }

# create working dirs
mkdir -p $SOURCE_FILE_SYSTEM_DIR
mkdir -p $OPTIMIZED_FILE_SYSTEM_DIR

# === source image analysis ===
qemu-nbd -c /dev/nbd0  $SOURCE_IMAGE_FILE || { echo "Could not attach original qcow2 file" ; exit 243 ; }
SOURCE_IMAGE_SECTORS=`blockdev --getsize /dev/nbd0` # get number of sectors of the original image
SOURCE_IMAGE_SIZE=$(((512*SOURCE_IMAGE_SECTORS+999999)/1000000)) # ceil of original image size (sectors * 512 bytes) in megabytes (10^6)
sfdisk -d /dev/nbd0 > $SOURCE_IMAGE_FILE.partitions # save the table of partitions of the source image
SOURCE_IMAGE_LAST_PARTITION_SIZE=`awk '/start/&&!/Id= 0/ {lastplen=$6}; END {print lastplen}' $SOURCE_IMAGE_FILE.partitions | cut -d, -f1` # size of the last partition (to be resized!)
SOURCE_IMAGE_VOLUME_ID=`vgscan | awk -F\" '/Found volume/&&!/ubuntu-vg/ { print $2 }'` # "vg0", optimizer vm's vg is ubuntu (otherwise change ubuntu-vg!)
vgcfgbackup -f $SOURCE_IMAGE_FILE.groups $SOURCE_IMAGE_VOLUME_ID
vgchange -ay $SOURCE_IMAGE_VOLUME_ID
# Here we should list Volumes and mount all that is mountable. now we assume just "root"'s presence
mount /dev/$SOURCE_IMAGE_VOLUME_ID/root $SOURCE_FILE_SYSTEM_DIR
FREE_SPACE_BEFORE_OPTIMIZATION=`df -Pm $SOURCE_FILE_SYSTEM_DIR | tail -1 | awk '{print $4}'`

# => variables: SOURCE_IMAGE_SIZE, FREE_SPACE_BEFORE_OPTIMIZATION, SOURCE_IMAGE_VOLUME_ID, SOURCE_IMAGE_LAST_PARTITION_SIZE, 
# => files: $SOURCE_IMAGE_FILE.partitions, $SOURCE_IMAGE_FILE.groups
# => source image mounted to $SOURCE_FILE_SYSTEM_DIR

# === removal script execution ===
$RMME_SCRIPT $SOURCE_FILE_SYSTEM_DIR/
FREE_SPACE_AFTER_OPTIMIZATION=`df -Pm $SOURCE_FILE_SYSTEM_DIR | tail -1 | awk '{print $4}'`

# === new disk image creation ===
OPTIMIZED_IMAGE_SIZE=$((SOURCE_IMAGE_SIZE - FREE_SPACE_AFTER_OPTIMIZATION + FREE_SPACE_BEFORE_OPTIMIZATION))
qemu-img create -f qcow2 -o preallocation=off OPTIMIZED_IMAGE_FILE ${OPTIMIZED_IMAGE_SIZE}000000
qemu-nbd -c /dev/nbd1 OPTIMIZED_IMAGE_FILE || { echo "Could not attach just created qcow2 file" ; exit 243 ; }

# === write optimized disk image content ===

#dd if=/dev/nbd0 of=/dev/nbd1 bs=512 count=1 #substituted below with the larger dd
IMGFN_OPT_MARSH=`echo $IMGFN_OPT | sed s+/+\\\\\\\\/+g`
OPT_NBDPID=`ps ux | awk "!/awk/&&/$IMGFN_OPT_MARSH/ {print \\$2}"`
NEW_SECCOUNT=`blockdev --getsize /dev/nbd1`

#Nasty stuff begins (wp image specific)
NEW_LASTPARTLEN=`awk -v totlen=$NEW_SECCOUNT '/start/&&!/Id= 0/ {tot=totlen-$4 }; END {print tot}' $SOURCE_IMAGE_FILE.partitions`
NEW_LASTPARTSTART=`awk '/start/&&!/Id= 0/ {lastplen=$4 }; END {print lastplen}' $SOURCE_IMAGE_FILE.partitions | cut -d, -f1`
sed s/$SOURCE_IMAGE_LAST_PARTITION_SIZE/$NEW_LASTPARTLEN/ $SOURCE_IMAGE_FILE.partitions > $PARTLAYOUT_NEW
dd if=/dev/nbd0 of=/dev/nbd1 bs=512 count=$NEW_LASTPARTSTART conv=noerror,sync #Overwrites mbr + first partition

sfdisk --force /dev/nbd1 < $PARTLAYOUT_NEW
kill $OPT_NBDPID

qemu-nbd -c /dev/nbd1 $IMGFN_OPT  || { echo "Could not attach just created qcow2 file" ; exit 243 ; }
OPT_NBDPID=`ps ux | awk "!/awk/&&/optimised/ {print \\$2}"`
ls -l ${TARGET_DEVNAME}*
DDPARTSOURCE=`awk '/bootable/ {print $1}' $SOURCE_IMAGE_FILE.partitions`
DDPARTTARGET=`echo $DDPARTSOURCE | sed s+/dev/nbd0+/dev/nbd1+`
VGPARTTARGET=`echo $DDPARTTARGET | sed s/p1$/p2/`
#dd if=$DDPARTSOURCE of=$DDPARTTARGET bs=512 conv=noerror,sync # substituted above with the larger dd
SWAPLEN=`lvdisplay $SOURCE_IMAGE_VOLUME_ID --units m | awk '/LV Name/ && !/swap/ {swapmarker=0}; /LV Name/ && /swap/ {swapmarker=1}; swapmarker==1&&/LV Size/ { print $3 }'`
vgscan
pvcreate $VGPARTTARGET
VOLID_TEMP=${SOURCE_IMAGE_VOLUME_ID}-TEMP
vgcreate $VOLID_TEMP $VGPARTTARGET
lvcreate -L ${SWAPLEN}m -n swap $VOLID_TEMP
lvcreate -l 100%FREE -n root $VOLID_TEMP
mkswap /dev/$VOLID_TEMP/swap
mkfs.ext4 -qF /dev/$VOLID_TEMP/root
mount /dev/$VOLID_TEMP/root $OPTIMIZED_FILE_SYSTEM_DIR
cp -ax $SOURCE_FILE_SYSTEM_DIR/* $OPTIMIZED_FILE_SYSTEM_DIR
#Done copying. now we just need to clean up end ensure bootability

umount $OPTIMIZED_FILE_SYSTEM_DIR
umount $SOURCE_FILE_SYSTEM_DIR
vgchange -an $SOURCE_IMAGE_VOLUME_ID
vgchange -an $VOLID_TEMP
qemu-nbd --disconnect /dev/nbd1
rm $IMGFN_TEMP

vgscan
vgrename $VOLID_TEMP $SOURCE_IMAGE_VOLUME_ID

vgchange -ay $SOURCE_IMAGE_VOLUME_ID
mount /dev/$SOURCE_IMAGE_VOLUME_ID/root $OPTIMIZED_FILE_SYSTEM_DIR
mount $DDPARTTARGET ${OPTIMIZED_FILE_SYSTEM_DIR}/boot
mount --bind /dev ${OPTIMIZED_FILE_SYSTEM_DIR}/dev
cp $DMAP $DMAP_TEMP
echo "(hd0) $DEVNAMEFIX" > $DMAP
chroot $OPTIMIZED_FILE_SYSTEM_DIR grub-install $DEVNAMEFIX 
cp $DMAP_TEMP $DMAP
umount ${OPTIMIZED_FILE_SYSTEM_DIR}/dev
umount ${OPTIMIZED_FILE_SYSTEM_DIR}/boot
umount $OPTIMIZED_FILE_SYSTEM_DIR
vgchange -an $SOURCE_IMAGE_VOLUME_ID

kill $OPT_NBDPID

cd -
#Saving the image
mv $IMGFN_OPT /mnt/optimized-image.qcow2
#Dropping temporary data
rm -r $basedir
