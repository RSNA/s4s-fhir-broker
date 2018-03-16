### Suggested method to create studies for test purposes

#### Assumes

You have unzipped the createTestDataSets.zip file into a directory of
your choice which has enough free space to hold several copies of the
largest study you are going to process.

One or more DICOM studies in zip files on an ftp site for which you have
a URL. For example:
ftp://ftp.ihe.net/image_sharing/test-data/s4s-testing

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

#### Method

Place the patient demographic values for each study (in a file with the
file extension ".mod" located in the mod subdirectory) with one Tag and
its value per line. For example (See .mod files in the mod subdirectory):
```
uidBase=1.2.143542
AccessionNumber=7610982
PatientAge=65
PatientBirthDate=19520417
PatientID=br-549
PatientName=Granger^Farley
PatientSex=M
```
Notes:
1. DICOM attributes may be referred to by keyword or tag value. Tag
values are represented as 8 digit hex, without parentheses or comma.
2. The name of the .mod file must be the same as the corresponding .zip
file in the ftp site directory, including spaces.
3. The additional tag "uidBase" may be added with a UID base string for
the study. If you do not include this parameter, one will be created by
the script.
4. The existing .mod files in the mod subdirectory assume that you are
generating test data from the ftp site given above. If you are, simply
modify these files to contain the tag values you wish. If you are not
using the ftp site above, delete these .mod files from the mod
subdirectory before running the script.

Run the bash script createTestDataSet.sh (in this directory).
For example:
```
createTestDataSet.sh \
   ftp://ftp.ihe.net/image_sharing/test-data/s4s-testing/IDS-DEPT003-a.zip \
   DCM4CHEE@10.252.175.44:11112
```
Notes:
1. There is additional documentation in the bash script itself.
2. The script will look for a zip file on the ftp site for each .mod
file in the mod subdirectory, download and unzip the file, process each
image file found, updating the demographic values and generating new
UIDs, and finally send the updated files to the archive.










