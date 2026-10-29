-- Creates the restricted, read-only database role used by the Data Connect query engine
-- to execute researcher-submitted SQL (/search, /table/{table_name}/data).
--
-- Must be run by a database admin (superuser or CREATEROLE) BEFORE the Liquibase
-- changelog is applied: the refcloudapi role cannot create roles, and the Liquibase
-- changelog grants SELECT on specific tables to this role.
--
-- Safe to re-run: creates the role if missing, then (re)sets its password and settings,
-- so it can also be used to rotate the password.
--
-- The password is read from the DATA_CONNECT_READER_PASSWORD environment variable
-- (e.g. populated from a Kubernetes Secret) and falls back to 'secret' for local
-- development only. Reading it from the environment keeps it out of the process list.
--
-- Usage:
--   local dev:  psql postgres -f setup/postgres/create-data-connect-reader-role.sql
--   prod:       DATA_CONNECT_READER_PASSWORD=... psql <admin connection> -f setup/postgres/create-data-connect-reader-role.sql
--
-- The app reads the same password from GA4GH_REFCLOUD_DATACONNECT_DATASOURCE_PASSWORD,
-- so both should be populated from the same secret.

\set ON_ERROR_STOP on

\getenv password DATA_CONNECT_READER_PASSWORD
\if :{?password}
\else
    \echo 'DATA_CONNECT_READER_PASSWORD not set, using local development password'
    \set password secret
\endif

SELECT 'CREATE ROLE data_connect_reader LOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'data_connect_reader')
\gexec

ALTER ROLE data_connect_reader WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'password';

-- database-enforced limits that apply even if the app does not set them per transaction
ALTER ROLE data_connect_reader SET default_transaction_read_only = on;
ALTER ROLE data_connect_reader SET statement_timeout = '30s';
