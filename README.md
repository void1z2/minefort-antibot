# minefort antibot v1.0.2

small spigot plugin that keeps known bot accounts banned on your server.

works on 1.8.8 through current paper/spigot builds. it was compiled against the old 1.8 api on purpose so it doesnt depend on new server stuff.

## install

1. download the jar from releases
2. put it in `plugins/`
3. restart the server

it checks the public bot list every 5 minutes and bans new accounts automatically. UUIDs are used first, while `+` and `.` names use their username.

LiteBans and AdvancedBan get the silent ban option. the plugin also checks GitHub for updates.

config is in `plugins/MinefortAntiBot/config.yml`.

## bStats

bStats support is built into the jar with plugin id `33379`.

the bStats library is shaded and relocated, so servers do not need another plugin or jar.

commands:

- `/mab status`
- `/mab reload`

the database is public here: [databasev2.txt](databasev2.txt)

