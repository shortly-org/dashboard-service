-- Local compose and integration tests only: each password equals the role name.
CREATE ROLE dashboard_owner LOGIN PASSWORD 'dashboard_owner';
ALTER DATABASE :"DBNAME" OWNER TO dashboard_owner;

CREATE ROLE dashboard_app LOGIN PASSWORD 'dashboard_app';

CREATE ROLE link_reader NOLOGIN;
