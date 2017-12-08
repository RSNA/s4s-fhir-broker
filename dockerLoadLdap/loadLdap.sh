#/bin/sh

cat template.ldif | envsubst > scp.ldif
cat template.sh | envsubst > cmd.sh
sh cmd.sh
