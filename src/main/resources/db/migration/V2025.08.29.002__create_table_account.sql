CREATE TABLE account
(
    id varchar(36) NOT NULL,
    name varchar(255) NOT NULL,
    email varchar(255) NOT NULL,
    sha256 varchar(64) NOT NULL,
    CONSTRAINT account_pkey PRIMARY KEY (id)
)
