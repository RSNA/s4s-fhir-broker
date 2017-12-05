#/bin/sh

cat template.ldif | envsubst > scp.ldif
echo scp.ldif
cat scp.ldif
cat template.sh | envsubst > cmd.sh
echo cmd.sh
cat cmd.sh
sh cmd.sh
