#!/usr/bin/env sh

psql postgresql://test:test@localhost:5432/testpgdb -c "create table post (username text, \"text\" text);"
psql postgresql://test:test@localhost:5432/testpgdb -c "create table postbird (username text, \"text\" text);"
psql postgresql://test:test@localhost:5432/testpgdb -c "create table postzio (username text, \"text\" text);"
