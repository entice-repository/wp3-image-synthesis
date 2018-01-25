#!/bin/bash
set -euxv

echo Cloning source image...
if [ ! -f target-image.status ]; then
	cp -f ${SOURCE_IMAGE_FILE} ${TARGET_IMAGE_FILE}
fi

mountTargetImage

function chrootMounts() {
	echo Mounting necessary binds for chroot...
	mount --bind /dev ${TARGET_IMAGE_DIR}/dev || error ${LINENO} "ERROR: Cannot mount chroot dir dev" 31
	mount --bind /sys ${TARGET_IMAGE_DIR}/sys || error ${LINENO} "ERROR: Cannot mount chroot dir sys" 32
	mount --bind /proc ${TARGET_IMAGE_DIR}/proc || error ${LINENO} "ERROR: Cannot mount chroot dir proc" 33
	# mount --bind /run ${TARGET_IMAGE_DIR}/run || error ${LINENO} "ERROR: Cannot mount chroot dir run" 34
	# mount --bind /etc/resolv.conf ${TARGET_IMAGE_DIR}/etc/resolv.conf || error ${LINENO} "ERROR: Cannot mount chroot resolv.conf" 35
	mount --bind /dev/pts ${TARGET_IMAGE_DIR}/dev/pts || error ${LINENO} "ERROR: Cannot mount chroot dir dev/pts" 36
	mv ${TARGET_IMAGE_DIR}/etc/resolv.conf ${TARGET_IMAGE_DIR}/etc/resolv.conf-old || error ${LINENO} "ERROR: Cannot rename /etc/resolv.conf" 37
	cp -L /etc/resolv.conf ${TARGET_IMAGE_DIR}/etc/resolv.conf || error ${LINENO} "ERROR: Cannot copy /etc/resolv.conf into chroot" 38
}

function chrootUmounts() {
	echo Unmounting chroot binds...
	umount -l ${TARGET_IMAGE_DIR}/dev/pts || error ${LINENO} "ERROR: Cannot umount chroot dir" 36
	# umount -l ${TARGET_IMAGE_DIR}/etc/resolv.conf || error ${LINENO} "ERROR: Cannot umount chroot dir" 35
	umount -l ${TARGET_IMAGE_DIR}/dev || error ${LINENO} "ERROR: Cannot umount chroot dir" 31
	umount -l ${TARGET_IMAGE_DIR}/sys || error ${LINENO} "ERROR: Cannot umount chroot dir" 32
	umount -l ${TARGET_IMAGE_DIR}/proc || error ${LINENO} "ERROR: Cannot umount chroot dir" 33
	# umount -l ${TARGET_IMAGE_DIR}/run || error ${LINENO} "ERROR: Cannot umount chroot dir" 34
    rm -rf ${TARGET_IMAGE_DIR}/etc/resolv.conf || echo  "ERROR: Cannot remove /etc/resolv.conf"
    mv ${TARGET_IMAGE_DIR}/etc/resolv.conf-old ${TARGET_IMAGE_DIR}/etc/resolv.conf || error ${LINENO} "ERROR: Cannot restore /etc/resolv.conf in chroot" 38
}

chrootMounts

# run custom installer (if applicable)
if [ -f "$INSTALLER_FILE" ]; then
	echo Running custom installer...
	# remove potential carriage returns
	sed -i 's/\r$//' "$INSTALLER_FILE"
	cp "$INSTALLER_FILE" "${TARGET_IMAGE_DIR}"
	chmod u+x ${TARGET_IMAGE_DIR}/${INSTALLER_FILE}
	cat ${TARGET_IMAGE_DIR}/${INSTALLER_FILE}
	chroot ${TARGET_IMAGE_DIR}/ ./${INSTALLER_FILE} || { chrootUmounts ; error ${LINENO} "ERROR: Cannot run custom install" 22 ; }
	rm ${TARGET_IMAGE_DIR}/${INSTALLER_FILE}
fi

# run installers
echo Running installers...
for INSTALLER_ID in ${INSTALLER_IDS}
do
	INSTALLER_URL="${INSTALLER_STORAGE_URL}installers/${INSTALLER_ID}/install"
	curl ${CURL_OPTIONS} "${INSTALLER_URL}" -o ${TARGET_IMAGE_DIR}/${INSTALLER_FILE} || { chrootUmounts ; error ${LINENO} "ERROR: Cannot download installer: ${INSTALLER_URL}" 21 ; }
	chmod u+x ${TARGET_IMAGE_DIR}/${INSTALLER_FILE}
	echo Running installer $INSTALLER_ID
#	cat ${TARGET_IMAGE_DIR}/${INSTALLER_FILE}
	chroot ${TARGET_IMAGE_DIR}/ ./${INSTALLER_FILE} || { chrootUmounts ; error ${LINENO} "ERROR: Cannot install: ${INSTALLER_ID}" 22 ; }
	rm ${TARGET_IMAGE_DIR}/${INSTALLER_FILE}
	# concatenate init scripts
	echo Preparing init script $INSTALLER_ID
	INIT_URL="${INSTALLER_STORAGE_URL}installers/${INSTALLER_ID}/init"
	curl ${CURL_OPTIONS} "${INIT_URL}" >> ${INIT_FILE}
	PRE_URL="${INSTALLER_STORAGE_URL}installers/${INSTALLER_ID}/pre"
	curl ${CURL_OPTIONS} "${PRE_URL}" >> ${PRE_ASSEMBLY_FILE} || { echo "No pre-assembly script file" ; rm -rf ${PRE_ASSEMBLY_FILE} ; }
done

chrootUmounts

unmountTargetImage

# target image done
touch target-image.status 

# cleanup: TARGET_IMAGE_FILE 