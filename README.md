# turku-amk-api



#### To Install:

SQL:
- postgresql
- PostGis
- dbeaver (optional on Linux/Mac)

IDE:
- intellij (community and ultimate both work)

API:
- any (worked with postman during the development)


#### Requirements:

- Add google credentials in the `resources/credentials.json` file.

- Compare the variables in the script of your choice in the folder environment, with the env variables in .idea > runConfigurations > local.xml

- Create DB, named `turku-amk` (follow the steps in the next category for help).

- Run migrations by launching the app once.



#### SQL Scripts for DB setup:

# enter sql
`sudo -iu postgres`

# when inside postgres initialize postgres
`initdb --locale=C.UTF-8 --encoding=UTF8 -D /var/lib/postgres/data --data-checksums`

# enable & start postgres
- for Linux (exit postgresql first):
`systemctl enable postgresql.service`
`systemctl start postgresql.service`

- for Mac with Brew:
`brew services start postgresql`

# create the user that will use the database and give him a password
`createuser --interactive`

then connect to the postgres database
`ALTER USER [USERNAME] WITH PASSWORD '[NEW PASSWORD]';`

# create the database
`createdb [DBNAME] -O [USERNAME] -U postgres`

# access the db
`psql -d [DBNAME]`

# add postgis extension
`CREATE EXTENSION postgis;`

# RUN THE MIGRATIONS IN THE APP BEFORE PROCEEDING

- Add services table index for geom_point using `CREATE INDEX service_location_index ON public.services USING gist(geom_point);`.
- Add trigger to update geom_point column in services table using:
```
CREATE OR REPLACE FUNCTION public.update_geom_point_column()
RETURNS TRIGGER AS $BODY$
begin
    UPDATE services
    SET geom_point = ST_GeomFromText(CONCAT('POINT(', new.longitude, ' ', new.latitude, ')'),4326)
    WHERE id = new.id;
    RETURN new;
END
$BODY$ language 'plpgsql';

-- DROP TRIGGER IF EXISTS update_geom_point_column_update on public.services;
-- DROP TRIGGER IF EXISTS update_geom_point_column_insert on public.services;

create trigger update_geom_point_column_update
after update
on services
for each row
when (new.latitude is distinct from old.latitude or new.longitude is distinct from old.longitude)
execute procedure public.update_geom_point_column();

create trigger update_geom_point_column_insert
after insert
on services
for each row
execute procedure public.update_geom_point_column();
```
