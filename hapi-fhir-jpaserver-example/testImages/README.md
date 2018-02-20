### Suggested method to create studies for test purposes

#### Assumes

One or more DICOM files in a zip file on an ftp site for which you have
a URL. For example:
ftp://ftp.ihe.net/image_sharing/test-data/s4s-testing/IDS-DEPT003-a.zip

dcm4che utilities installed on your system, specifically storescu.

Access to your archive, specifically a Storage Service Class Provider
(SCP), for example: DCM4CHEE@localhost:11112

Appropriate patient demographic values for the patient you will be
testing, for example:
- AccessionNumber (0008,0050)  - 9812345
- PatientAge (0010,1010)       -  66
- PatientBirthDate (0010,0030) - 19510417
- PatientID (0010,0020)        - smart-1288992
- PatientName (0010,0010)      - Adams Pat
- PatientSex (0010,0040)       - M

An OID suffix for the study, series, and instance uids of the images,
which should be selected to make these id's unique in your archive. For
example:  "230113.17".

#### Method

Place the patient demographic values in a text file, one per line. For
example (See modifications.txt in this directory):
```
"AccessionNumber=7610982"
"PatientAge=65"
"PatientBirthDate=19520417"
"PatientID=br-549"
"PatientName=Granger^Farley"
"PatientSex=M"
```
Notes: DICOM attributes may be referred to by keyword or tag value. Tag
values are represented as 8 digit hex, without parentheses or comma.

Run the bash script createTestDataSet.sh (in this directory).
For example:
```
createTestDataSet.sh \
   ftp://ftp.ihe.net/image_sharing/test-data/s4s-testing/IDS-DEPT003-a.zip \
   modifications.txt 230113.17 DCM4CHEE@10.252.175.44:11112
```
Note: There is additional documentation in the bash script itself.

For additional documentation on storescu, type:
```
storescu -h
```
Windows storescu.bat file is located in bin directory.



