#!/bin/bash
# Copyright 2020 Alibaba Group Holding Limited.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This script is used to generate the Java SDK from the Flex Interactive API
# It uses the Swagger Codegen tool to generate the SDK

PACKAGE_NAME="com.alibaba.graphscope.interactive"
PYTHON_PACKAGE_NAME="gs_interactive"
PYTHON_ADMIN_PACKAGE_NAME="gs_interactive_admin"
GROUP_ID="com.alibaba.graphscope"
ARTIFACT_ID="interactive"
ARTIFACT_URL="https://github.com/alibaba/GraphScope/tree/main/flex/interactive"
VERSION="0.3"
EMAIL="graphscope@alibaba-inc.com"
DESCRIPTION="GraphScope Interactive Java SDK"
ORGANIZATION="Alibaba GraphScope"
developerOrganizationUrl="https://graphscope.io"
DEVELOPER_NAME="GraphScope Team"
LICENSE_NAME="Apache-2.0"
LICENSE_URL="https://www.apache.org/licenses/LICENSE-2.0.html"
LOG_LEVEL="INFO"
export OPENAPI_GENERATOR_VERSION=7.2.0

#get current bash scrip's directory
CUR_DIR=$(cd `dirname $0`; pwd)
OPENAPI_SPEC_PATH="${CUR_DIR}/../../openapi/openapi_interactive.yaml"


function usage() {
  echo "Usage: $0 [options]"
  echo "Options:"
  echo "  -h, --help        Show this help message and exit"
  echo "  -g, --lang        Generate the SDK for the specified language"
  echo "                    Supported languages: java/python"
}

function do_gen_java() {
    if [ $# -ne 1 ]; then
        err "Invalid number of arguments:$#"
        usage
        exit 1
    fi
    type=$(echo $1 | tr '[:upper:]' '[:lower:]')
    if [ "$type" != "client" ]; then
        err "Currently only support client type when generating Java Code"
        usage
        exit 1
    fi

    echo "Generating Java SDK"
    OUTPUT_PATH="${CUR_DIR}/java/"


    addtional_properties="licenseName=${LICENSE_NAME},licenseUrl=${LICENSE_URL},artifactUrl=${ARTIFACT_URL}"
    addtional_properties="${addtional_properties},artifactDescription=\"${DESCRIPTION}\",developerEmail=${EMAIL}"
    addtional_properties="${addtional_properties},developerName=\"${DEVELOPER_NAME}\",developerOrganization=\"${ORGANIZATION}\""
    addtional_properties="${addtional_properties},developerOrganizationUrl=${developerOrganizationUrl}"
    addtional_properties="${addtional_properties},artifactVersion=${VERSION},hideGenerationTimestamp=true"
    export JAVA_OPTS="-Dlog.level=${LOG_LEVEL}"

    cmd="openapi-generator-cli generate -i ${OPENAPI_SPEC_PATH} -g java -o ${OUTPUT_PATH}"
    cmd=" ${cmd} --api-package ${PACKAGE_NAME}.api"
    cmd=" ${cmd} --model-package ${PACKAGE_NAME}.models"
    cmd=" ${cmd} --invoker-package ${PACKAGE_NAME}"
    cmd=" ${cmd} --package-name ${PACKAGE_NAME}"
    cmd=" ${cmd} --artifact-id ${ARTIFACT_ID}"
    cmd=" ${cmd} --group-id ${GROUP_ID}"
    cmd=" ${cmd} --additional-properties=${addtional_properties}"
    echo "Running command: ${cmd}"

    eval $cmd
}

function do_gen_python() {
    if [ $# -ne 1 ]; then
        err "Invalid number of arguments:$#"
        usage
        exit 1
    fi
    type=$(echo $1 | tr '[:upper:]' '[:lower:]')
    if [ "$type" == "client" ]; then
        echo "Generating Python SDK"
        OUTPUT_PATH="${CUR_DIR}/python"
        export JAVA_OPTS="-Dlog.level=${LOG_LEVEL}"
        cmd="openapi-generator-cli generate -i ${OPENAPI_SPEC_PATH} -g python -o ${OUTPUT_PATH}"
        cmd=" ${cmd} --package-name ${PYTHON_PACKAGE_NAME}"
        cmd=" ${cmd} --additional-properties=packageVersion=${VERSION},pythonVersion=3"
        echo "Running command: ${cmd}"
        eval $cmd
    elif [ "$type" == "server" ]; then
        echo "Generating Python Server"
        OUTPUT_PATH="${CUR_DIR}/master"
        export JAVA_OPTS="-Dlog.level=${LOG_LEVEL}"
        cmd="openapi-generator-cli generate -i ${OPENAPI_SPEC_PATH} -g python-flask -o ${OUTPUT_PATH}"
        cmd=" ${cmd} --package-name ${PYTHON_ADMIN_PACKAGE_NAME}"
        cmd=" ${cmd} --additional-properties=packageVersion=${VERSION},pythonVersion=3"
        echo "Running command: ${cmd}"
        eval $cmd
    else
        err "Unsupported type: $type"
        usage
        exit 1
    fi
}

function do_gen_spring() {
    echo "Generating Spring API"
    OUTPUT_PATH="${CUR_DIR}/../../../interactive_engine/groot-http" 
    GROOT_PACKAGE_NAME="com.alibaba.graphscope.groot"
    GROOT_ARTIFACT_ID="groot-http"
    additional_properties="apiPackage=${GROOT_PACKAGE_NAME}.service.api,modelPackage=${GROOT_PACKAGE_NAME}.service.models,artifactId=${GROOT_ARTIFACT_ID},groupId=${GROUP_ID},invokerPackage=${GROOT_PACKAGE_NAME}"
    export JAVA_OPTS="-Dlog.level=${LOG_LEVEL}"
    cmd="openapi-generator-cli generate -i ${OPENAPI_SPEC_PATH} -g spring -o ${OUTPUT_PATH}"
    cmd=" ${cmd} --additional-properties=${additional_properties}"
    echo "Running command: ${cmd}"

    eval $cmd
}

function do_gen() {
    if [ $# -ne 2 ]; then
        err "Invalid number of arguments:$#"
        usage
        exit 1
    fi
    # to lower case
    lang=$(echo $1 | tr '[:upper:]' '[:lower:]')
    type=$(echo $2 | tr '[:upper:]' '[:lower:]')
    case $lang in
    java)
        do_gen_java $type
        ;;
    python)
        do_gen_python $type
        ;;
    spring)
        do_gen_spring
        ;;
    *)
        err "Unsupported language: $lang"
        usage
        exit 1
        ;;
    esac
}

if [ $# -eq 0 ]; then
  usage
  exit 1
fi

function install_generator() {
  CLI_INSTALLED=false
  JQ_INSTALLED=false
  MVN_INSTALLED=false
  # first check openapi-generator-cli exists is executable
  if [ -f ~/bin/openapitools/openapi-generator-cli ]; then
    echo "openapi-generator-cli is already installed"
    export PATH=$PATH:~/bin/openapitools/
  fi
  if command -v openapi-generator-cli &>/dev/null; then
    echo "openapi-generator-cli is already installed"
    CLI_INSTALLED=true
  fi
  if ! $CLI_INSTALLED; then
    echo "Installing openapi-generator-cli"
    mkdir -p ~/bin/openapitools
    curl https://raw.githubusercontent.com/OpenAPITools/openapi-generator/master/bin/utils/openapi-generator-cli.sh > ~/bin/openapitools/openapi-generator-cli
    chmod u+x ~/bin/openapitools/openapi-generator-cli
    export PATH=$PATH:~/bin/openapitools/
  fi
  # on ubuntu apt-get jq on mac brew install jq

  if command -v jq &>/dev/null; then
    echo "jq is already installed"
    JQ_INSTALLED=true
  fi
  if command -v mvn &>/dev/null; then
    echo "maven is already installed"
    MVN_INSTALLED=true
  fi
  if [[ "$(uname -s)" == "Linux" ]]; then
    if ! $JQ_INSTALLED; then
      sudo apt-get update && sudo apt-get -y install jq
    fi
    if ! $MVN_INSTALLED; then
      sudo apt-get update && sudo apt-get -y install maven
    fi
  elif [[ "$(uname -s)" == "Darwin" ]]; then
    if ! $JQ_INSTALLED; then
      brew install jq
    fi
    if ! $MVN_INSTALLED; then
      brew install maven
    fi
  else
    echo "Unsupported OS"
    exit 1
  fi
}

install_generator
type="client" # client or server
language=""

while [[ $# -gt 0 ]]; do
  key="$1"

  case $key in
  -h | --help)
    usage
    exit
    ;;
  -g | --lang)
    shift
    language="$1"
    shift
    ;;
  -t | --type)
    shift
    type="$1"
    shift
    ;;
  *) # unknown option
    err "unknown option $1"
    usage
    exit 1
    ;;
  esac
done

do_gen $language $type