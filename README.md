![CI](https://github.com/OlivierMary/Yuumi/workflows/CI/badge.svg)

# Yuumi
League of legends Automatic Runes/Items/Spell setter

Compile
```sh
./gradlew build
```

Executable will be here `build\launch4j\yuumi.exe`

# Info 

## Source
All datas come from u.gg

## Items
Set all items set possible for any position / map

## Runes
If not enough available page only set calculated position rune

## Summoner Spells
Set summoner spell for calculated position

# TODO
## Improvement
 - Get Ugg LoL version and Ugg version dynamically
 - Make a persistent cache with 1-2d retention + refresh cache button
 - Champ menu with position to set ? => Can be use full if not enough pages and selected page is not good (fill,pick,switch)
 - Clean Generated notification in Lcu
 - Persist Notification settings
 - Toggle Lcu Notifications ?
 - Change App name for windows Task Manager it's the jdk name

## Refactor
 - DTO for yuumi tools and not from ugg + parse ugg -> new dto
 - Not dependent of com.github.stirante:lol-client-java-api

## To Fix
 - Find a way to have only one `validateChampion` if summoner spell before != need => another handler endpoint?

