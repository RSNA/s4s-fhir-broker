FROM dcm4che/dcm4che-tools:latest

ENV AE_TITLE="PACS_SCP"
ENV DEVICE_NAME="BROKER_SCP"

ADD images/smart-1288992 /images/smart-1288992

ENTRYPOINT storescu -c ${DEST_PACS} /images/${IMAGE_SET}/*
