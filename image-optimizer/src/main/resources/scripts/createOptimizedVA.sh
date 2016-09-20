#!/bin/bash

#	Copyright 2009-10 Gabor Kecskemeti, University of Westminster, MTA SZTAKI
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

#Parameters: 1. Original VA, 2. Removal script, 3. New VA name
# Error codes:
# 239 => The appliance cannot be packed
# 240 -> The new Disk image cannot be created 
# 241 -> The Original VA cannot be unpacked
# 242 -> invalid parameter set
# 243 -> The removal script failed on the original VA

set -x

if [ $# -ne 3 ]
then
	echo "Usage $0 <OrigVALoc> <Remscript> <NewVAName>"
	exit 242  
fi

#Original VA handling
basedir=`mktemp -td appliancecreator.XXXXXXXXX`
cd $basedir
mkdir OrigiMount
mkdir OptimizedMount
mkdir TunedMount
tar -x -I pigz -f $1 || { cd /tmp ; rm -r $basedir ; exit 241 ; }
mount -o loop disk.image OrigiMount
freespace=`df -Pm $basedir/OrigiMount | tail -1 | awk '{print $4}'`

#Removal script execution
cp $2 OrigiMount
rmmeBN=`basename $2`
chmod +x OrigiMount/$rmmeBN
$basedir/OrigiMount/$rmmeBN $basedir/OrigiMount/ || { umount OrigiMount ; cd /tmp ; rm -r $basedir ; exit 243 ; }
#get rid of the old unused symbolic links
#rm `find $basedir/OrigiMount -type l -xtype l`
rm OrigiMount/$rmmeBN
usedspace=`df -Pm $basedir/OrigiMount | tail -1 | awk '{print $3}'`
echo $usedspace $freespace

#New disk image creation
dd if=/dev/zero of=$basedir/new.image bs=1M seek=$(((usedspace+freespace)*1032/1000)) count=0 || { umount OrigiMount ; cd /tmp ; rm -r $basedir ; exit 240 ; }
mkfs.ext3 -qF $basedir/new.image
mount -o loop $basedir/new.image OptimizedMount
cp -ax $basedir/OrigiMount/* $basedir/OptimizedMount
umount $basedir/OrigiMount
rm disk.image

#Tuning free disk space
freespaceNow=`df -Pm $basedir/OptimizedMount | tail -1 | awk '{print $4}'`
dd if=/dev/zero of=$basedir/tuned.image bs=1M seek=$(((usedspace+freespace+(freespace-freespaceNow))*1032/1000)) count=0 || { umount OptimizedMount ; cd /tmp ; rm -r $basedir ; exit 240 ; }
mkfs.ext3 -qF $basedir/tuned.image
mount -o loop $basedir/tuned.image TunedMount
cp -ax $basedir/OptimizedMount/* $basedir/TunedMount
df -Pm $basedir/TunedMount
umount $basedir/TunedMount
umount $basedir/OptimizedMount
mv tuned.image disk.image

#New VA registration
tar -c -I pigz -f appliance.tgz disk.image domain.cfg || { cd /tmp ; rm -r $basedir ; exit 239 ; }
mkdir /repository/$3
cp $basedir/appliance.tgz /repository/$3
rm -r $basedir

