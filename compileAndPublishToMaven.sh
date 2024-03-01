#!/usr/bin/env bash

export JAVA_HOME=/spare/kolokasis/dev/teraheap/jdk17u067/build/linux-x86_64-server-release/jdk

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

jar_files=( "proc-common-1.1.1.jar" \
  "cypher-printer-1.1.1.jar" \
  "alpha-algo-1.1.1.jar" \
  "neo4j-export-adapter-1.1.1.jar" \
  "proc-centrality-1.1.1.jar" \
  "proc-similarity-1.1.1.jar" \
  "algo-1.1.1.jar" \
  "neo4j-adapter-1.1.1.jar" \
  "alpha-core-1.1.1.jar" \
  "proc-community-1.1.1.jar" \
  "proc-1.1.1.jar" \
  "annotations-1.1.1.jar" \
  "alpha-proc-1.1.1.jar" \
  "algo-common-1.1.1.jar" \
  "proc-catalog-1.1.1.jar" \
  "core-1.1.1.jar" \
  "proc-beta-1.1.1.jar" \
  "neo4j-collections-1.1.1.jar" )

file_paths=( "${HOME}/.m2/repository/org/neo4j/gds/proc-common/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/cypher-printer/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/alpha-algo/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/neo4j-export-adapter/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/proc-centrality/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/proc-similarity/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/algo/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/neo4j-adapter/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/alpha-core/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/proc-community/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/proc/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/annotations/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/alpha-proc/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/algo-common/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/proc-catalog/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/core/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/proc-beta/1.1.1/" \
  "${HOME}/.m2/repository/org/neo4j/gds/neo4j-collections/1.1.1/" )

echo "Building graph data scince library"
#./gradlew build -x test --info
./gradlew build
  
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
