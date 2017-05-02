#!/bin/bash
curl -X POST -s -H "Token: entice" -H "Content-Type: application/json" -d '{debug:false,owner:ahajnal,parent:"'$(cat baseImageId)'",name:"Ubuntu 16.04 with mc",description:"mc installed using installer",installerIds:[mc]}' http://localhost:8080/virtual-image-manager/rest/virtualimages/ > virtualImageId
curl -X POST -s -H "Token: entice" -H "Content-Type: application/json" -d '{owner:ahajnal,parent:"'$(cat virtualImageId)'",name:"Ubuntu 16.04 with mc and mysql-client",description:"mysql-client installed using installer",installerIds:[mysql-client]}' http://localhost:8080/virtual-image-manager/rest/virtualimages/ > virtualImageId2
curl -X POST -s -H "Token: entice" -H "Content-Type: application/json" -d '{owner:ahajnal,parent:"'$(cat baseImageId)'",name:"Ubuntu 16.04 with mc and mysql-client",description:"mysql-client and mc installed using installers",installerIds:[mysql-client, mc]}' http://localhost:8080/virtual-image-manager/rest/virtualimages/ > virtualImageId4


curl -X POST -s -H "Token: entice" -H "Content-Type: application/json" -d '{debug:true,owner:ahajnal,parent:"4792f4f2-8c6d-4d76-a1c5-2a4ae3866c8a",name:"Ubuntu 14.04 with mysql-client",description:"mysql-client installed using installers",installerIds:[mysql-client]}' http://localhost:8080/virtual-image-manager/rest/virtualimages/ > virtualImageId5
