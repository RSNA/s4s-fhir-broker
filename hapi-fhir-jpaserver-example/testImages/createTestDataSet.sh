#!/bin/bash

function help
{
echo create test data set and send to SCP
echo usage:
echo createTestDataSet zipURL modFile uidSuffix targetSCP tmpFolder
echo where:
echo zipURL ftp URL of zip file containing base image set
echo modFile pfn of file containing modifications to make to image dicom tags
echo targetSCP, AE_TITLE@host:port
echo tmpFolder folder to use \for temporary work, default \"tmp\"
echo modFile and tmpFolder are absolute or relative to current working directory
exit
}

#------------- proper number of parameters
if [ $# -lt 4 ] || [ $# -gt 5 ]; then
   help
fi

#------------- pull command line parameters
zipURL=$1
modFile=$2
uidSuffix=$3
targetSCP=$4
if [ $# -eq 5 ]; then
   tmpFolder=$5
else
   tmpFolder="tmp"
fi

#-------- resolve modFile and tmpFolder pfn's if relative
currDir=$(pwd)
if [ ${modFile:0:1} != "/" ]; then
   modFile=${currDir}/${modFile};
fi
if [ ${tmpFolder:0:1} != "/" ]; then
   tmpFolder=${currDir}/${tmpFolder};
fi

#------------------------------------- modFile must exist
if [ ! -f ${modFile} ]; then
   echo modification file ${modFile} not found
   exit
fi
#---------------- tmpDir must exist as dir, or be created
if [ -f ${tmpFolder} ]; then
   echo A file named ${tmpFolder} exists
   exit
fi
mkdir -p ${tmpFolder}

#------------------------- tmpDir is current
cd ${tmpFolder}
rm -Rf *

#------------------ download the zip file of images
zipName=${zipURL##*/}
wget -o ../log.txt -O ${zipName} ${zipURL}
if [ $? -gt 0 ]; then
   echo wget exit with error code $?
   echo see ${tmpFolder}/log.txt \for details
   exit
else
   echo ${zipURL} file downloaded.
fi

#-------------------------- unzip the imagesh
unzip -u ${zipName} \*.dcm
status=$?
if [ $status -gt 0 ]; then
   echo gunzip exited with status $status
   if [ $status -eq 1 ]; then
      exit
   fi
else
   echo ${zipName} unzipped into ${tmpFolder}.
fi
rm ${zipName}

#------- find longest UID

find . -type f -exec dcm_dump_file -t -Z {} \; | egrep "0020 000d|0020 000e|0008 0018" >> t

longestUID=$(cut -d / -f 5 t | wc -L)

maxUID=$((${longestUID} + ${#uidSuffix}))
rm t

if [ ${maxUID} -gt 64 ]; then
   echo uidSuffix ${uidSuffix} too long, would create {maxUID} character UIDs
   exit
else
   echo uidSuffix ${uidSuffix} OK- Will generate max {maxUID} character UIDs
fi

#---------------------- pull changes from modFile
chgs="";
while read -r var
do
   echo Mod read from ${modFile} - ${var}
   chgs="${chgs} -s ${var}"
done < ${modFile}

#----------------- send images to archive
find ${tmpFolder} -type d >> t
folders=""
while read -r var
do
   echo img folder ${var}
   folders="${folders} ${var}"
done < t
rm t
echo folders is ${folders}

echo storescu --uid-suffix ${uidSuffix} ${chgs} -c ${targetSCP} ${folders}

status=$?
if [ ${status} -gt 0 ]; then
   echo storescu exited with status ${status}
else
   echo storescu complete.
fi
