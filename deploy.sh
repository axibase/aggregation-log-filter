#!/bin/bash

. deploy.config

mvn -s settings.xml clean javadoc:jar \
source:jar -Dgpg.passphrase=$GPG_PASSPHRASE deploy
