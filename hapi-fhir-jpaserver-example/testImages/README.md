## Creating and loading test image sets

I used the DICOM CTN command line utilities, which I installed on my
Ubuntu machine using:
```
sudo apt-get install ctn
```
This gave me version 3.2.0

I have included in this directory a pdf (ctn_mesa_utilities.pdf) which
gives instructions for using the utilities.

### Modifying DICOM header tags

I am assuming that you have some dicom image files which will work for
testing, but that you need to modify some of the header tags to match
the fhir data.

The tags you most likely will want to modify (the values are from my
smart-1288992 patient test:

- (0x0008,0x0016) SOP Class UID = 1.2.840.10008.5.1.4.1.1.1
- (0x0008,0x0018) SOPInstanceUID = 1.3.6.1.4.1.14519.5.2.1.6279.6001.270617793.1.1
- (0x0008,0x0020) StudyDate = 20000101
- (0x0008,0x0030) StudyTime = empty
- (0x0008,0x0050) AccessionNumber = 2819497684894126
- (0x0010,0x0030) PatientBirthDate = 19251223
- (0x0010,0x0020) PatientID = smart-1288992
- (0x0012,0x0062) PatientIdentityRemoved = YES
- (0x0010,0x0010) PatientName = Adams^Daniel
- (0x0020,0x000d) StudyInstanceUID = 1.3.6.1.4.1.14519.5.2.1.6279.6001.270617793
- (0x0020,0x000e) SeriesInstanceUID = 1.3.6.1.4.1.14519.5.2.1.6279.6001.270617793

I used the ctn dcm_modify_object utility to modify header tags, putting
the changes I wanted to make in a file (mods):

```
-i "(0008,0018)=1.3.6.1.4.1.14519.5.2.1.6279.6001.270617793.1.1"
-i "(0010,0020)=smart-1288992"
-i "(0020,000d)=1.3.6.1.4.1.14519.5.2.1.6279.6001.270617793"
-i "(0020,000e)=1.3.6.1.4.1.14519.5.2.1.6279.6001.270617793.1"
```
 and running the command:
 ```
 dcm_modify_object -i mods 1288992-adams.dcm smart-1288992-adams.dcm
 ```
 to perform the conversion.

 The mods file and a bash script (convert.sh) are in this directory.

### Loading images to the dcm4chee archive

You need to have the dcm4chee archive running, which you can accomplish:

1. By bringing up the whole stack, or
2. Just bringing up the archive, as described in the main readme for this
project, in the section entitled, "Set up a dcm4chee image archive for
testing".

Assuming that the default values are used, the DICOM port on the
dcm4chee server will be 11112, its AE title DCM4CHEE.

I used the CTN send_image utility (also described in
ctn_mesa_utilities.pdf). To send the image used as an example above:
```
send_image -c DCM4CHEE localhost 11112 smart-1288992-adams.dcm
```
This sends one image. send_image supports multiple file names and wild
cards, which can be used to send groups of images.

**Note:** The archive stores images by their study, series, and instance
uids. Sending multiple images with the same uids but different demographics
will muck up the database. If you want to clear the database and start
over, shut down the archive dockers, delete the directory
"/var/local/dcm4chee-arc" on your host machine (root access will be
required) and start the archive dockers again.

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



