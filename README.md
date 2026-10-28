# FChatColor

Plugin de Minecraft para **colores en el chat**, compatible de **1.8 a 1.21.x y la
numeración por años (26.x)**, con soporte de **MySQL/SQLite**, **hex colors**,
**MiniMessage**, **colores legacy** y **Folia/Canvas**.

## Características

| Requerimiento | Implementación |
|---|---|
| **MySQL / SQLite** | `JdbcColorStorage` con pool de conexiones propio, upsert por motor y caché en memoria por jugador |
| **Hex colors** | `&#RRGGBB` → `§x§R§R§G§G§B§B` en 1.16+; en 1.8/1.12/1.15 se degrada al color legacy más cercano |
| **MiniMessage** | Servidores modernos: parser nativo (`<red>`, `<#FF5555>`, `<gradient:…>`). Servidores 1.8: traducción parcial de etiquetas a códigos legacy |
| **Legacy colors** | `&a`, `&l`, `§a`, etc. en todos los servidores |
| **1.8 / 1.21.x / 26.x** | Un solo jar. Detección de versión por **reflection** + dispatch de módulos |
| **Folia / Canvas** | `folia-supported: true`, schedulers regionizados y listeners compatibles con multi-región |

## Arquitectura multi-módulo (Gradle Kotlin DSL)

```
FChatColor-Parent
├── fchatcolor-api      Interfaces + ServerDetector (reflection) + utilidades
├── fchatcolor-legacy   Servicio, scheduler y listener para servidores 1.8+ (Java 8)
├── fchatcolor-modern   Servicio, scheduler y listener para Paper 1.20.5+/1.21.x/26.x/Folia (Java 21)
└── fchatcolor-plugin   Plugin principal, comando, config, JDBC (MySQL/SQLite) → jar final (shadowJar)
```

**Clave de la compatibilidad:** `ServiceLocator` jamás referencia `LegacyServices`
ni `ModernServices` en el código; los carga con `Class.forName(...)`. Así, en un
servidor 1.8 **ni una sola clase de Paper 1.21 se carga**, evitando
`NoClassDefFoundError`. Todo el código está compilado a **bytecode Java 8**
(`--release 8`), también los drivers JDBC incluidos.

### Detección del servidor por reflection

`ServerDetector.detect()` (módulo `api`) sondea el runtime sin depender de APIs
compilables:

| Sonda | Qué descubre |
|---|---|
| `Bukkit.getBukkitVersion()` + regex | Versión de Minecraft (soporta `1.8.8`, `1.21.4`, `26.1`, `26.2.3`…) |
| `Bukkit.getName()` / `Bukkit.getVersion()` | Fork: Spigot, Paper, Purpur, Pufferfish, Folia, Canvas… |
| `io.papermc.paper.threadedregions.RegionizedServer` | ¿Es Folia o un fork (Canvas)? |
| `io.papermc.paper.threadedregions.scheduler.RegionScheduler` | ¿Hay schedulers regionizados disponibles? |
| `net.kyori.adventure.text.minimessage.MiniMessage` | ¿Hay MiniMessage? |
| `org.bukkit.ChatColor#of(String)` | ¿Hay colores hex legacy (1.16+)? |
| `io.papermc.paper.event.player.AsyncChatEvent` | ¿Usar el evento de chat moderno o `AsyncPlayerChatEvent`? |

Resultado accesible por API (`ServerInfo`): versión, plataforma y capacidades
reales detectadas.

## Compilación

Requisitos: **JDK 17** para ejecutar Gradle 8.14 (Gradle descarga toolchain **Java 21** automáticamente para `fchatcolor-modern`).

```bash
# JDK 17 requerido para Gradle 8.14 (JDK 25 no es parseado por Kotlin 1.9)
./gradlew clean build
# o en Windows
gradlew.bat clean build
```

El jar final queda en `fchatcolor-plugin/build/libs/FChatColor-1.0.0.jar`.

> **Compatibilidad 1.8 → 26.x garantizada:** `fchatcolor-api`/`legacy`/`plugin` compilan con `--release 8` (bytecode Java 8) y `fchatcolor-modern` con `--release 21` para `paper-api:1.21.4`. `ServiceLocator` carga `LegacyServices`/`ModernServices` vía `Class.forName` — en 1.8 nunca se toca una clase de Paper 1.21.

## Instalación

1. Copia `FChatColor-1.0.0.jar` en `plugins/`.
2. Arranca el servidor: se crea `plugins/FChatColor/config.yml` y la base de datos.
3. Para MySQL, edita `database.type: MYSQL` y la sección `mysql`.

## Comandos

| Comando | Permiso | Descripción |
|---|---|---|
| `/fchatcolor` | `fchatcolor.command.color` | **Abre el menú de colores** (paginado, con clics) |
| `/fchatcolor set <color>` | `fchatcolor.command.color` + permiso del color (ver abajo) | Elige tu color de chat (se guarda en la BD) |
| `/fchatcolor clear` | `fchatcolor.command.color` | Elimina tu color |
| `/fchatcolor set <player> <color>` | `fchatcolor.command.color` (consola) | Fija el color de otro jugador desde la consola |
| `/fchatcolor clear <player>` | `fchatcolor.command.color` (consola) | Borra el color de otro jugador desde la consola |
| `/fchatcolor reload` | `fchatcolor.admin` | Recarga config, mensajes, colores, **menú** y servicios |

Alias: `/fcolor`, `/fc`, `/chatcolor`. Subcomando explícito del menú:
`/fchatcolor menu` (o `/chatcolor gui`).

Acepta las claves de `colors.yml` (cada una con su permiso), también sus
nombres para mostrar (`White`, `<white>White`), `#FF5555` (requiere
`fchatcolor.chat.hex`) y códigos legacy (`&9`, `9`).

### Colores y permisos por color (`colors.yml`)

Los colores con nombre viven en `colors.yml`; **cada color tiene su permiso**:

```yaml
colors:
  white:
    name: '<white>White'
    permission: fchatcolor.color.white
    value: '#FFFFFF'
  red:
    name: '<red>Red'
    permission: fchatcolor.color.red
    value: '#FF5555'
  rainbow:
    name: '<rainbow>Rainbow'
    permission: fchatcolor.color.rainbow
    value: rainbow
# ... 17 colores por defecto (16 + rainbow)
```

- **Sin límite de colores**: los de abajo son solo los que trae el plugin.
  Añade todas las entradas que quieras bajo `colors` (clave, `name`,
  `permission` y `value` tuyos) y `/fchatcolor reload` las carga.
- `name` es el **nombre para mostrar** del color: puede llevar etiquetas
  MiniMessage (`<white>`) o códigos `&a`. Lo devuelve
  `%fchatcolor_color_name%` ya traducido al formato del servidor
  (`<white>White` → `§fWhite`) y también sirve para elegir el color:
  `/fchatcolor set White` y `/fchatcolor set <white>White` funcionan igual
  que `/fchatcolor set white`. Si falta, se usa la clave.
- Los permisos se registran con valor por defecto `op` (los admins pueden usar
  todos los colores); concede nodos como `fchatcolor.color.red` a los jugadores.
- `fchatcolor.color.*` otorga todos los colores.
- `value` admite **`#RRGGBB`** (`#FF5555`, `&#FF5555`) o **código legacy**
  con o sin ampersand: `&c`, `§c`, `c`. También el valor especial
  **`rainbow`**: el mensaje se pinta **letra a letra** con el espectro
  (rojo → naranja → amarillo → verde → azul → magenta ...) y la base del
  color sigue girando con el tiempo, así que el arcoíris recorre cada
  mensaje y nunca se repite. Los códigos que el propio jugador escribe se
  respetan. En servidores sin hex (o sin `fchatcolor.chat.hex`) se aproxima
  con los colores legacy del espectro (`§c§6§e§a§b§d`), que también avanza
  letra a letra.
- Añade/quita colores editando el archivo; `/fchatcolor reload` lo recarga.
- **El archivo viaja siempre dentro del jar** (`colors.yml`, en la raíz) y se
  copia a `plugins/FChatColor/` en el primer arranque; si lo borras, se vuelve
  a crear en el siguiente arranque o `/fchatcolor reload` (tus ediciones solo
  se pisan si borras el archivo).
- El tab-complete solo sugiere los colores que el jugador puede usar.

## Menú `/chatcolor`

Al ejecutar **`/chatcolor`** (o `/chatcolor menu`, `/fc menu`) se abre un
**menú de inventario paginado** con todos los colores del catálogo. Un clic
selecciona el color; volver a clicar tu color actual lo **elimina**. Los
botones de flechas cambian de página y la cama cierra el menú.

La disposición se controla en **`menu.yml`** (se crea automáticamente, viaja
dentro del jar y se recarga con `/fchatcolor reload`):

```yaml
title: "<dark_gray>ChatColor (Page. %page%/%maxpage%)"
size: 54                     # múltiplo de 9, máx 54
colors-slots: 0-8;9-17;18-26;27-35;36-44   # huecos para colores (rangos con ';')

fill:                          # fondo opcional (deshabilitado por defecto)
  enabled: false
  material: GRAY_STAINED_GLASS_PANE
  name: " "

item-format:                   # cómo se ve cada color ({color_name}, {permission})
  select:        {name: "{color_name}", material: "{color_material}", lore: ['&aClick to select!']}
  unselect:     {name: "{color_name}", material: "{color_material}", lore: ['&cClick to remove your chat color!']}
  selected:      {name: "{color_name}", material: "{color_material}", lore: ['&cYou have already selected this color!']}
  no_permission: {name: "{color_name}", material: "{color_material}", lore: ['&cYou need the {permission} permission to select the color!']}

buttons:
  close:          {enabled: true, material: RED_BED,   name: "&cClose", slot: 49, action: "close"}
  previous-page:  {enabled: true, material: ARROW,    name: "&aBack",  slot: 45, action: "prev"}
  next-page:      {enabled: true, material: ARROW,    name: "&aNext",  slot: 53, action: "next"}
```

- `title` admite `%page%`/`%maxpage%` y etiquetas MiniMessage o códigos `&`.
- `colors-slots` acepta rangos (`0-8;9-17`) y sueltos (`45`). También se
  tolera la errata antigua `clors-slots`.
- **Iconos con cabezas**: cada color de `colors.yml` acepta `material` y
  `texture` (ver abajo). `{color_material}` los usa automáticamente.
- El color **sin permiso** se muestra atenuado con su lore de "no permission"
  y su clic no hace nada; tu **color actual** se puede quitar con un clic.

### Cabezas personalizadas (`material` y `texture`)

Cada entrada de `colors.yml` puede llevar dos campos extra para su icono del
menú:

```yaml
  red:
    name: '<red>Red'
    permission: fchatcolor.color.red
    value: '#FF5555'
    material: "PLAYER_HEAD"    # cualquier material de Bukkit (por defecto PLAYER_HEAD)
    texture: ""                # skin de la cabeza; vacío = cabeza normal
```

`texture` acepta **tres formatos** y se aplica por reflection (funciona de 1.8
a 26.x sin romper el menú si el servidor no lo soporta):

| Formato | Ejemplo |
|---|---|
| **base64** (el habitual de las webs de skins) | `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6...` |
| **hash** de 64 caracteres | `f9e6b60a2f...` (64 hex) |
| **URL** completa de textura | `http://textures.minecraft.net/texture/<hash>` |

`material` permite iconos que no sean cabeza (p. ej. `REDSTONE` para rojo,
`LAPIS_LAZULI` para azul); `{color_material}` los respeta en `item-format`.

## Permisos

| Permiso | Por defecto |
|---|---|
| `fchatcolor.chat.use` | `true` |
| `fchatcolor.chat.hex` | `op` |
| `fchatcolor.chat.minimessage` | `op` |
| `fchatcolor.command.color` | `true` |
| `fchatcolor.admin` | `op` |
| `fchatcolor.color.<color>` (uno por color de `colors.yml`) | `op` |
| `fchatcolor.color.*` | `op` |
| `fchatcolor.*` | `op` |

El modo de color que aplica a cada jugador se resuelve así
(`ColorSelector`): MiniMessage → Hex → legacy, en función de permisos,
modos habilitados y capacidades del servidor (el modo por defecto está
fijado en código: `ChatDefaults.DEFAULT_MODE`).

## Configuración (`config.yml`)

El comportamiento del chat es **fijo en el código** (no configurable): carácter
`&`, traducción de códigos, aplicar el color elegido a los mensajes y modo
por defecto (`ChatDefaults`), para que sea idéntico en todos los servidores.

```yaml
chat:
  modes: { legacy: true, hex: true, minimessage: true }

database:
  type: SQLITE              # SQLITE | MYSQL
  cache-millis: 30000
  pool-size: 4
  table: fchatcolor_colors
  sqlite: { file: data.db }
  mysql:
    host: localhost
    port: 3306
    database: fchatcolor
    username: root
    password: ''
```

## Mensajes (`messages.yml`)

All plugin messages (prefix, errors, `/fchatcolor set`…) are edited in
`messages.yml`, with `&a` codes and `{...}` placeholders. **Every message can
contain `{prefix}`**: delete it from any message to remove the plugin prefix
from that message only.

Two formats are accepted for each message: a **single line** or a **list of
lines** (each entry is sent as its own line).

```yaml
prefix: '&8[&bFChatColor&8] &r'

# single line
color-set: '{prefix}&aChat color updated: {color}█████&r &7({value})'
# no prefix on this one:
color-cleared: '&aYour chat color has been removed.'

# multiple lines (list format)
usage:
  - '{prefix}&e/fchatcolor set <color> &7- choose your chat color'
  - '&e/fchatcolor clear &7- remove your color'
  - '&e/fchatcolor reload &7- reload the configuration'
```

A `|-` YAML block also works (it is just a single string with real newlines).
`/fchatcolor reload` reloads `config.yml`, `messages.yml` and `colors.yml`
without restarting.

## Placeholders (PlaceholderAPI)

Con [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
instalado, FChatColor expone estos placeholders (también útiles en el formato
de cualquier plugin de chat):

| Placeholder | Devuelve |
|---|---|
| `%fchatcolor_color%` | Códigos `§` del color elegido: **pinta lo que venga detrás** (`%fchatcolor_color%Hola` → "Hola" en tu color) |
| `%fchatcolor_color_legacy%` | Siempre un código legacy de un carácter (máxima compatibilidad) |
| `%fchatcolor_color_hex%` | Valor crudo guardado (`#RRGGBB` o letra) |
| `%fchatcolor_color_name%` | Campo `name` del color en `colors.yml`, **ya traducido** (`<white>White` → `§fWhite`); vacío si no hay color o no coincide con ninguna entrada |
| `%fchatcolor_color_preview%` | Códigos + `█████` coloreados + reset |
| `%fchatcolor_has_color%` | `true` / `false` |

Respeta los permisos del chat: sin `fchatcolor.chat.use` devuelve vacío y sin
`fchatcolor.chat.hex` degrada al color legacy más cercano. Si no hay
PlaceholderAPI instalado, el plugin sigue funcionando igual (solo no habrá
placeholders).

Ejemplo en un formato de chat: `%fchatcolor_color%%player_name%: %message%`
(el `:` y el nombre quedan en el color del jugador) o directamente
`%player_name%: %fchatcolor_color%%message%` para pintar solo el mensaje.

## API para otros plugins

```java
FChatColorAPI api = FChatColorAPI.get();           // null si FChatColor no está cargado
String chat = api.getColorService().translate("&aHola &#FF55FFmundo", ColorMode.HEX);
ServerInfo info = api.getServerInfo();             // versión + plataforma detectadas
String colorJugador = api.getStorage().getSelectedColor(uuid);
```

## Matriz de compatibilidad

| Servidor | Módulo cargado | Hex | MiniMessage | Scheduler | Evento de chat |
|---|---|---|---|---|---|
| Spigot/CraftBukkit 1.8–1.15 | legacy | al color más cercano | parcial | Bukkit | `AsyncPlayerChatEvent` |
| Spigot/Paper 1.16–1.19 | legacy / modern | nativo | nativo en Paper | Bukkit | ambos |
| Paper 1.20.5–1.21.x | modern | nativo | nativo | Bukkit/Folia | `AsyncChatEvent` |
| Paper/Spigot "26.x" | modern (fallback legacy) | nativo | nativo | región/global | `AsyncChatEvent` |
| Folia / Canvas | modern | nativo | nativo | **region schedulers** | `AsyncChatEvent` |

Todos los accesos a APIs condicionales están envueltos en `try/catch`, de modo
que una API que aparezca o desaparezca en una futura versión degrada al
máximo sin tumbar el plugin.

## Licencia

Proyecto propio. Los drivers JDBC embebidos mantienen sus licencias
originales (SQLite JDBC: Apache 2.0, MySQL Connector/J: GPL v2 con
exception de linking).
