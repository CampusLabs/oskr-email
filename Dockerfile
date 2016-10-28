FROM quay.io/orgsync/clojure:2.5.3
MAINTAINER Lars Levie <llevie@campuslabs.com>

WORKDIR /code
COPY . /code/

RUN lein uberjar \
    && mkdir /opt/oskr-email \
    && mv /code/target/oskr-email.jar /opt/oskr-email/oskr-email.jar \
    && rm -Rf /code \
    && rm -Rf /root/.m2

WORKDIR /opt/oskr-email

ENV HEAP_SIZE 200m

CMD exec java \
    -server \
    -XX:+UseG1GC \
    -Xmx${HEAP_SIZE} \
    -Xms${HEAP_SIZE} \
    -XX:MaxGCPauseMillis=1000 \
    -XX:+AggressiveOpts \
    -jar oskr-email.jar
