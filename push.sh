#!/bin/bash

if [ -z "${1}" ]; then
   version="latest"
else
   version="${1}"
fi


docker push gennyproject/rulesservice:"${version}"
docker tag  gennyproject/rulesservice:"${version}"  gennyproject/rulesservice:latest
docker push gennyproject/rulesservice:latest

