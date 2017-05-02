#!/bin/bash
curl -X POST -s -H "Token: entice" -H "Content-Type: application/json" -d '{owner:ahajnal,url:"http://source-images.s3.lpds.sztaki.hu/ami-000001654.qcow2",name:"Ubuntu 16.04",tags:[ubuntu,ubuntu16,"16.04"],description:"[BASE OFFICIAL] Ubuntu 16.04.02",partition:"1",cloudImageIds:{sztaki:"ami-000001654"}}' http://localhost:8080/virtual-image-manager/rest/baseimages/ > baseImageId
curl -X POST -s -H "Token: entice" -H "Content-Type: application/json" -d '{owner:ahajnal,url:"http://source-images.s3.lpds.sztaki.hu/ami-000001668.qcow2",name:"Ubuntu 14.04.1 LTS",tags:[ubuntu,ubuntu14,"14.04"],description:"[BASE CLONED] Ubuntu 14.04 - cloud-init -updated, VG changed",partition:"ubuntu-volume-group root",cloudImageIds:{sztaki:"ami-000001668"}}' http://localhost:8080/virtual-image-manager/rest/baseimages/ > baseImageId


