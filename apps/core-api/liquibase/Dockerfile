FROM liquibase/liquibase:4.33.0

WORKDIR /liquibase

COPY dbchangelog.xml .

# the base image's non-root user (uid 1001)
USER liquibase

CMD ["liquibase", "--changeLogFile=dbchangelog.xml", "update"]
