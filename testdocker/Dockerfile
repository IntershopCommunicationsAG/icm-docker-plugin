ARG USERNAME=intershop
ARG USERID=150

FROM ubuntu:20.10 AS BUILD
ARG USERNAME=intershop
ARG USERID=150

RUN addgroup --gid 150 intershop && useradd -m --uid ${USERID} -g intershop --shell /bin/sh ${USERNAME}
RUN mkdir -p /intershop-prj

COPY bin /intershop-prj/bin

RUN mkdir -p /intershop-prj/jgroups-share && \
    mkdir -p /intershop-prj/temp-share/servletengine/pagecompile && \
    mkdir -p /intershop-prj/temp-share/dist && \
    mkdir -p /intershop-prj/temp-share/reportingrepository && \
    mkdir -p /intershop-prj/temp && \
    mkdir -p /intershop-prj/temp/fonts && \
    mkdir -p /intershop-prj/conf && \
    mkdir -p /intershop-prj/sites && \
    mkdir -p /intershop-prj/project/cartridges && \
    mkdir -p /intershop-prj/project/extraCartridges && \
    mkdir -p /intershop-prj/project/libs && \
    mkdir -p /intershop-prj/project/extraCartridgesLibs && \
    mkdir -p /intershop-prj/logs && \
    mkdir -p /intershop-prj/clusterid && \
    mkdir -p /intershop-prj/testrunner/output && \
    mkdir -p /intershop-prj/ishunitrunner/output && \
    mkdir -p /intershop-init && \
    chmod ug+x -R /intershop-prj/bin && \
    chown -R ${USERNAME}:intershop /intershop-prj && \
    chown -R ${USERNAME}:intershop /intershop-init

# FROM busybox:latest
FROM eclipse-temurin:11.0.14.1_1-jre
ARG USERNAME=intershop
ARG USERID=150

COPY usr /usr/bin/
RUN chmod a+x -R /usr/bin/dirdiff

RUN addgroup --gid 150 intershop && \
    useradd -m --uid ${USERID} -g intershop --shell /bin/sh ${USERNAME} && \
    useradd --uid 151 -g intershop --shell /bin/sh intershop

COPY --from=BUILD /intershop-prj/ /intershop/
COPY --from=BUILD /intershop-init/ /intershop-init/

RUN mkdir -p /intershop/intershop-prj/

RUN chown -R ${USERNAME}:intershop /intershop
RUN chown -R ${USERNAME}:intershop /intershop-init

USER ${USERNAME}

VOLUME /intershop/jgroups-share
VOLUME /intershop/temp-share
VOLUME /intershop/conf
VOLUME /intershop/sites
VOLUME /intershop/logs
VOLUME /intershop/project/cartridges
VOLUME /intershop/project/extraCartridges
VOLUME /intershop/project/libs
VOLUME /intershop/clusterid

VOLUME /intershop/testrunner/output
VOLUME /intershop/ishunitrunner/output

WORKDIR /intershop

# add command
ENTRYPOINT [ "/intershop/bin/intershop.sh" ]

CMD appserver