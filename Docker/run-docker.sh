#!/bin/bash -e

# First build the docker image from the dockerfile
sudo docker build -t modelserver ./

# Hostname for docker container
dockerHostname="modelserver-docker"

modelServerTestJar=`ls ModelServer-*-tests.jar`
modelServerMainJar=`ls ModelServer*[^tests].jar`

if [[ "$1" == "test" ]]
then
    set -x
      sudo docker run -h $dockerHostname \
          -p 18080:18080 \
            --rm \
            -it \
            --name modelserverContainer \
            --entrypoint /modelserver/run-tucana-tests.sh \
            modelserver \
            $modelServerMainJar $modelServerTestJar $@
else
    echo "** Dropping into bash with all dependencies"
    # Using getopts to get command line arguments
    hostFolder=""
    mntFolder="/Tucana/project"
    mountCmd=""
    while getopts ":m:" Opt
    do
      case $Opt in
        m )
          echo "** Mounting folder ${OPTARG} to /project"
          hostFolder=$OPTARG
          mountCmd="--mount type=bind,source=$hostFolder,target=$mntFolder";;
        * ) echo "Unknown option $Opt";;
      esac
    done

     sudo docker run -h $dockerHostname \
          -p 18080:18080 \
          --rm \
          -it \
          --name modelserverContainer \
        $mountCmd \
        --entrypoint /modelserver/run-tucana.sh \
        modelserver
fi
