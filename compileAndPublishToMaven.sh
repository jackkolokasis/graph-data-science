#!/usr/bin/env bash

export JAVA_HOME=/spare/kolokasis/dev/teraheap/jdk17u067/build/linux-x86_64-server-release/jdk
#export JAVA_HOME=/usr/java/jdk-17.0.4.1

# Define some variables for pretty printing
ESC='\033[' 
# Attributes
NORMAL=0
BOLD=1
# Foreground colors
GREEN_FG=32
# Presets
BGREEN=${ESC}${BOLD}';'${GREEN_FG}'m'
RESET=${ESC}${NORMAL}'m'

# Check if the last command executed succesfully
#
# if executed succesfully, print SUCCEED
# if executed with failures, print FAIL and exit
check () {
    if [ "$1" -ne 0 ]
    then
        echo -e "  $2 \e[40G [\e[31;1mFAIL\e[0m]"
        exit
    else
        echo -e "  $2 \e[40G [\e[32;1mSUCCED\e[0m]"
    fi
}

# Initialize an empty array
declare -a jar_files
declare -a file_paths

# Assign the output of the find command to the array variable
mapfile -t jar_files < <(find ~/.m2/repository/org/neo4j/gds \
  -name "2.7.0-alpha*" -type d -print0 \
  | xargs -0 -I {} find {} -type f -name "*.jar" -printf "%P\n";)

mapfile -t file_paths < <(find ~/.m2/repository/org/neo4j/gds \
  -name "2.7.0-alpha*" -type d -print0 \
  | xargs -0 -I {} find {} -type f -name "*.jar" \
  -exec sh -c 'echo "$(dirname "{}")/$(basename "{}")"' \;)

echo "Building graph data scince library"
./gradlew build -x test
  
retValue=$?
message="Building GDS library" 
check ${retValue} "${message}"

for ((i=0; i<${#jar_files[@]}; i++ ))
do
  search_file=$(find "$(pwd)" -name "${jar_files[$i]}")
  cp "$search_file" "${file_paths[$i]}"
  echo -ne "Update File: $(basename "${search_file}")"
  echo -e "\t[${BGREEN}DONE${RESET}]"
done
