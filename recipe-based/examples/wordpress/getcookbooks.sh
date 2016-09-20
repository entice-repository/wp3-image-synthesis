#!/bin/bash
#
# Runme from the wp3-imagesynthesis directory
#

TMPCOOKBOOK=foobar-cookbook
TMPDIR=/tmp
MYPWD=`pwd`

rm -rf ${TMPDIR}/${TMPCOOKBOOK}
cd ${TMPDIR}
berks cookbook ${TMPCOOKBOOK}
cp -f ${MYPWD}/Berksfile ${TMPDIR}/${TMPCOOKBOOK}/
cd ${TMPCOOKBOOK}
berks vendor ${MYPWD}/cookbooks
cd ${MYPWD}
rm -rf ${TMPDIR}/${TMPCOOKBOOK}