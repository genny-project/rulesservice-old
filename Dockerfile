#FROM  openjdk:8u151-jre-alpine3.7 
FROM java:8
#RUN echo http://mirror.yandex.ru/mirrors/alpine/v3.7/main > /etc/apk/repositories; \
#    echo http://mirror.yandex.ru/mirrors/alpine/v3.7/community >> /etc/apk/repositories

#RUN apk update \
# && apk add --no-cache 
#RUN apk update && apk add jq && apk add bash && apk add curl
RUN apt-get -y update
RUN apt-get -y install curl
#RUN curl -sL https://deb.nodesource.com/setup_7.x | bash
RUN apt-get install -y jq 

RUN mkdir /realm
RUN mkdir /rules
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

#HEALTHCHECK --interval=10s --timeout=5s --retries=10 CMD curl -f / http://localhost:8080/version || exit 1 

ENTRYPOINT [ "/docker-entrypoint.sh" ]

ADD target/rulesservice-0.0.1-SNAPSHOT-fat.jar /service.jar
ADD src/main/resources/rules /rules
