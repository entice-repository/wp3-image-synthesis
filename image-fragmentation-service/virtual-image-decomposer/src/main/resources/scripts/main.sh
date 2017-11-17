#!/bin/bash
set -euxv

# constants
CURL_OPTIONS="-k -L -s -f --retry 5 --retry-delay 10"
SOURCE_IMAGE_FILE="source-image.qcow2"
SOURCE_IMAGE_DIR="source-image"
TARGET_IMAGE_FILE="target-image.qcow2"
TARGET_IMAGE_DIR="target-image"
FAILURE_FILE="failure"
DONE_FILE="done"
PHASE_FILE="phase"
DELTA_FILE="delta-package.tar.gz"
SCRIPT_DIR="`pwd`"
MERGE_SCRIPT=".source-image-assembly.sh"
INSTALLER_FILE=".delta-install.sh"
INIT_FILE=".delta-init.sh"
PRE_ASSEMBLY_FILE=".delta-pre.sh"

DELTA_DIR="delta"
CHANGE_LOG="delta.changelog"
INCLUDES="delta.includes"

DEBUG_FILE="debug"
IMAGE_SIZE_FILE="imageSize"
FRAGMENT_SIZE_FILE="fragmentSize"


# check parameters
if [ "$#" -ne 2 ]; then
	echo "$(date) ERROR: Invalid number of parameters: $#. Usage: $0 <WORKING_DIR> <DEVICE_NUMBER>" >> decomposer.log
	exit 255
fi

echo "$(date) started: $1" >> decomposer.log

# parameters
WORKING_DIR="$1"
DEVICE_NUMBER="$2"

# source common.sh
if [ ! -f "common.sh" ]; then
	echo "common.sh not found"
	exit 254
else	
	. common.sh
fi

# source input.sh
for i in "${WORKING_DIR}input.sh"; do
	if [ ! -f "${i}" ]; then
		error ${LINENO} "ERROR: missing input file: ${i}" 254
	else
		. ${i}
	fi
done

# derived variables from input.sh
MERGE_SCRIPT_URL="${VIRTUAL_IMAGE_COMPOSER_URL}${SOURCE_VIRTUAL_IMAGE_ID}" 
SOURCE_DEVICE="/dev/nbd${DEVICE_NUMBER}"
TARGET_DEVICE="/dev/nbd$(( ${DEVICE_NUMBER} + 8 ))"

VOLUME_GROUP_SOURCE=${VOLUME_GROUP}
LOGICAL_VOLUME_SOURCE=${LOGICAL_VOLUME}
VOLUME_GROUP_TARGET=${VOLUME_GROUP}
LOGICAL_VOLUME_TARGET=${LOGICAL_VOLUME}

# release resources to be used (if needed, umount, qemu -d), fail silently
for i in "${SOURCE_DEVICE}" "${TARGET_DEVICE}"; do
    source_mounted=`mount | { grep -s ${i} || true; }`
    if [ ! -z "${source_mounted}" ]; then
        umount_device=`echo "${source_mounted}" | awk '{ print $1 }'`
        umount -l "${umount_device}" && echo ${umount_device} unmounted
    fi
    qemu-nbd -d "${i}" && echo "${i}" disconnected 
done

# debug mode, don't delete this dir even if done
 if [ "$DEBUG" == "true" ]; then
 	touch ${WORKING_DIR}${DEBUG_FILE}
 fi

# Step 1: download and assemble source virtual image in file SOURCE_IMAGE_FILE (when done, umounted/detached)
cd "${WORKING_DIR}"
set_phase "${WORKING_DIR}" "Building source image..."
. "${SCRIPT_DIR}/build-source-image.sh"

# Step 2: build target image either using installers or by downloading snapshot in file TARGET_IMAGE_FILE (when done, umounted/detached)
if [ -z "${SNAPSHOT_URL}" ]; then
	cd "${WORKING_DIR}"
	set_phase "${WORKING_DIR}" "Running installers..."
	. "${SCRIPT_DIR}/run-installers.sh"
else
	cd "${WORKING_DIR}"
	set_phase "${WORKING_DIR}" "Downloading snapshot image..."
	. "${SCRIPT_DIR}/download-snapshot.sh"
fi

# Step 3: compute delta by mounting SOURCE_IMAGE_FILE and TARGET_IMAGE_FILE
cd "${WORKING_DIR}"
set_phase "${WORKING_DIR}" "Computing diff..."
. "${SCRIPT_DIR}/compute-diff.sh"

# save statistics
cd "${WORKING_DIR}"
stat -c '%s' "${TARGET_IMAGE_FILE}" >> "${IMAGE_SIZE_FILE}" || echo '0' >> "${IMAGE_SIZE_FILE}"
stat -c '%s' "${DELTA_FILE}" >> "${FRAGMENT_SIZE_FILE}" || echo '0' >> "${FRAGMENT_SIZE_FILE}"

# Step 4: upload delta
cd "${WORKING_DIR}"
set_phase "${WORKING_DIR}" "Uploading fragment..."
. "${SCRIPT_DIR}/upload-fragment.sh"

set_phase "${WORKING_DIR}" "Done"

cleanup

echo "$(date) ended: $1" >> ${SCRIPT_DIR}/decomposer.log
