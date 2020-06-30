![CI](https://github.com/OlivierMary/Yuumi/workflows/CI/badge.svg)
![RELEASE](https://github.com/OlivierMary/Yuumi/workflows/RELEASE/badge.svg)

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
 - Clean Generated notification in Lcu
 - Change App name for windows Task Manager it's the jdk name ==> `-Dname` not working
 - Make a screen with all champ icons

## Refactor
 - Not dependent of com.github.stirante:lol-client-java-api
 
