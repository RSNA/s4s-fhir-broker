FROM dcm4che/dcm4che-tools:latest

RUN apt-get update
RUN apt-get install -y gettext-base
RUN apt-get install -y ldap-utils

ENV AE_TITLE="PACS-SCP"
ENV DEVICE_NAME="BROKER-SCP"
ENV DEVICE_HOST="10.252.175.44"
ENV DEVICE_PORT="11122"
ENV LDAP_HOST="10.252.175.44"
ENV LDAP_PORT="389"

WORKDIR work
ADD loadLdap.sh template.ldif template.sh ./

ENTRYPOINT /bin/sh -x loadLdap.sh 
