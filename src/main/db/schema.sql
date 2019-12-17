/*
    To load this file in sqlite, use the following:
    samik@samik-lap:~/db$ sqlite3 -init ~/git/tucana/src/main/db/schema.sql tucana.db
*/
create table users
(
    userId varchar(200) not null primary key,   /* NTId */
	devKey varchar(200) not null unique,
    registerDate DATETIME not null,
    isAdmin boolean
);

/* TBD: Use userId or devKey below? */
create table tucanaprojects
(
    clientId varchar(200) not null,
    version varchar(100) not null,
    userId varchar(200) not null references users (userId),
    projectId varchar(200) not null,
    description varchar(250),
    config text,
    primary key (clientId, version)
);

create table tucanamodels
(
    modelId varchar(200) not null,
    version varchar(100) not null,
    userId varchar(200) not null references users (userId),
    description varchar(250),
    lastUpdateTimestamp text,
    model longblob,
    dataSchema varchar(20000),
    primary key (modelId, version)
);

