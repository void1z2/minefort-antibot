# minefort antibot

small spigot plugin that keeps the public bot account list banned on your server.

works on 1.8.8 through current paper/spigot builds. it was compiled against the old 1.8 api on purpose so it doesnt depend on new server stuff.

## install

1. download the jar from releases
2. put it in `plugins/`
3. restart the server

it checks `database.txt` every 5 minutes. first startup bans the full list, later checks only ban new names.

LiteBans and AdvancedBan are detected and use `/ban -s name Bot Account`. other plugins use normal `/ban name Bot Account`.

config is in `plugins/MinefortAntiBot/config.yml`.

commands:

- `/mab status`
- `/mab reload`

the database is public here: [database.txt](database.txt)

