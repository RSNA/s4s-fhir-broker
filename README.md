## S4S FHIR Image Broker
The broker requires a WADO RS Image source and an
introspection server for testing. You can provide these yourself,
or use the test setups here:

### Set up a dcm4chee based WADO RS Image source server for testing
_(Skip this section if you have your own WADO RS Image source.)_

A dockerized dcm4chee is used for testing. Setting it up 
involves three containers. I found that running each container
in a separate terminal window and leaving the "-d" parameter off
allowed me to see logs readily.

1. dcm4che/slapd-dcm4chee

Ref: https://hub.docker.com/r/dcm4che/slapd-dcm4chee/
```
docker run --name slapd \
   -p 389:389 \
   -v /etc/localtime:/etc/localtime \
   -v /var/local/dcm4chee-arc/ldap:/var/lib/ldap \
   -v /var/local/dcm4chee-arc/slapd.d:/etc/ldap/slapd.d \
   -d dcm4che/slapd-dcm4chee:2.4.44-10.5
```   
2. dcm4che/postgres-dcm4chee

Ref: https://hub.docker.com/r/dcm4che/postgres-dcm4chee/
```
docker run --name postgres \
   -p 5432:5432 \
   -v /etc/localtime:/etc/localtime \
   -e POSTGRES_DB=pacsdb \
   -e POSTGRES_USER=pacs\
   -e POSTGRES_PASSWORD=pacs \
   -v /var/local/dcm4chee-arc/db:/var/lib/postgresql/data \
   -d dcm4che/postgres-dcm4chee:9.6-10
```   
3. dcm4che/dcm4chee-arc-psql

Ref: https://hub.docker.com/r/dcm4che/dcm4chee-arc-psql/

I used the non-secured version, tag 5.10.5, 
ignored the option to use Elasticsearch,
and moved the Wildfly port to 9090.
```
docker run --name dcm4chee-arc \
   -p 9090:8080 \
   -p 9990:9990 \
   -p 11112:11112 \
   -p 2575:2575 \
   -v /var/local/dcm4chee-arc/wildfly:/opt/wildfly/standalone \
   -v /var/local/dcm4chee-arc/storage:/storage \
   --link slapd:ldap \
   --link postgres:db \
   dcm4che/dcm4chee-arc-psql:5.10.5
```   
The reference page gives more details, but this setup provides
a dcm4chee server with:

- DICOM_HOST = localhost
- DICOM_PORT = 11112
- AE_TITLE = DCM4CHEE
- HL7_PORT = 2575
- Wildfly Administration Console: http://localhost:9990, 
User: admin, pw: admin
- WADO-RS  Base URL: http://localhost:9090/dcm4chee-arc/aets/DCM4CHEE/rs

### Load test images into dcm4chee server
_(The instructions here show how to load test images into the dcm4chee
test server. You will need to modify them is you are using your own WADO
RS Image Source.)_

I loaded one test image in the dcm4chee server.
There are two versions of this image file in the 'testImages' directory
in this project if you would like to use one. The file name in testImages
is of the form mrn-familyName.dcm. If you need to change any of the
dicom tags, you could use the CTN utility dcmodify. For example, to
create a new file with a different mrn:
```
dcmodify -i "(0x0010,0x0020)=newMrn" 1288992-adams.dcm
```
There is a sample changes file for dcmodify in the directory also,
called "mods".

I used the CTN utility send_image
from the directory the image was in:
```
send_image -c DCM4CHEE -Z localhost 11112 1288992-adams.dcm
```
This loaded an anonymized Chest CR

- (0x0008,0x0016) SOP Class UID = 1.2.840.10008.5.1.4.1.1.1
- (0x0008,0x0018) SOPInstanceUID = 1.3.6.1.4.1.14519.5.2.1.6279.6001.144065313879447963369902174642
- (0x0008,0x0020) StudyDate = 20000101
- (0x0008,0x0030) StudyTime = empty
- (0x0008,0x0050) AccessionNumber = 2819497684894126
- (0x0010,0x0030) PatientBirthDate = 19251223
- (0x0010,0x0020) PatientID = 1288992
- (0x0012,0x0062) PatientIdentityRemoved = YES
- (0x0010,0x0010) PatientName = Adams^Daniel
- (0x0020,0x000d) StudyInstanceUID = 1.3.6.1.4.1.14519.5.2.1.6279.6001.270617793094907821983261388534
- (0x0020,0x000e) SeriesInstanceUID = 1.3.6.1.4.1.14519.5.2.1.6279.6001.210102868760281756294235082201

### Set up and run Matt Kelsey's test introspection service
_(Skip this section if you are using your own introspection service.)_

Clone the Introspection Service application from github:
```
clone https://github.com/kelseym/introspection-service.git
```
From within the introspection-service directory, build and start the introspection service:
```
docker-compose build
docker-compose up
```
You will now have an introspection endpoint available at: http://localhost:9004/api/introspect
which will make request to:
```
https://portal.demo.syncfor.science/api/fhir
```
### Configuring the s4s FHIR Broker

By default, the broker points to the test WADO RS source and instrospection
servers described above. To point to different locations, edit the file
hapi-fhir-jpaserver-example/utl.properties:
```
WADO_SERVER_URL = http://localhost:9090/dcm4chee-arc/aets/DCM4CHEE/rs
INTROSPECTION_SERVICE_URL = http://localhost:9004/api/introspect
```
to provide the appropriate base URLs.

### run the test s4s FHIR Broker as a Docker Container

To build the docker file, make hapi-fhir-jpaserver-example your current
directory and run:
```
./build-docker-image.sh
```
this will create an image with the label "rsna/s4s-fhir-broker".

To run the image, use (for example):
```
docker run -p 8080:8080 rsna/s4s-fhir-broker
```

### run the test s4s FHIR Broker from Intellij.
I run the modified example server from Intellij on a tomcat server instance.
My run configuration is:
##### Edit Configurations Dialog, Run Tab

![Server Tab](./hapi-fhir-jpaserver-example/docImgs/runConfigServerTab.png?raw=true)
##### Edit Configurations Dialog, Deployment Tab
![Deployment Tab](./hapi-fhir-jpaserver-example/docImgs/runConfigDeploymentTab.png?raw=true)
##### and the Tomcat server dialog
![Application Server Dialog](./hapi-fhir-jpaserver-example/docImgs/applicationServerDialog.png?raw=true)

### Testing the modifications to the example server

Use the Restful client of your choice to run tests.

Test ImageStudy query
```
http://localhost:8080/baseDstu3/ImagingStudy?patient=smart-1288992
Authorization: Bearer authorizationTokenGoesHere
```

GET Test WADO Study request (include Authorization header for authentication)
```
http://localhost:8080/baseDstu3/studies/1.3.6.1.4.1.14519.5.2.1.6279.6001.270617793094907821983261388534
Authorization: Bearer authorizationTokenGoesHere
```

To Test introspection service directly
```
POST http://localhost:9004/api/introspect
Content-Type: application/x-www-form-urlencoded
Accept-Charset: utf-8
and, in the request body:
?token=authorizationTokenGoesHere$patient=smart-1288992
```
### How to generate an authentication token for testing
_(This applies if you are using Matt Kelsey's introspection server.)_

1. Navigate to https://tests.demo.syncfor.science

![s4s-1](./hapi-fhir-jpaserver-example/docImgs/s4s-1.png?raw=true)

2. Select Vendor: SMART
3. Click on "Show more options"

![s4s-1](./hapi-fhir-jpaserver-example/docImgs/s4s-2.png?raw=true)

4. Next to "Tags", click on "None"
5. then click on "patient-demographics"

![s4s-1](./hapi-fhir-jpaserver-example/docImgs/s4s-3.png?raw=true)

6. Click on the "Run tests" button.
7. A "Tests complete!" dialog will display; click on its "x"
to dismiss it.
8. On the right panel, click on "Feature: Patient demographics".
A "Method URL" table with one row should appear.

![s4s-1](./hapi-fhir-jpaserver-example/docImgs/s4s-4.png?raw=true)

9. Click on the URL, which should read:
```
https://portal.demo.syncfor.science/api/fhir/Patient/smart-1288992
```
10. A new tab or window should open, showing an http Request and
Response beginning:
```
Request
-------
GET https://portal.demo.syncfor.science/api/fhir/Patient/smart-1288992
Accept-Encoding: deflate,sdch
User-Agent: python-requests/2.10.0
Accept: application/json
Connection: keep-alive
Authorization: Bearer Y4ljzndNNkvayLME2HphxBJTs08SUo
```
11. Copy the bearer token to your http Request Authorization header.
Not sure how long the tokens remain valid.




