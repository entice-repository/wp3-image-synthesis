#!/bin/bash

# input: two directories
# output: delta-package.tar.gz
# dependencies: qemu-nbd

DELETIONS_FILE=".delta-delete"
DELETIONS_SCRIPT_FILE=".delta-delete.sh"
ID_FILE="/var/lib/cloud/virtual-image.id"

# LVM rename to avoid conflict
changeSourceVgUuid
mountSourceImage
mountTargetImage

mkdir -p $DELTA_DIR

function explode() {
  if [ -z "$1" ] || [ $1 == '/' ]; then return; fi
  explode `dirname $1`
  echo $1/
}

echo Creating delta package...
RSYNC_OPTIONS="--recursive --acls --perms --xattrs --owner --group --times --links --hard-links --super --one-file-system --devices"

# rsync files (NOTE: it creates empty dirs for every directories even if nothing changed there)
# echo '  'copying additions and updates (creates empty dirs)
# rsync $RSYNC_OPTIONS --compare-dest=../$SOURCE_IMAGE_DIR $TARGET_IMAGE_DIR/ $DELTA_DIR &> /dev/null || error ${LINENO} "ERROR: Cannot create update delta" 41

# optimized solution: create only changed directories
echo '  'creating list of updates
rsync --dry-run --itemize-changes --out-format="%i|%n|" $RSYNC_OPTIONS --compare-dest=../$SOURCE_IMAGE_DIR $TARGET_IMAGE_DIR/ $DELTA_DIR | \
  grep '^c\|^>'| awk -F '|' '{print "/"$2 }' > $CHANGE_LOG || error ${LINENO} "ERROR: Cannot create update list" 42
# transform paths to directory list and generate all parent directories
cat $CHANGE_LOG | awk -F'/[^/]*$' '{print $1}' | sort | uniq | { while read DIR; do explode "$DIR"; done } | sort | uniq > $INCLUDES
cat $CHANGE_LOG >> $INCLUDES
echo '  'copying additions and updates
rsync --include-from=$INCLUDES --exclude='/**' $RSYNC_OPTIONS --compare-dest=../$SOURCE_IMAGE_DIR $TARGET_IMAGE_DIR/ $DELTA_DIR &> /dev/null || error ${LINENO} "ERROR: Cannot create update delta" 44

echo '  'creating list of deletions
rsync --verbose --dry-run --existing --ignore-existing --delete --out-format="%i|%n|" $RSYNC_OPTIONS $TARGET_IMAGE_DIR/ $SOURCE_IMAGE_DIR | grep "^*deleting" | awk -F '|' '{print "./"$2 }' >> $DELTA_DIR/$DELETIONS_FILE  || error ${LINENO} "ERROR: Cannot create delete delta" 45
echo '#!/bin/sh' > $DELTA_DIR/$DELETIONS_SCRIPT_FILE
echo 'while read FILE; do rm -rf "$FILE"; done < '$DELETIONS_FILE >> $DELTA_DIR/$DELETIONS_SCRIPT_FILE
echo 'rm '$DELETIONS_FILE >> $DELTA_DIR/$DELETIONS_SCRIPT_FILE
chmod u+x $DELTA_DIR/$DELETIONS_SCRIPT_FILE

# copy .delta-init.sh to delta dir (if exists)
if [ -f "$INIT_FILE" ]; then
  # remove carriage returns
  sed -i 's/\r$//' "$INIT_FILE"
  cp "${INIT_FILE}" "${DELTA_DIR}"
  chmod u+x "${DELTA_DIR}"/"${INIT_FILE}"
fi

# watermark target image
# remove potential previous cloud-init history from /var/lib/cloud/ (can occur in the case of snapshot)
rm -rf "${DELTA_DIR}"/var/lib/cloud/
mkdir -p "${DELTA_DIR}"/var/lib/cloud/
echo "${TARGET_VIRTUAL_IMAGE_ID}" >> "${DELTA_DIR}""${ID_FILE}" || echo "Cannot save virtual image id" 
# echo "${SOURCE_VIRTUAL_IMAGE_ID}" >> "${DELTA_DIR}"/var/lib/cloud/vvmi.parent.id || echo "Cannot save parent virtual image id" 

echo '  'creating tarball
tar -zcf $DELTA_FILE -C $DELTA_DIR . &> /dev/null || error ${LINENO} "ERROR: Cannot create delta tar.gz" 46

# delete delta dir (cannot be deleted by tomcat user later)
rm -rf $DELTA_DIR

unmountSourceImage
unmountTargetImage
