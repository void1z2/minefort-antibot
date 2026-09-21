# minefort antibot v1.1.0

blocks known Minefort raid accounts before they can enter your server.

the plugin downloads the public username database every minute and keeps a local copy if GitHub is temporarily unavailable. normal Java names, `+` offline names and `.` Bedrock names are supported.

## install

1. download the jar from releases
2. put it in `plugins/`
3. restart the server

no punishment plugin or operator account is needed.

## raid alerts

when known bot accounts hit the server together, online staff receive one compact raid alert. hover over it to see the usernames.

the plugin can also report the incident to Kixae using the signed V3 service at `https://minef.art`.

Kixae must be on the server first. Run `.linkverify SERVER` from Kixae's terminal while it is in that server. Kixae runs the one-time link command itself; no signing key exists until this succeeds.

This stops a plugin on one server from claiming it is installed on a different server. The plugin connects to `minef.art` and saves its signing key in `v3-link.properties`. that file is private and should not be shared.

## commands

`/mab status`

`/mab reload`

## other stuff

bStats is included using plugin id `33379`.

the plugin checks GitHub releases for updates.

the public username database is [database.txt](database.txt).

