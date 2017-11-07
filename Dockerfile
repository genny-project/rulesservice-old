FROM openjdk:8u131-jre-alpine

RUN apk update && apk add jq && apk add bash

ADD target/rulesservice-0.0.1-SNAPSHOT-fat.jar /service.jar
#ADD cluster.xml /cluster.xml

RUN mkdir /realm
ADD realm /opt/realm
ADD docker-entrypoint.sh /docker-entrypoint.sh

WORKDIR /

EXPOSE 5701
EXPOSE 5702
EXPOSE 5703
EXPOSE 5704
EXPOSE 5705
EXPOSE 5706
EXPOSE 15701 
EXPOSE 15702
#CMD ["java"]
ENTRYPOINT [ "/docker-entrypoint.sh" ]

