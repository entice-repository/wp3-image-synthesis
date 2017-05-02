#!/bin/bash
echo OBSOLETE
curl -X POST -s -H "token: entice" -H "Content-Type: application/json" -d '{author:admin,url:"http://host"}' http://localhost:8080/virtual-image-manager/rest/baseimages/ > baseImageId || exit 1
echo Base image created: $(cat baseImageId)
curl http://localhost:8080/virtual-image-manager/rest/virtualimages/$(cat baseImageId)
echo
curl -X POST -s -H "token: entice" -H "Content-Type: application/json" -d '{author:admin,parent:'$(cat baseImageId)',installerIds:[mysql-server],tags:["MySQL Server"]}' http://localhost:8080/virtual-image-manager/rest/virtualimages/ > virtualImageId && VIRT_IMG=true || echo Cannot create virtual image
echo Virtual image created: $(cat virtualImageId)
if [ "$VIRT_IMG" = true ]; then
    curl http://localhost:8080/virtual-image-manager/rest/virtualimages/$(cat virtualImageId)
    echo
	curl http://localhost:8080/virtual-image-manager/rest/virtualimages
	echo
    curl -X DELETE -H "token: entice" -H "user: admin" http://localhost:8080/virtual-image-manager/rest/virtualimages/$(cat virtualImageId)
    echo Virtual image deleted
fi
curl -X DELETE -H "token: entice" -H "user: admin" http://localhost:8080/virtual-image-manager/rest/baseimages/$(cat baseImageId) || exit 1
echo Base image deleted