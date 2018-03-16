#!/bin/bash

function helpMsg
{
echo create one or more test data sets and send them to SCP
echo usage:
echo createTestDataSets zipURL targetSCP
echo where:
echo zipURL is URL of ftp directory with zip files
echo targetSCP is destination AE_TITLE@host:port
echo 'for example:'
echo createTestDataSets DCM4CHEE@localhost:11112
exit
}

currDir="`dirname "$0"`"
cd ${currDir}

#------------- proper number of parameters
if [ $# -ne 2 ]; then
   helpMsg
fi

#----------------------------------------- parameters
modDir=${currDir}/mod
tmpFolder=${currDir}/tmp
outFolder=${currDir}/out

zipURL=$1
targetSCP=$2

#-------------------------------- modDir must exist as dir
if [ ! -d ${modDir} ]; then
   echo modification file directory ${modDir} not found
   exit
fi

#-------------- tmpFolder must exist as dir, or be created
if [ -f ${tmpFolder} ]; then
   echo A file named ${tmpFolder} exists
   exit
fi
mkdir -p ${tmpFolder}

#-------------- outFolder must exist as dir, or be created
if [ -f ${outFolder} ]; then
   echo A file named ${outFolder} exists
   exit
fi
mkdir -p ${outFolder}

#---------------------------- scan .mod files in directory
for modFile in ${modDir}/*.mod; do
#------------ eliminate non files and handle no match case
   [ -f "${modFile}" ] || continue
#---------------------------------- clear temp directories
   rm -Rf ${tmpFolder}/*
   rm -Rf ${outFolder}/*

   name=$(basename "${modFile}" .mod)
   zipName=${name}.zip

   echo '************************************' Downloading ${name}.zip
#------------------------- download the zip file of images
   wget -o log.txt -O "${tmpFolder}/${zipName}" "${zipURL}/${zipName}"
   status=$?
   if [ $status -gt 0 ]; then
      echo wget exit with error code $?
      echo see log.txt \for details
      continue
   else
      echo "${zipURL}/${zipName}" file downloaded.
   fi

#---------------------------------------- unzip the images
   echo '************************' unzipping "${name}" into tmp folder
   unzip -d "${tmpFolder}" -u "${tmpFolder}/${zipName}" \*.dcm  > unzip.log
   status=$?
   if [ $status -gt 0 ]; then
      echo gunzip exited with status $status
      if [ $status -eq 1 ]; then
         continue
      fi
   else
      echo "${zipName}" unzipped into ${tmpFolder}.
   fi
   rm "${tmpFolder}/${zipName}"

   echo '********************************************' converting ${name}
   /bin/bash ./bin/updtdcms -i "${tmpFolder}" -o "${outFolder}" -t "${modFile}"
   status=$?
   if [ $status -gt 0 ]; then
      echo updtdcms exited with status $status
      if [ $status -eq 1 ]; then
         continue
      else
         exit
      fi
   else
      echo ${name} files updated into ${outFolder}.
   fi

#----------------------------------- send images to archive

   echo '********************************************' sending ${name}
   bin/storescu -c ${targetSCP} ${outFolder}
   status=$?
   if [ ${status} -gt 0 ]; then
      echo storescu exited with status ${status}
   else
      echo '********************************' storescu complete.
   fi
done
