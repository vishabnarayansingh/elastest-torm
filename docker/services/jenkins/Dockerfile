FROM openjdk:8-jdk

USER root

LABEL io.elastest.type=tool

RUN apt-get update && apt-get install -y git curl && rm -rf /var/lib/apt/lists/*

ARG user=jenkins
ARG group=jenkins
ARG uid=1000
ARG gid=1000
ARG http_port=8080
ARG agent_port=50000
ARG JENKINS_HOME=/var/jenkins_home
ARG TMP=/tmp

ENV JENKINS_HOME $JENKINS_HOME
ENV JENKINS_SLAVE_AGENT_PORT ${agent_port}

# Jenkins is run with user `jenkins`, uid = 1000
# If you bind mount a volume from the host or a data container,
# ensure you use the same uid
RUN mkdir -p $JENKINS_HOME \
  && chown ${uid}:${gid} $JENKINS_HOME \
  && groupadd -g ${gid} ${group} \
  && useradd -d "$JENKINS_HOME" -u ${uid} -g ${gid} -m -s /bin/bash ${user}

# Jenkins home directory is a volume, so configuration and build history
# can be persisted and survive image upgrades
VOLUME $JENKINS_HOME

# Set the jobs names
ARG job_name_1="jenkins-junit5-unit-test"
ARG job_name_2="jenkins-junit5-multi-browser-test"
ARG job_name_3="jenkins-junit5-rest-test"
ARG job_name_4="jenkins-openvidu-example"
ARG job_name_5="jenkins-fullteaching-example"
ARG job_name_6="jenkins-junit5-rest-test-on-k8s"
ARG job_name_7="jenkins-junit5-multi-browser-test-on-k8s"
ARG job_name_8="jenkins-openvidu-example-on-k8s"
ARG job_name_9="jenkins-fullteaching-example-on-k8s"

RUN mkdir -p "$TMP"/workspace/

# Create the job workspaces  
RUN mkdir -p "$TMP"/workspace/${job_name_1}
RUN mkdir -p "$TMP"/workspace/${job_name_2}
RUN mkdir -p "$TMP"/workspace/${job_name_3}
RUN mkdir -p "$TMP"/workspace/${job_name_4}
RUN mkdir -p "$TMP"/workspace/${job_name_5}
RUN mkdir -p "$TMP"/workspace/${job_name_6}
RUN mkdir -p "$TMP"/workspace/${job_name_7}
RUN mkdir -p "$TMP"/workspace/${job_name_8}
RUN mkdir -p "$TMP"/workspace/${job_name_9}

# Create the jobs folder recursively  
RUN mkdir -p "$TMP"/jobs/${job_name_1}  
RUN mkdir -p "$TMP"/jobs/${job_name_2}  
RUN mkdir -p "$TMP"/jobs/${job_name_3}  
RUN mkdir -p "$TMP"/jobs/${job_name_4}  
RUN mkdir -p "$TMP"/jobs/${job_name_5}
RUN mkdir -p "$TMP"/jobs/${job_name_6}
RUN mkdir -p "$TMP"/jobs/${job_name_7}  
RUN mkdir -p "$TMP"/jobs/${job_name_8}
RUN mkdir -p "$TMP"/jobs/${job_name_9}

# Add the custom configs to the container  
COPY /config/${job_name_1}_config.xml "$TMP"/jobs/${job_name_1}/config.xml
COPY /config/${job_name_2}_config.xml "$TMP"/jobs/${job_name_2}/config.xml
COPY /config/${job_name_3}_config.xml "$TMP"/jobs/${job_name_3}/config.xml
COPY /config/${job_name_4}_config.xml "$TMP"/jobs/${job_name_4}/config.xml
COPY /config/${job_name_5}_config.xml "$TMP"/jobs/${job_name_5}/config.xml
COPY /config/${job_name_6}_config.xml "$TMP"/jobs/${job_name_6}/config.xml
COPY /config/${job_name_7}_config.xml "$TMP"/jobs/${job_name_7}/config.xml
COPY /config/${job_name_8}_config.xml "$TMP"/jobs/${job_name_8}/config.xml
COPY /config/${job_name_9}_config.xml "$TMP"/jobs/${job_name_9}/config.xml

# Create build file structure  
RUN mkdir -p "$TMP"/jobs/${job_name_1}/latest/  
RUN mkdir -p "$TMP"/jobs/${job_name_1}/builds/1/
RUN mkdir -p "$TMP"/jobs/${job_name_2}/latest/  
RUN mkdir -p "$TMP"/jobs/${job_name_2}/builds/1/
RUN mkdir -p "$TMP"/jobs/${job_name_3}/latest/  
RUN mkdir -p "$TMP"/jobs/${job_name_3}/builds/1/
RUN mkdir -p "$TMP"/jobs/${job_name_4}/latest/  
RUN mkdir -p "$TMP"/jobs/${job_name_4}/builds/1/
RUN mkdir -p "$TMP"/jobs/${job_name_5}/latest/  
RUN mkdir -p "$TMP"/jobs/${job_name_5}/builds/1/
RUN mkdir -p "$TMP"/jobs/${job_name_6}/latest/  
RUN mkdir -p "$TMP"/jobs/${job_name_6}/builds/1/
RUN mkdir -p "$TMP"/jobs/${job_name_7}/latest/  
RUN mkdir -p "$TMP"/jobs/${job_name_7}/builds/1/
RUN mkdir -p "$TMP"/jobs/${job_name_8}/latest/  
RUN mkdir -p "$TMP"/jobs/${job_name_8}/builds/1/
RUN mkdir -p "$TMP"/jobs/${job_name_9}/latest/  
RUN mkdir -p "$TMP"/jobs/${job_name_9}/builds/1/

# `/usr/share/jenkins/ref/` contains all reference configuration we want
# to set on a fresh new installation. Use it to bundle additional plugins
# or config file with your custom jenkins Docker image.
RUN mkdir -p /usr/share/jenkins/ref/init.groovy.d

# Use tini as subreaper in Docker container to adopt zombie processes
ARG TINI_VERSION=v0.16.1
COPY tini_pub.gpg ${JENKINS_HOME}/tini_pub.gpg
RUN curl -fsSL https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-static-$(dpkg --print-architecture) -o /sbin/tini \
  && curl -fsSL https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-static-$(dpkg --print-architecture).asc -o /sbin/tini.asc \
  && gpg --no-tty --import ${JENKINS_HOME}/tini_pub.gpg \
  && gpg --verify /sbin/tini.asc \
  && rm -rf /sbin/tini.asc /root/.gnupg \
  && chmod +x /sbin/tini

COPY init.groovy /usr/share/jenkins/ref/init.groovy.d/tcp-slave-agent-port.groovy

# jenkins version being bundled in this docker image
ARG JENKINS_VERSION
ENV JENKINS_VERSION ${JENKINS_VERSION:-2.187}

# jenkins.war checksum, download will be validated using it
ARG JENKINS_SHA=ee58e028cd7520e5152abdea7bc19d73d194fb7283234edd8b9476d6e1e8a4bb

# Can be used to customize where jenkins.war get downloaded from
ARG JENKINS_URL=https://repo.jenkins-ci.org/public/org/jenkins-ci/main/jenkins-war/${JENKINS_VERSION}/jenkins-war-${JENKINS_VERSION}.war

# could use ADD but this one does not check Last-Modified header neither does it allow to control checksum
# see https://github.com/docker/docker/issues/8331
RUN curl -fsSL ${JENKINS_URL} -o /usr/share/jenkins/jenkins.war \
  && echo "${JENKINS_SHA}  /usr/share/jenkins/jenkins.war" | sha256sum -c -

ENV JENKINS_UC https://updates.jenkins.io
ENV JENKINS_UC_EXPERIMENTAL=https://updates.jenkins.io/experimental
ENV JENKINS_REPO https://repo.jenkins-ci.org/releases/org/jenkins-ci
ENV JENKINS_INCREMENTALS_REPO_MIRROR=https://repo.jenkins-ci.org/incrementals
RUN chown -R ${user} "$JENKINS_HOME" /usr/share/jenkins/ref

# for main web interface:
EXPOSE ${http_port}

# will be used by attached slave agents:
EXPOSE ${agent_port}

ENV COPY_REFERENCE_FILE_LOG $JENKINS_HOME/copy_reference_file.log

# Install other tools
# ===================
# Install maven
RUN apt-get update -y && apt-get install maven -y

# Install docker
RUN apt-get update
RUN apt-get install -y \
     apt-transport-https \
     ca-certificates \
     curl \
     gnupg2 \
     software-properties-common

RUN curl -fsSL https://download.docker.com/linux/$(. /etc/os-release; echo "$ID")/gpg | apt-key add - 
RUN add-apt-repository -y \
   "deb [arch=amd64] https://download.docker.com/linux/$(. /etc/os-release; echo "$ID") \
   $(lsb_release -cs) \
   stable"
RUN apt-get update
RUN apt-get install docker-ce -y

# Install docker-compose
RUN curl -L https://github.com/docker/compose/releases/download/1.21.0/docker-compose-`uname -s`-`uname -m` > /usr/local/bin/docker-compose
RUN chmod +x /usr/local/bin/docker-compose

#Install kubectl
RUN curl -LO https://storage.googleapis.com/kubernetes-release/release/$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)/bin/linux/amd64/kubectl
RUN chmod +x ./kubectl
RUN sudo mv ./kubectl /usr/local/bin/kubectl


# Add the Jenkins user to the docker group
#RUN usermod -aG docker ${user}

# Set some configurations
# Add the main config file to the jenkins path  
COPY config/jenkins_home_config.xml "$JENKINS_HOME"/config.xml

# Add files to set the maven configuration
COPY config/hudson.tasks.Maven.xml "$JENKINS_HOME"/hudson.tasks.Maven.xml
COPY config/jenkins.mvn.GlobalMavenConfig.xml "$JENKINS_HOME"/jenkins.mvn.GlobalMavenConfig.xml

# Copy scripts to execute upon start up
COPY config/init.groovy.d/ /usr/share/jenkins/ref/init.groovy.d

COPY jenkins-support /usr/local/bin/jenkins-support
COPY jenkins.sh /usr/local/bin/jenkins.sh
# from a derived Dockerfile, can use `RUN plugins.sh active.txt` to setup /usr/share/jenkins/ref/plugins from a support bundle
COPY plugins.sh /usr/local/bin/plugins.sh
COPY install-plugins.sh /usr/local/bin/install-plugins.sh

# Install necessary plugins
ARG plugins_list=plugins.txt
COPY $plugins_list /usr/share/jenkins/ref/plugins.txt
RUN /usr/local/bin/install-plugins.sh < /usr/share/jenkins/ref/plugins.txt
COPY tini-shim.sh /bin/tini
ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/jenkins.sh"]
