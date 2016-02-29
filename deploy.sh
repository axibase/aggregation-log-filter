#!/bin/bash

. deploy.config

mvn -s settings.xml clean javadoc:jar \
source:jar -Dgpg.keyname=$GPG_KEYNAME -Dgpg.passphrase=$GPG_PASSPHRASE deploy
