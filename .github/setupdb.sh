#!/usr/bin/env sh

psql postgresql://test:test@localhost:5432/testpgdb -c "create table post (username text, \"text\" text);"