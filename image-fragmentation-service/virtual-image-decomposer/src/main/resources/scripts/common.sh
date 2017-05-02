#!/bin/bash
set -euxv

# silently fail on any error
function unmountSourceImage() {
  cd ${WORKING_DIR}
  umount $SOURCE_IMAGE_DIR &> /dev/null && echo '  'source image unmonted 
  if [ -n "$PARTITION" ]; then
    qemu-nbd -d $SOURCE_DEVICE &> /dev/null && echo '  'source image detached
  else
    vgchange -a n $VOLUME_GROUP_SOURCE &> /dev/null && echo '  '$VOLUME_GROUP_SOURCE deactivated
    qemu-nbd -d $SOURCE_DEVICE &> /dev/null && echo '  'source image detached
  fi
}

# silently fail on any error
function unmountTargetImage() {
  cd ${WORKING_DIR}
  umount $TARGET_IMAGE_DIR &> /dev/null && echo '  'target image unmonted
  if [ -n "$PARTITION" ]; then
    qemu-nbd -d $TARGET_DEVICE &> /dev/null && echo '  'target image detached
  else
    vgchange -a n $VOLUME_GROUP_TARGET &> /dev/null && echo '  '$VOLUME_GROUP_TARGET deactivated
    qemu-nbd -d $TARGET_DEVICE &> /dev/null && echo '  'target image detached
  fi
}

function releaseResources() {
	unmountSourceImage
	unmountTargetImage
}

function error() { 
	# Usage: error ${LINENO} "the foobar failed" 2
	local failure_file="${WORKING_DIR}${FAILURE_FILE}"
	local done_file="${WORKING_DIR}${DONE_FILE}"
	local parent_lineno="$1"
  	local message="${2:-}"
  	local code="${3:-1}"
	rm -f ${done_file}
	rm -f ${failure_file}
  	if [[ -n "$message" ]] ; then
    	echo "Error on or near line ${parent_lineno}: ${message}; exiting with status ${code}"
    	echo "${message}; Exit code: ${code}, line: ${parent_lineno}" > "${failure_file}"
  	else
    	echo "Error on or near line ${parent_lineno}; exiting with status ${code}"
    	echo "Exit code: ${code}, line: ${parent_lineno}" > "${failure_file}"
  	fi
  	
  	releaseResources
  	echo "$(date) error: $1" >> ${SCRIPT_DIR}/decomposer.log
  	exit "${code}"
}
trap 'error ${LINENO}' ERR

function mountSourceImage() {
  echo Mounting source image...
  mkdir -p $SOURCE_IMAGE_DIR
  modprobe -a nbd &> /dev/null || echo "WARNING: Cannot load NBD kernel module"
  if [ -n "$PARTITION" ]; then
    qemu-nbd -c $SOURCE_DEVICE $SOURCE_IMAGE_FILE || error ${LINENO} "ERROR: Cannot attach source device" 2
	if [ -e "$SOURCE_DEVICE" ]; then sleep 1; fi
    mount $SOURCE_DEVICE"p"$PARTITION $SOURCE_IMAGE_DIR || error ${LINENO} "ERROR: Cannot mount source device " 2
    echo '  source image mounted'
  else
    qemu-nbd -c $SOURCE_DEVICE $SOURCE_IMAGE_FILE || error ${LINENO} "ERROR: Cannot attach source device" 2
	if [ -e "$SOURCE_DEVICE" ]; then sleep 1; fi
    vgchange -a y $VOLUME_GROUP_SOURCE &> /dev/null || error ${LINENO} "ERROR: Cannot activate source volume group" 2
    mount /dev/$VOLUME_GROUP_SOURCE/$LOGICAL_VOLUME_SOURCE $SOURCE_IMAGE_DIR || error ${LINENO} "ERROR: Cannot mount source volume group" 2
    echo '  source image mounted'
  fi
}

function mountTargetImage() {
  echo Mounting target image...
  mkdir -p $TARGET_IMAGE_DIR
  if [ -n "$PARTITION" ]; then
    qemu-nbd -c $TARGET_DEVICE $TARGET_IMAGE_FILE || error ${LINENO} "ERROR: Cannot attach target device" 2
	if [ -e "$TARGET_DEVICE" ]; then sleep 1; fi
    mount $TARGET_DEVICE"p"$PARTITION $TARGET_IMAGE_DIR || error ${LINENO} "ERROR: Cannot mount target device " 2
    echo '  'target image mounted
  else
    qemu-nbd -c $TARGET_DEVICE $TARGET_IMAGE_FILE || error ${LINENO} "ERROR: Cannot attach target device " 2
	if [ -e "$TARGET_DEVICE" ]; then sleep 1; fi
    vgchange -a y $VOLUME_GROUP_TARGET &> /dev/null || error ${LINENO} "ERROR: Cannot activate target volume group" 2
    mount /dev/$VOLUME_GROUP_TARGET/$LOGICAL_VOLUME_TARGET $TARGET_IMAGE_DIR || error ${LINENO} "ERROR: Cannot mount target volume group" 2
    echo '  'target image mounted
  fi
}

# avoid duplicate VG UUID error
function changeSourceVgUuid() {
	if [ -n "$PARTITION" ]; then
		return
	else 
		local new_source_vg_name=${VOLUME_GROUP_SOURCE}-source
		local new_source_lv_name=${LOGICAL_VOLUME_SOURCE}-source
		echo Renaming and changing UUID of source volume group...
	    qemu-nbd -c $SOURCE_DEVICE $SOURCE_IMAGE_FILE || error ${LINENO} "ERROR: Cannot attach source device" 2
		if [ -e "$SOURCE_DEVICE" ]; then sleep 1; fi
		vgrename ${VOLUME_GROUP_SOURCE} ${new_source_vg_name} || error ${LINENO} "ERROR: Cannot rename source volume group" 1
		lvrename /dev/${new_source_vg_name}/${LOGICAL_VOLUME} /dev/${new_source_vg_name}/${new_source_lv_name} || error ${LINENO} "ERROR: Cannot rename source logical volume" 1
		VOLUME_GROUP_SOURCE=${new_source_vg_name}
		LOGICAL_VOLUME_SOURCE=${new_source_lv_name}
	    vgchange -a n ${VOLUME_GROUP_SOURCE} || error ${LINENO} "ERROR: Cannot deactivate source volume group" 3
		# change VG UUID
	    vgchange -u ${VOLUME_GROUP_SOURCE} || error ${LINENO} "ERROR: Cannot change UUID of source volume group" 2
	    # change PV UUID
		pvs | grep ${VOLUME_GROUP_SOURCE} | awk '{system("pvchange -u "$1)}'
    	qemu-nbd -d $SOURCE_DEVICE &> /dev/null && echo '  'source image detached
    fi
}

function set_phase() {
	local working_dir="$1"
	local phase="$2"
	echo "${phase}" > "${working_dir}${PHASE_FILE}"
}

function onExit() {
	touch "${WORKING_DIR}${DONE_FILE}"
}
trap onExit EXIT

function cleanup() {
	releaseResources
	
	if [ "$DEBUG" != "true" ]; then
	
		# build-source-image.sh	
		rm -rf $SOURCE_IMAGE_DIR
		rm -f $SOURCE_IMAGE_FILE
		rm -f $MERGE_SCRIPT
	
		# download-snapshot.sh/run-installers
		rm -rf $TARGET_IMAGE_DIR
		rm -f $TARGET_IMAGE_FILE
		
		# compute-diff.sh 
		rm -f $CHANGE_LOG
		rm -f $INCLUDES
		
		# upload-fragment.sh
		rm -f $DELTA_FILE
		
		# all
		rm -f *.status
		
	fi
}
