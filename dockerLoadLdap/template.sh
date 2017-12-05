#/bin/sh

ldapadd -xD "cn=admin,dc=dcm4che,dc=org" \
	-w secret -f scp.ldif \
	-h ${LDAP_HOST} -p ${LDAP_PORT}
