FROM ubuntu:xenial
MAINTAINER Ralph Moulton

# Update Ubuntu
RUN apt-get update && apt-get -y upgrade

# Add oracle java 8 repository
RUN apt-get -y install software-properties-common
RUN add-apt-repository ppa:webupd8team/java
RUN apt-get -y update
# Accept the Oracle Java license
RUN echo "oracle-java8-installer shared/accepted-oracle-license-v1-1 boolean true" | debconf-set-selections
# Install Oracle Java
RUN apt-get -y install oracle-java8-installer

# Install tomcat
RUN apt-get -y install tomcat8
RUN echo "JAVA_HOME=/usr/lib/jvm/java-8-oracle" >> /etc/default/tomcat8

EXPOSE 8080

RUN apt-get install -y git maven

RUN git clone https://github.com/RSNA/s4s-fhir-broker.git

ADD utl.properties /s4s-fhir-broker/hapi-fhir-jpaserver-example/src/resources

WORKDIR s4s-fhir-broker

RUN mvn package -Dmaven.test.skip=true

ADD /s4s-fhir-broker/hapi-fhir-jpaserver-example/target/hapi-fhir-jpaserver-example.war /var/lib/tomcat8/webapps/hapi-fhir-jpaserver-example.war

CMD service tomcat8 start && tail -f /var/lib/tomcat8/logs/catalina.out
