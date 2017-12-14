#!/bin/bash

export GENNYDEV=true
export HOSTIP=10.1.120.89
export REACT_APP_QWANDA_API_URL=http://localhost:8280
export REACT_APP_VERTX_URL=http://localhost:8088/frontend
java -jar target/rulesservice-0.0.1-SNAPSHOT-fat.jar

