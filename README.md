# Judah's Throwing Discs - Paper plugin

A Paper port of the Fabric mod. Players need **no client mod**.

## Build
Needs Java 21 and Maven.

    mvn package

The plugin jar is `target/JudahsThrowingDiscs-Paper-1.2.0.jar`. Put it in your server's `plugins/` folder.
Target: Paper 1.21.11 (change the `paper-api` version in `pom.xml` for another 1.21.x).

## What it does
- Right-click with a disc to throw it. Off-hand works too; sneak with discs in both hands to throw the off-hand one.
- The disc flies straight, bounces off blocks (15% faster each bounce, up to 8 bounces), hits every
  living thing it passes through once per pass, then curves back to you and is caught into the slot it came from.
- If you log out or the server stops mid-flight, the disc is caught or dropped, never lost.
- Same tiers as the mod: copper 4, iron 5, gold 4, diamond 7, netherite 8 damage; durability 95/125/16/780/1015.
- Recipes: ingot ring (`_M_ / M_M / _M_`) for copper/iron/gold/diamond; netherite = diamond disc + netherite ingot
  + upgrade template at a smithing table.
- Hitting a player who is blocking with a shield disables it for 5 seconds, like an axe (`shield-disable-ticks`).
- Armor-piercing hits by default (`armor-piercing: true`), which fixes discs doing little against armor.

## Commands
- `/throwingdisc give <player> <tier> [amount]` (alias `/disc`, permission `throwingdisc.give`)
- `/throwingdisc reload` (permission `throwingdisc.reload`)

## Textures (optional)
By default discs look like vanilla music discs. To get the mod's textures:
1. Host `JudahsThrowingDiscs-ResourcePack.zip` somewhere with a direct download link.
2. In `config.yml` set `custom-model: true` and fill in `resource-pack.url` and `resource-pack.sha1`.
3. `/throwingdisc reload`, then give yourself a new disc (existing discs keep their old look).

## Not carried over from the mod
- Enchanting-table/anvil enchants and ingot repair (the mod added the discs to a vanilla enchant tag; plugins can't edit that tag).
  Unbreaking is still honoured if a disc has it.
- Recipe unlock by advancement: recipes unlock on join instead.
- The mod's own projectile entity: the flying disc is an item display, so it looks the same but is not a real projectile.
- Sound effects are my picks; edit the `Sound.` lines in `DiscFlight.java` / `FlightManager.java` if you want others.

Original mod by JudahXP, MIT licensed (see LICENSE_throwing_disc).
