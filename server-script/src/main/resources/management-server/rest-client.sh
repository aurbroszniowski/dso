#!/bin/bash


export main_class="org.terracotta.management.cli.rest.RestCli"
export cli_name="__hidden__"

unset CDPATH
root=`dirname $0`

$root/management-cli-common.sh "$@"
exit $?