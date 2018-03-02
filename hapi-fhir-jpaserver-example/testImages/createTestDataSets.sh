#!/bin/bash

function help
{
echo create one or more test data sets and send them to SCP
echo usage:
echo createTestDataSet zipURL modDir targetSCP tmpDir
echo where:
echo zipURL ftp URL of a directory containing one or more zip files of images,
echo zip files expected to have .zip extension.
echo modDir directory containing one or more modification files to make to image dicom tags,
echo modification files must have .mod extension, must match up to ftp .zip files
echo targetSCP, AE_TITLE@host:port
echo tmpDir directory to use \for temporary work, default \"tmp\"
echo modDir and tmpDir are absolute or relative to current working directory
exit
}

#------------- proper number of parameters
if [ $# -lt 3 ] || [ $# -gt 4 ]; then
   help
fi

#------------- pull command line parameters
zipURL=$1
modDir=$2
targetSCP=$3
if [ $# -eq 4 ]; then
   tmpDir=$4
else
   tmpDir="tmp"
fi

#-------- resolve modDir and tmpDir pfn's if relative
currDir=$(pwd)
if [ ${modDir:0:1} != "/" ]; then
   modDir=${currDir}/${modDir};
fi
if [ ${tmpDir:0:1} != "/" ]; then
   tmpFolder=${currDir}/${tmpDir};
fi

#-------------------------------- modDir must exist as dir
if [ ! -d ${modDir} ]; then
   echo modification file directory ${modDir} not found
   exit
fi
#---------------- tmpDir must exist as dir, or be created
if [ -f ${tmpFolder} ]; then
   echo A file named ${tmpFolder} exists
   exit
fi
mkdir -p ${tmpFolder}
cd ${tmpFolder}

#---------------------------- scan .mod files in directory
for modFile in ${modDir}/*.mod; do
#------------ eliminate non files and handle no match case
   [ -f "$modFile" ] || continue
#------------------------------------ clear temp directory
   rm -Rf *

   name=$(basename "$modFile" .mod)
   zipName=${name}.zip

echo '********************************************' Downloading ${name}.zip
#------------------------- download the zip file of images
   wget -o ../log.txt -O "${zipName}" "${zipURL}/${zipName}"
   status=$?
   if [ $status -gt 0 ]; then
      echo wget exit with error code $?
      echo see log.txt \for details
      continue
   else
      echo "${zipURL}/${zipName}" file downloaded.
   fi

#---------------------------------------- unzip the images
echo '********************************************' unzipping ${name}.zip
   unzip -u "${zipName}" \*.dcm  > unzip.log
   status=$?
   if [ $status -gt 0 ]; then
      echo gunzip exited with status $status
      if [ $status -eq 1 ]; then
         continue
      fi
   else
      echo "${zipName}" unzipped into ${tmpFolder}.
   fi
#   rm "${zipName}"

#----------------------------------------- find longest UID
echo '********************************' Calculating longest UID in ${name}
   find . -type f -exec dcmdump -w 125 {} \; | egrep "(0020,000D)|(0020,000E)|(0008,0018)" >> t

   cut -d \[ -f 2 t | cut -d \] -f 1 > ../"${name}.uids"
   longestUID=$(cat ../"${name}.uids" | wc -L)
   uidSuffix=$(grep Suffix "$modFile" | cut -d \= -f 2 | cut -d \" -f 1)

   maxUID=$((${longestUID} + ${#uidSuffix}))
   rm t

   if [ ${maxUID} -gt 64 ]; then
      echo uidSuffix ${uidSuffix} too long, would create ${maxUID} character UIDs
      continue
   else
      echo uidSuffix ${uidSuffix} OK- Will generate max ${maxUID} character UIDs
   fi
#--------------------------------- pull changes from modFile
echo '********************************' Pulling changes from ${name}.mod
   changes="";
   while read -r var
   do
      if [ ${var} = *"Suffix"* ]; then
         continue
      fi
      echo Mod read from "${modFile}" - ${var}
      changes="${changes} -s ${var}"
   done < "${modFile}"

#----------------------------------- send images to archive
echo '********************************' Sending ${name} images
   find ${tmpFolder} -type d >> t
   folders=""
   while read -r var
   do
      echo img folder ${var}
      folders="${folders} ${var}"
   done < t
   rm t
   echo folders is ${folders}

   echo storescu --uid-suffix ${uidSuffix} ${changes} -c ${targetSCP} ${folders}

   status=$?
   if [ ${status} -gt 0 ]; then
      echo storescu exited with status ${status}
   else
      echo '********************************' storescu complete.
   fi
done
