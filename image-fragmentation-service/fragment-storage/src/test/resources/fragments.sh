tar cvf file.tar,gz --files-from /dev/null
curl -X POST -H "Content-Type: application/gzip" -H "token: entice" --upload-file @file.tar.gz http://localhost:8080/fragment-storage/rest/fragments/ > fragmentUrl
curl -o file.tar.gz $(cat fragmentUrl)
