# minefort antibot

small spigot plugin that keeps the public bot account list banned on your server.

works on 1.8.8 through current paper/spigot builds. it was compiled against the old 1.8 api on purpose so it doesnt depend on new server stuff.

## install

1. download the jar from releases
2. put it in `plugins/`
3. restart the server

it checks `databasev2.txt` every 5 minutes. entries are UUIDs with an optional last-known name, so renamed accounts stay covered. `database.txt` is only a readable legacy mirror.

LiteBans and AdvancedBan are detected and use a silent `/ban -s uuid Bot Account` command. other plugins use normal `/ban uuid Bot Account`. Cracked `+Name` entries use the username instead of an offline UUID.

config is in `plugins/MinefortAntiBot/config.yml`.

## bStats

bStats support is built into the jar with plugin id `33379`.

the bStats library is shaded and relocated, so servers do not need another plugin or jar.

commands:

- `/mab status`
- `/mab reload`

the database is public here: [databasev2.txt](databasev2.txt)

