ARG PYCHARM_VERSION=2019.3.2
FROM ubuntu:18.04
ARG PYCHARM_VERSION
RUN echo "Building PyCharm $PYCHARM_VERSION with python-security"

COPY . /sources/plugin
RUN rm -rf /sources/plugin/build

WORKDIR /sources

# Install dependencies
RUN apt-get -y update && apt-get -y install wget unzip

# Install PyCharm
RUN wget https://download.jetbrains.com/python/pycharm-community-${PYCHARM_VERSION}.tar.gz && tar xzf pycharm-community-${PYCHARM_VERSION}.tar.gz -C /opt/
RUN mv /opt/pycharm-community-${PYCHARM_VERSION} /opt/pycharm-community

# Compile plugin
RUN apt-get -y install openjdk-11-jre-headless

WORKDIR /sources/plugin

# Test and compile plugin
RUN ./gradlew test --no-daemon -PintellijPublishToken=FAKE_TOKEN
RUN ./gradlew buildPlugin --no-daemon -PintellijPublishToken=FAKE_TOKEN

# Install built plugin
RUN unzip build/distributions/pycharm-security-*.zip -d /opt/pycharm-community/plugins

# Install default inspection profile
RUN wget https://github.com/tonybaloney/pycharm-security/raw/master/doc/_static/SecurityInspectionProfile.xml -O /sources/SecurityInspectionProfile.xml

# Tidy up
RUN rm -f /sources/pycharm-community-${PYCHARM_VERSION}.tar.gz
RUN rm -rf /sources/plugin

# Configure entrypoint
ENTRYPOINT /opt/pycharm-community/bin/inspect.sh /code /sources/SecurityInspectionProfile.xml out.log -format plain -v0 2> /dev/null && cat out.log