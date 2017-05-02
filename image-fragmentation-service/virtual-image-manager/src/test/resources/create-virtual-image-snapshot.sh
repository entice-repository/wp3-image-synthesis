#!/bin/bash
curl -X POST -s -H "Token: entice" -H "Content-Type: application/json" -d '{owner:ahajnal,parent:"'$(cat baseImageId)'",name:"Ubuntu 16.04.02 with mc",description:"mc installed manually",snapshotUrl:"http://images.s3.lpds.sztaki.hu/xenial-server-cloudimg-amd64-disk1.img.snapshot.qcow2",tags:["mc"]}' http://localhost:8080/virtual-image-manager/rest/virtualimages/ > virtualImageId3
