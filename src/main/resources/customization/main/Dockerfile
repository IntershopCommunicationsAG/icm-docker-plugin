ARG BASE_IMAGE

FROM ubuntu:20.04 as BUILD

RUN mkdir -p /intershop-prj

# extract the content of mainpkg.tgz to /intershop-prj
# ATTENTION: can not be replaced by a build argument because that lead to just copiing the archive instead of extracting
ADD mainpkg.tgz /intershop-prj

RUN mv /intershop-prj/system-conf /intershop-prj/config

FROM $BASE_IMAGE
ARG NAME

COPY --from=BUILD --chown=intershop:intershop /intershop-prj/ /intershop/
USER intershop

# assign customization's name to env-variable CUSTOMIZATION_NAME (used by icm-as-customization-base)
ENV CUSTOMIZATION_NAME=$NAME

# finally let $BASE_IMAGE do it's work
