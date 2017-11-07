#!/bin/bash
echo "RUNNING DOCKER-ENTRYPOINT"
#set -e;

myip=
while IFS=$': \t' read -a line ;do
    [ -z "${line%inet}" ] && ip=${line[${#line[1]}>4?1:2]} &&
        [ "${ip#127.0.0.1}" ] && myip=$ip
  done< <(LANG=C /sbin/ifconfig eth0)


if [ -z "${myip}" ]; then
   myip=127.0.0.1
fi

export MYIP=${myip}

KEYCLOAK_JSON_DIR=/realm
KEYCLOAK_ORIGINAL_JSON_DIR=/opt/realm

# copy all the keycloak files so they may be modified
cp -rf ${KEYCLOAK_ORIGINAL_JSON_DIR}/* ${KEYCLOAK_JSON_DIR}/


# change the package.json file
function escape_slashes {
    /bin/sed 's/\//\\\//g'
}

function change_line {
  eval OLD_LINE_PATTERN="$1"
  eval NEW_LINE="$2"
  eval FILE="$3"

    local NEW=$(echo "${NEW_LINE}" | escape_slashes)
    /bin/sed -i  '/'"${OLD_LINE_PATTERN}"'/s/.*/'"${NEW}"'/' "${FILE}"
}

function change_line2 {
  eval OLD_LINE_PATTERN="$1"
  eval NEW_LINE="$2"
  eval FILE="$3"

    local NEW=$(echo "${NEW_LINE}" | escape_slashes)
    /bin/sed -i  '/'"${OLD_LINE_PATTERN}"'/s/.*/'"${NEW}"'/' "${FILE}"
}

for i in `ls ${KEYCLOAK_JSON_DIR}` ; do
if grep -r localhost ${KEYCLOAK_JSON_DIR}/${i}
then
   OLD_LINE_KEY="auth-server-url"
   NEW_LINE="\"auth-server-url\": \"${KEYCLOAKURL}/auth\","
   change_line "\${OLD_LINE_KEY}" "\${NEW_LINE}" "\${KEYCLOAK_JSON_DIR}\/\${i}"
fi

done


command="$1"; 
if [ "$command" != "java" ]; then 
   echo "ERROR: command must start with: java"; 
   exit 1; 
fi; 

exec "$@"
