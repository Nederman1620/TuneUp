# TuneUp
Minecraft paper plugin that optimises performance a bit

Lichte server-optimizer voor Paper 26.3, met extra kans op de belangrijkste enchanted books. Geen backdoors, geen netwerkcode, geen afhankelijkheden buiten de Paper API.

## Bouwen

Vereist: Java 25 en Gradle 9.x (of IntelliJ, dat regelt Gradle zelf).

```
gradle build
```

De jar staat daarna in `build/libs/TuneUp-1.0.0.jar`. Zet hem in de `plugins/` map van je Paper 26.3 server en start de server opnieuw.

## Commando's

- `/tuneup status`: TPS, MSPT, afstanden per wereld en statistieken
- `/tuneup books on|off|toggle`: de book boost aan of uit zetten zonder herstart (wordt bewaard in `config.yml`)
- `/tuneup reload`: config opnieuw laden (permissie: `tuneup.admin`)

## Wat doet het

- **Entity limiter**: begrenst te drukke chunks (mobs per type, gedropte items). Genoemde, vastgebonden, getemde en bestuurde mobs blijven altijd staan, net als waardevolle items.
- **Dynamic distance**: verlaagt de simulation distance als de TPS zakt en zet hem terug als alles weer soepel loopt.
- **Book boost**: grotere kans op mending, unbreaking, fortune, efficiency, protection en meer via normale spelbronnen. Alles is instelbaar in `config.yml`.

## Let op

Deze code is geschreven zonder tegen de echte Paper 26.3 API te kunnen compileren. Als `gradle build` een foutmelding geeft, stuur die door.
