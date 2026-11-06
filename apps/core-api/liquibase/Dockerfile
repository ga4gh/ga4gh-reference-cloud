FROM liquibase/liquibase:4.33.0

WORKDIR /liquibase

COPY dbchangelog.xml .

# the base image's non-root user (liquibase), by numeric id so kubernetes can verify the user is non-root
USER 1001

CMD ["liquibase", "--changeLogFile=dbchangelog.xml", "update"]
