FROM busybox:latest as BUILD
ARG USERNAME=intershop
ARG USERID=150

RUN addgroup --gid 150 intershop && adduser -u ${USERID} -G intershop -D ${USERNAME} -s /bin/sh
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
    mkdir -p /intershop-prj/license && \
    mkdir -p /intershop-prj/logs && \
    mkdir -p /intershop-prj/clusterid && \
    mkdir -p /intershop-prj/testrunner/output && \
    mkdir -p /intershop-prj/ishunitrunner/output && \
    chmod ug+x -R /intershop-prj/bin && \
    chown -R ${USERNAME}:intershop /intershop-prj

FROM busybox:latest
ARG USERNAME=intershop
ARG USERID=150

RUN addgroup --gid 150 intershop && adduser -u ${USERID} -G intershop -D ${USERNAME} -s /bin/sh

COPY --from=BUILD --chown=${USERNAME}:intershop /intershop-prj/ /intershop/

USER ${USERNAME}

VOLUME /intershop/jgroups-share
VOLUME /intershop/temp-share
VOLUME /intershop/conf
VOLUME /intershop/sites
VOLUME /intershop/logs
VOLUME /intershop/project/cartridges
VOLUME /intershop/project/extraCartridges
VOLUME /intershop/project/libs
VOLUME /intershop/license
VOLUME /intershop/clusterid

VOLUME /intershop/testrunner/output
VOLUME /intershop/ishunitrunner/output

WORKDIR /intershop

# add command
ENTRYPOINT [ "/intershop/bin/intershop.sh" ]

CMD appserver