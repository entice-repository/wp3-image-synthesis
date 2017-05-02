#!/bin/bash
echo OBSOLETE
echo Populating test virtual image database...

HOST="https://entice.lpds.sztaki.hu:8443"

JSON="{name:'Ubuntu 14.04 LTS', tags:[Ubuntu, Ubuntu14.04, 64bit, xen], owner:admin, url:'http://host/ubuntu14.qcow2', description:"Ubuntu official base image", partition:1, cloudImageIds:{sztaki:"ami-000001657"}}"
curl -k -X POST -s -H "Token: entice" -H "Content-Type: application/json" -d "$JSON" $HOST/virtual-image-manager/rest/baseimages/ > i1 || exit 1
echo i1 $(cat i1)

JSON="{name:'CentOS-7.1.1503', tags:[CentOS, CentOS-7.1.1503, 64bit, kvm], owner:admin, url:'http://host/centos7.qcow2', description:"CentOS official base image", partition:1, cloudImageIds:{sztaki:"ami-000000253"}}"
curl -k -X POST -s -H "Token: entice" -H "Content-Type: application/json" -d "$JSON" $HOST/virtual-image-manager/rest/baseimages/ > i2 || exit 1
echo i2 $(cat i2)

JSON="{name:'ArchLinux 4.7.6-1', tags:[ArchLinux, ArchLinux-4.7.6-1, 64bit, kvm], owner:admin, url:'http://host/arch-4.qcow2', description:"ArchLinux official base image", partition:1, cloudImageIds:{sztaki:"ami-000001606"}}"
curl -k -X POST -s -H "Token: entice" -H "Content-Type: application/json" -d "$JSON" $HOST/virtual-image-manager/rest/baseimages/ > i3 || exit 1
echo i3 $(cat i3)

# create first level virtual images
JSON="{parent: $(cat i2), name:'Tomcat installed on CentOS', installerIds:[tomcat], owner:admin}"
curl -k -X POST -s -H "Token: entice" -H "Content-Type: application/json" -d "$JSON" $HOST/virtual-image-manager/rest/virtualimages/ > i4 || exit 1
echo i4 $(cat i4)

JSON="{parent: $(cat i2), name:'MySQL server installed on CentOS', installerIds:[mysql-server], owner:admin}"
curl -k -X POST -s -H "Token: entice" -H "Content-Type: application/json" -d "$JSON" $HOST/virtual-image-manager/rest/virtualimages/ > i5 || exit 1
echo i5 $(cat i5)

# wait till first level done...
sleep 120

# create second level virtual images
JSON="{parent: $(cat i3), name:'Arch Linux with X-Window', installerIds:[xfce], owner:admin}"
curl -X POST -s -H "Token: entice" -H "Content-Type: application/json" -d "$JSON" $HOST/virtual-image-manager/rest/virtualimages/ > i6 || exit 1
echo i6 $(cat i6)

JSON="{parent: $(cat i4), name:'My web application on CentOS', installerIds:[mywebapp], parent: 'TODO', owner:admin, snapshotUrl:'http://images.s3.lpds.sztaki.hu/xenial-server-cloudimg-amd64-disk1.img.snapshot.qcow2'}"
curl -X POST -s -H "Token: entice" -H "Content-Type: application/json" -d "$JSON" $HOST/virtual-image-manager/rest/virtualimages/ > i7 || exit 1
echo i7 $(cat i7)

# print result
curl -sS $HOST/virtual-image-manager/rest/virtualimages | python -m json.tool
