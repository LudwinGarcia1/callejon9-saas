# Jenkins LTS controller for RestoHub CI. Upgrade: change the tag and digest together.
FROM jenkins/jenkins:2.580.1-lts-jdk21@sha256:a660310e39ade10631f774bacd5219de767dfd08947ea5c32a739b5e5bb382c1

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false" \
    CASC_JENKINS_CONFIG=/usr/share/jenkins/casc/jenkins.yaml \
    PLUGINS_FORCE_UPGRADE=true

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt --latest false

COPY casc/ /usr/share/jenkins/casc/
# The .override suffix makes Jenkins refresh the script on every start, even with a persisted JENKINS_HOME.
COPY init.groovy.d/write-agent-secret.groovy /usr/share/jenkins/ref/init.groovy.d/write-agent-secret.groovy.override
