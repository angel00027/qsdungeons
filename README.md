# QSDungeons

Plugin de dungeons para **Paper 1.21.1+** (Java 21) con jefes, oleadas de mobs,
loot personalizado, cofres, bloques premiados, bossbars por proximidad,
placeholders e importación/exportación de configuraciones y estructuras.

Las integraciones con **WorldEdit**, **PlaceholderAPI** y **MythicMobs** son
opcionales: si no están instaladas, el plugin arranca y funciona igual (solo se
desactivan las funciones que dependen de ellas).

---

## Cambios recientes

### Actualización actual

- Añadido menú de **dungeons disponibles para jugadores**, con estado, jugadores
  conectados, capacidad, cooldown y entrada mediante clic.
- Los tiempos aceptan formatos legibles como `1s`, `1m`, `1h`, `1d`, `1mes` y
  combinaciones como `1m30s`.
- Los mensajes y el GUI muestran tiempos legibles en segundos, minutos, horas,
  días o meses.
- Los objetos de loot y equipamiento se guardan en **Base64**, conservando NBT,
  componentes, texturas, lore, nombres y encantamientos.
- El loot colocado desde el GUI se guarda automáticamente y aparece de inmediato
  en la lista.
- Los cofres se rellenan al iniciar la dungeon, no al abrirlos y cerrarlos.
- `respawn-seconds` y `refill-seconds` usan `0` por defecto.
- La bossbar actualiza su total cuando aparecen mobs por respawn.
- Se corrigieron listeners de inventario, botones del GUI y pérdidas de entradas
  de loot al recargar o reiniciar.

---

## Índice

1. [Características](#características)
2. [Requisitos e instalación](#requisitos-e-instalación)
3. [Estructura de archivos](#estructura-de-archivos)
4. [Inicio rápido](#inicio-rápido)
5. [Comandos](#comandos)
6. [Permisos](#permisos)
7. [Editor gráfico (GUI)](#editor-gráfico-gui)
8. [Esquema YAML](#esquema-yaml)
9. [Placeholders](#placeholders)
10. [MythicMobs](#mythicmobs)
11. [WorldEdit e import/export](#worldedit-e-importexport)
12. [Compilar](#compilar)

---

## Características

- **Una instancia activa por dungeon**, con entrada/salida, límite de jugadores,
  tiempo límite, cooldown, reintentos y reinicio al quedarse vacía.
- **Entrada automática** al pisar la región (configurable) y salida al salir de
  ella o al morir.
- **Jefes**: mobs vanilla personalizados (vida, daño, velocidad, nombre, equipo,
  drops, comandos al aparecer/morir) o jefes de **MythicMobs**.
- **Spawns** con cantidad de mobs, máximo vivo, tiempo de reaparición, oleadas
  repartidas en el tiempo (`spawn-interval-seconds`), búsqueda de suelo seguro y
  distancia mínima a los jugadores.
- **Loot** con probabilidad, peso, rango de cantidades, mensajes y comandos.
- **Cofres** que se rellenan al iniciar (y opcionalmente se reabastecen). Abrir
  y cerrar un cofre no genera loot nuevo. Su
  `BlockData` original se guarda y se restaura en cada partida, así que romper
  un cofre no impide reclamar las recompensas en la siguiente.
- **Bloques premiados** que entregan loot al romperse, se restauran solos y
  pueden ser de un solo uso o reaparecer tras un tiempo.
- **Editor gráfico completo en español** (`/dungeon gui`) para dungeons,
  ajustes, spawns, jefes, loot, cofres y bloques premiados, con todos los textos,
   materiales, slots y layouts configurables en `menus.yml`.
- **Menú de acceso para jugadores**: permite consultar dungeons activas,
  jugadores conectados, disponibilidad y cooldown, y entrar haciendo clic sin
  escribir un comando.
- **Bossbars solo para jugadores cercanos**, con radio configurable por dungeon
  y por jefe; cada jugador tiene su propia barra (Adventure).
- **Placeholders internos** (`{dungeon}`, `{kills}`, `{progress}`…) y expansión
  de **PlaceholderAPI** (`%qsdungeons_...%`).
- **Mensajes 100 % configurables** con MiniMessage en `messages.yml`.
- **Actualización automática de YAML**: al iniciar o recargar, las claves nuevas
  de `config.yml`, `messages.yml` y `menus.yml` se agregan sin sobrescribir tus
  valores personalizados.
- **Protección configurable de cofres** mediante `allow-chest-break`.
- **Cada dungeon en su propia carpeta**, con ID, jefes y loot separados.
- **Items completos en Base64**: loot y equipamiento conservan componentes,
  NBT, texturas, nombres, lore y encantamientos, con guardado automático.
- **Exportar/importar** configuración en `.zip` y estructuras con WorldEdit.

---

## Requisitos e instalación

| Componente | Versión |
|---|---|
| Servidor | Paper 1.21.1+ |
| Java | 21+ |
| WorldEdit (opcional) | 7.3+ |
| PlaceholderAPI (opcional) | 2.11+ |
| MythicMobs (opcional) | 5.6+ |

1. Coloca `QSDungeons-1.0.0.jar` en la carpeta `plugins/`.
2. Inicia el servidor una vez para generar `plugins/QSDungeons/`.
3. Edita `config.yml`, `messages.yml`, `menus.yml` y `player-menus.yml` a tu gusto.
4. Crea tu primera dungeon con los comandos de la sección
   [Inicio rápido](#inicio-rápido).
5. Usa `/dungeon reload` después de editar archivos a mano. Las claves nuevas
   de los YAML se agregan automáticamente durante la recarga.

El plugin no requiere dependencias duras. Si WorldEdit, MythicMobs o
PlaceholderAPI no están presentes, simplemente se omiten sus funciones.

---

## Estructura de archivos

```text
plugins/QSDungeons/
├── config.yml
├── messages.yml
├── menus.yml                 # textos, items y layouts del editor gráfico
├── player-menus.yml          # menú independiente para jugadores
├── data/
│   └── cooldowns.yml
├── exports/                  # .zip generados por /dungeon export
└── dungeons/
    └── <id>/
        ├── dungeon.yml       # región, entrada/salida, ajustes, spawns, cofres, premios
        ├── bosses/
        │   └── <bossId>.yml
        ├── loot/
        │   └── <lootId>.yml
        └── schematics/       # .schem / .schematic / .nbt de esta dungeon
```

El **ID** de una dungeon es el nombre de su carpeta y debe coincidir con
`[a-zA-Z0-9_-]{1,32}`. Todos los IDs se normalizan a minúsculas.

---

## Inicio rápido

```text
/dungeon create mi_dungeon
/dungeon pos1                 # mira al bloque de la primera esquina
/dungeon pos2                 # mira al bloque de la esquina opuesta
/dungeon region mi_dungeon
/dungeon entry mi_dungeon     # teletransporte de entrada en tu posición
/dungeon exit mi_dungeon      # teletransporte de salida (opcional)
/dungeon spawn add mi_dungeon oleada1 ZOMBIE 5
/dungeon loot create mi_dungeon comun
/dungeon loot add mi_dungeon comun 0.5 1-3   # item en la mano
/dungeon chest add mi_dungeon cofre1 comun   # mira al cofre
/dungeon boss create mi_dungeon jefe1 ZOMBIE
/dungeon start mi_dungeon
```

> La región se marca mirando a un bloque a menos de 10 bloques. Si no miras a
> ninguno, se usa el bloque bajo tus pies.

Los jugadores entran con `/dungeon join mi_dungeon` o automáticamente al pisar
la región (si `auto-enter` está activo).

---

## Comandos

Alias del comando: `/dungeons`, `/qsd`, `/qsdungeons`.

### Jugadores

| Comando | Descripción |
|---|---|
| `/dungeon help` | Muestra la ayuda. |
| `/dungeon list` | Lista las dungeons y su estado. |
| `/dungeon info [id]` | Información de una dungeon (o de la actual). |
| `/dungeon join <id>` | Entra a una dungeon. |
| `/dungeon leave` | Sale de la dungeon actual. |

### Administración (`qsdungeons.admin`)

| Comando | Descripción |
|---|---|
| `/dungeon create <id>` | Crea una dungeon y su carpeta. |
| `/dungeon delete <id> [confirm]` | Borra la dungeon (requiere `confirm`). |
| `/dungeon enable <id>` / `disable <id>` | Activa o desactiva una dungeon. |
| `/dungeon pos1` / `pos2` | Marca las esquinas de la región. |
| `/dungeon region <id>` | Guarda la región marcada. |
| `/dungeon entry <id>` | Guarda tu posición como entrada. |
| `/dungeon exit <id>` | Guarda tu posición como salida. |
| `/dungeon set <id> [ajuste] [valor]` | Ver o cambiar ajustes de la dungeon. |
| `/dungeon gui [id]` | Abre el editor gráfico (menú principal o editor de una dungeon). |
| `/dungeon start <id>` / `stop <id>` / `reset <id>` | Controla la instancia. |
| `/dungeon reload` | Recarga configuración, mensajes y dungeons. |

#### Spawns

| Comando | Descripción |
|---|---|
| `/dungeon spawn add <id> <spawnId> [entidad] [cantidad]` | Crea un spawn en tu posición. Admite `ZOMBIE` o `mythic:<id>`. |
| `/dungeon spawn remove <id> <spawnId>` | Elimina un spawn. |
| `/dungeon spawn list <id>` | Lista los spawns. |
| `/dungeon spawn set <id> <spawnId> <ajuste> <valor>` | Cambia un ajuste del spawn. |
| `/dungeon spawn test <id> <spawnId>` | Genera un mob de prueba en tu posición. |

Ejemplos de configuración rápida:

```text
/dungeon set mi_dungeon allow-chest-break true
/dungeon set mi_dungeon end-delay-seconds 30
/dungeon set mi_dungeon bosses-after-waves true
/dungeon spawn set mi_dungeon oleada1 burn-in-daylight false
/dungeon boss set mi_dungeon jefe1 burn-in-daylight false
```

Ajustes de spawn: `type`, `entity`, `mythic`, `amount`, `max-alive`,
`respawn-seconds`, `spawn-interval-seconds`, `radius`, `min-player-distance`,
`require-ground`, `health`, `damage`, `speed`, `follow-range`, `name`, `glowing`,
`baby`, `ai`, `persistent`, `clear-vanilla-drops`, `loot-table`,
`burn-in-daylight`, `location` y `equipment.<slot>`.

`burn-in-daylight: false` evita que los mobs del spawn ardan por el sol. Para
configurar equipo usa `head`, `chest`, `legs`, `feet`, `hand` u `off-hand`:

```yaml
equipment:
  head:
    material: DIAMOND_HELMET
  chest:
    material: DIAMOND_CHESTPLATE
  hand:
    material: IRON_SWORD
```

También puedes copiar el objeto que llevas en la mano al equipo configurado:

```text
/dungeon spawn set mi_dungeon oleada1 equipment.head hand
/dungeon spawn set mi_dungeon oleada1 equipment.hand hand
/dungeon boss set mi_dungeon jefe1 equipment.chest hand
/dungeon boss set mi_dungeon jefe1 equipment.hand clear
```

`hand` copia el objeto de la mano principal y `clear` elimina el objeto de esa
ranura. Las ranuras disponibles son `head`, `chest`, `legs`, `feet`, `hand` y
`off-hand`.

- `spawn-interval-seconds`: reparte la oleada inicial en el tiempo en lugar de
  aparecer todos de golpe.
- `min-player-distance`: evita que los mobs aparezcan encima de los jugadores.
- `require-ground`: exige suelo sólido bajo el punto de aparición.

Con `bosses-after-waves: true`, los jefes no aparecen al iniciar. Se activan
cuando ya no quedan mobs vivos ni spawns iniciales pendientes. Si el spawn usa
`respawn-seconds`, esos mobs pueden volver a aparecer después de desbloquear a
los jefes.

#### Jefes

| Comando | Descripción |
|---|---|
| `/dungeon boss create <id> <bossId> [entidad]` | Crea un jefe en tu posición. |
| `/dungeon boss remove <id> <bossId>` | Elimina un jefe. |
| `/dungeon boss list <id>` | Lista los jefes. |
| `/dungeon boss loc <id> <bossId>` | Mueve el jefe a tu posición. |
| `/dungeon boss set <id> <bossId> <ajuste> <valor>` | Cambia un ajuste del jefe. |
| `/dungeon boss test <id> <bossId>` | Genera un jefe de prueba en tu posición. |

Ajustes de jefe: `type`, `entity`, `mythic`, `health`, `damage`, `speed`,
`follow-range`, `name`, `display-name`, `glowing`, `baby`, `ai`, `persistent`,
`clear-vanilla-drops`, `announce-spawn`, `announce-death`, `loot-table`,
`burn-in-daylight`, `location`, `equipment.<slot>`, `bossbar.enabled`, `bossbar.title`, `bossbar.color`,
`bossbar.style`, `bossbar.radius`.

#### Loot

| Comando | Descripción |
|---|---|
| `/dungeon loot create <id> <lootId>` | Crea una tabla de loot. |
| `/dungeon loot add <id> <lootId> [prob] [cantidad] [peso]` | Añade el item de tu mano. |
| `/dungeon loot remove-entry <id> <lootId> <índice>` | Elimina una entrada (empieza en 1). |
| `/dungeon loot set <id> <lootId> <rolls\|unique> <valor>` | Cambia tiradas o unicidad. |
| `/dungeon loot info <id> <lootId>` | Muestra el resumen de la tabla. |
| `/dungeon loot list <id>` | Lista las tablas. |
| `/dungeon loot remove <id> <lootId>` | Elimina la tabla. |

`chance` acepta `0.5` (50 %) o `50` (también 50 %). Primero se comprueba la
probabilidad y, entre las entradas que la superan, `weight` decide la selección
proporcional: peso `2` tiene el doble de posibilidades que peso `1`. La cantidad
acepta `1`, `1-3` o `2..5`. `rolls: 1-3` hace entre una y tres tiradas;
`unique: true` evita repetir la misma entrada en esas tiradas.

#### Cofres y bloques premiados

Los bloques premiados no están limitados a cofres: puedes registrar un cofre,
un barril, una maceta, un dispensador o cualquier otro bloque que pueda
romperse. Debe registrarse con `reward add`; al romperlo durante la dungeon
entrega la tabla configurada y después se restaura según sus opciones.

| Comando | Descripción |
|---|---|
| `/dungeon chest add <id> <chestId> <lootId>` | Registra el cofre que estás mirando. |
| `/dungeon chest remove <id> <chestId>` | Elimina el cofre. |
| `/dungeon chest list <id>` | Lista los cofres. |
| `/dungeon chest set <id> <chestId> <loot-table\|refill-seconds\|enabled> <valor>` | Ajusta el cofre. |
| `/dungeon reward add <id> <rewardId> <lootId>` | Registra el bloque que estás mirando. |
| `/dungeon reward remove <id> <rewardId>` | Elimina el bloque premiado. |
| `/dungeon reward list <id>` | Lista los bloques premiados. |
| `/dungeon reward set <id> <rewardId> <one-time\|respawn-seconds\|direct-give\|announce\|loot-table> <valor>` | Ajusta el bloque. |

### Transferencia (`qsdungeons.admin`)

| Comando | Descripción |
|---|---|
| `/dungeon export <id> config [nombre]` | Exporta la config a `exports/<nombre>.zip`. |
| `/dungeon save <id> <nombre>` | Guarda la región como estructura WorldEdit. |
| `/dungeon export <id> all [nombre]` | Guarda la estructura y exporta el `.zip`. |
| `/dungeon import <id> <archivo>` | Importa un `.zip` o una estructura. |
| `/dungeon paste <id> <nombre>` | Pega una estructura en tu posición. |
| `/dungeon structures <id>` | Lista las estructuras de la dungeon. |
| `/dungeon wand` | Te da el hacha de WorldEdit. |

---

## Permisos

| Permiso | Por defecto | Descripción |
|---|---|---|
| `qsdungeons.use` | `true` | Usar `/dungeon` y entrar a dungeons. |
| `qsdungeons.admin` | `op` | Administrar dungeons, jefes, loot y regiones. |
| `qsdungeons.admin.bypass` | `op` | Ignorar la protección de bloques. |
| `qsdungeons.cooldown.bypass` | `op` | Ignorar el cooldown de reentrada. |

Además, cada dungeon puede exigir un permiso extra con
`settings.entry-permission`.

---

## Editor gráfico (GUI)

El editor funciona con inventarios de Bukkit (`InventoryHolder`) y está pensado
para no tener que memorizar comandos. Abre el menú principal con:

```text
/dungeon gui          # editor principal (solo administradores)
/dungeon gui <id>     # editor directo de una dungeon (solo administradores)
/dungeon menu         # menú de acceso para jugadores
```

Menús disponibles:

| Menú | Qué permite |
|---|---|
| Principal | Editor administrativo: ver/editar dungeons, crear una nueva o recargar la configuración. |
| Dungeons disponibles | Menú de jugadores: muestra únicamente dungeons habilitadas y permite entrar mediante clic. |
| Dungeons disponibles | Consultar estado, jugadores, disponibilidad y cooldown; clic para entrar. |
| Lista de dungeons | Editar, activar/desactivar o borrar (Shift + clic derecho). |
| Editor de dungeon | Ajustes, spawns, jefes, loot, cofres, premios, región, entrada/salida, iniciar/parar/reiniciar. |
| Ajustes | Todos los valores de `settings` (jugadores, tiempo, protección, bossbar, etc.). |
| Spawns | Crear/editar/borrar spawns y elegir entidad vanilla o de MythicMobs. |
| Jefes | Crear/editar/borrar jefes (incluidos mobs de MythicMobs) con bossbar propia. |
| Equipamiento | Arrastrar armaduras, armas y objetos a cabeza, pechera, piernas, botas, mano principal o secundaria. |
| Loot | Crear tablas, arrastrar items, cambiar tiradas/unicidad y quitar entradas. |
| Cofres | Registrar el cofre que miras y cambiar su tabla, relleno o estado. |
| Bloques premiados | Registrar el bloque que miras y ajustar sus opciones. |

Controles habituales:

- **Clic izquierdo**: editar o ciclar el valor. Los booleanos alternan siempre
  entre `Sí` y `No`.
- **Clic derecho**: escribir el valor exacto por chat (`cancelar` para salir).
- **Shift + clic derecho** en listas: borrar con confirmación.
- En **Equipamiento**, arrastra directamente los objetos desde tu inventario y
  cierra el menú para guardar. Se conservan encantamientos, nombres, lore,
  componentes y texturas personalizadas.
- En el editor de **Loot**, arrastra un objeto a la ranura `Arrastra un item
  aqui`. Se añadirá automáticamente como una nueva entrada conservando
  sus encantamientos, nombre, lore y componentes.
- En **Dungeons disponibles**, el cooldown se muestra en segundos, minutos,
  horas, días o meses según el tiempo restante. Al hacer clic se usa la entrada
  normal de la dungeon y se respeta `auto-start`, permisos, capacidad y cooldown.
- `respawn-seconds: 0` y `refill-seconds: 0` son los valores predeterminados:
  no reaparecen mobs, bloques premiados ni loot de cofres salvo que se configure
  explícitamente un tiempo mayor que cero.
- Los prompts de chat se cancelan también al escribir `cancelar`.

### `menus.yml`

Todo el aspecto del editor es configurable sin recompilar. El archivo incluye:

- `format`: textos para sí/no y valores vacíos.
- `messages`: mensajes del editor.
- `prompts`: instrucciones de los campos que se escriben por chat.
- `menus.<menu>`: `title`, `size` y `list-slots` (por ejemplo
  `"10-16,19-25,28-34"`).
- `menus.<menu>.items.<clave>`: `slot`, `material`, `name`, `lore`, `amount`,
  `glow` y `enabled`.

Las **claves de los items son fijas** (el plugin las usa para saber qué acción
ejecutar), pero puedes cambiar libremente su material, slot, nombre, lore,
cantidad y brillo, o desactivarlas con `enabled: false`. Todos los textos usan
**MiniMessage** y admiten placeholders como `{dungeon}`, `{name}`, `{state}`,
`{players}`, `{spawn}`, `{boss}`, `{chest}`, `{loot}`, `{value}` o
`{location}`.

### `player-menus.yml`

Este archivo controla únicamente `/dungeon menu`; no modifica el editor
administrativo. Solo lista dungeons con `enabled: true` y permite usar un item
distinto según el estado:

Puedes controlar qué dungeons aparecen:

```yaml
dungeons:
  mode: whitelist # all, whitelist o blacklist
  ids:
    - dungeon_principal
    - dungeon_evento
```

`all` muestra todas las dungeons habilitadas, `whitelist` solo muestra los IDs
indicados y `blacklist` oculta los IDs indicados.

- `dungeon-entry-available`: lista para entrar.
- `dungeon-entry-active`: instancia activa.
- `dungeon-entry-cooldown`: cooldown del jugador.
- `dungeon-entry`: plantilla de respaldo.

Placeholders: `{dungeon}`, `{name}`, `{state}`, `{players}`, `{max_players}`,
`{enabled}`, `{ready}`, `{available}`, `{cooldown}` y `{world}`. Cada item acepta
`material`, `name`, `lore`, `amount`, `glow` y `custom-model-data`.

También admite items de Nexo y Oraxen:

```yaml
      dungeon-entry-active:
        provider: NEXO
        item-id: dungeon_activa
        material: PAPER
        name: "<gold>{name}</gold>"
```

Usa `provider: ORAXEN` para Oraxen. Si el plugin o el ID no existe, se utiliza
el `material` configurado como respaldo.

`/dungeon reload` vuelve a leer `menus.yml` y `player-menus.yml` (y cierra las partidas activas,
igual que el resto de la recarga en caliente).

### Actualizador automático de claves

QSDungeons compara los archivos existentes con las plantillas incluidas en el
JAR al iniciar y al ejecutar `/dungeon reload`. Si una versión nueva añade una
clave, se copia únicamente esa clave a `config.yml`, `messages.yml`,
`menus.yml` o `player-menus.yml`; los valores existentes no se reemplazan. Así puedes actualizar el
plugin sin perder traducciones, colores ni ajustes propios. Se registra en la
consola qué archivo recibió claves nuevas.

---

## Esquema YAML

### `config.yml`

Estos valores son globales y sirven como valores predeterminados para las
dungeons nuevas:

```yaml
settings:
  debug: false                    # Mas informacion en consola
  generate-example-dungeon: true  # Crear ejemplo la primera vez
  default-world: world            # Mundo usado al crear dungeons
  bossbar-update-ticks: 20        # Actualizacion de bossbars; 20 = 1 segundo
  default-near-radius: 64         # Radio global por defecto

loot:
  fill-chests-on-start: true      # Rellenar cofres al comenzar
  clear-chests-on-start: true     # Vaciar cofres antes de rellenarlos
  announce-reward: true           # Avisar al recibir recompensas
  drop-naturally: false           # Soltar loot al suelo; false = inventario
  clear-vanilla-drops: true       # Quitar drops vanilla de mobs configurados

placeholders:
  time-format: "mm:ss"            # Formato de {time} y {time_left}
  progress-bar:
    length: 20                    # Longitud de {progress_bar}
    symbol: "|"                   # Simbolo usado en la barra
    completed-color: "<green>"    # Color de la parte completada
    remaining-color: "<dark_gray>" # Color de la parte restante
```

En `sounds` se pueden indicar sonidos Bukkit, por ejemplo
`ENTITY_ENDER_DRAGON_GROWL`; dejar un valor vacío (`""`) desactiva ese sonido.

### `dungeon.yml`

```yaml
id: mi_dungeon                    # ID unico; tambien es el nombre de la carpeta
display-name: "<gradient:#8e2de2:#4a00e0><bold>Mi Dungeon</bold></gradient>" # Nombre visible
enabled: true                     # Permite que los jugadores entren
world: world                      # Mundo principal de la dungeon

region:                            # Zona protegida y de deteccion de entrada
  world: world                     # Mundo de la region
  min:
    x: 100                         # Esquina minima X
    y: 60                          # Esquina minima Y
    z: 100                         # Esquina minima Z
  max:
    x: 180                         # Esquina maxima X
    y: 120                         # Esquina maxima Y
    z: 180                         # Esquina maxima Z

entry:                             # Lugar al que llegan los jugadores
  world: world                     # Mundo de entrada
  x: 100.5                         # Coordenada X
  y: 65.0                          # Coordenada Y
  z: 100.5                         # Coordenada Z
  yaw: 90.0                        # Direccion horizontal
  pitch: 0.0                       # Inclinacion de la camara

exit:                              # Lugar al que salen al terminar
  world: world                     # Mundo de salida
  x: 50.5                          # Coordenada X
  y: 65.0                          # Coordenada Y
  z: 50.5                          # Coordenada Z

settings:
  max-players: 8                  # Maximo de jugadores simultaneos
  min-players: 1                  # Jugadores necesarios para iniciar automaticamente
  time-limit-seconds: 30m         # Acepta 1s, 1m, 1h, 1d o 1mes; 0 = sin limite
  auto-enter: true                # Entrar al pisar la region
  auto-start: true                # Iniciar al alcanzar min-players
  exit-on-leave-region: true      # Sacar al jugador si abandona la region
  leave-on-death: false           # Sacar al jugador cuando muere
  reset-on-empty: true            # Reiniciar si no queda ningun jugador
  reset-delay-seconds: 10s        # Tiempo antes del reinicio por estar vacia
  end-delay-seconds: 5s           # Tiempo de loot antes de teletransportar al terminar
  allow-rejoin: true              # Permitir volver tras salir durante la partida
  protection: true                # Proteger bloques de la region
  allow-chest-break: false        # Permitir romper cofres registrados; se restauran despues
  bosses-after-waves: false       # false = jefes al inicio; true = jefes al acabar oleadas
  entry-permission: ""            # Permiso extra; vacio = no exigirlo
  cooldown-seconds: 0             # Espera para volver; acepta 1h, 1d, 1mes; 0 = desactivado
  complete-on: BOSSES             # BOSSES, KILLS, BOSSES_AND_KILLS o MANUAL
  required-kills: -1              # Kills necesarias; -1 = calcular automaticamente
  bossbar:
    enabled: true               # Mostrar la bossbar
    title: "<gradient:#8e2de2:#4a00e0><bold>{dungeon}</bold></gradient> <dark_gray>| <white>{kills}<gray>/<white>{total}</white> <dark_gray>| <yellow>Mobs: {mobs} Jefes: {bosses} <dark_gray>| <yellow>{time}" # Titulo MiniMessage
    color: PURPLE                # Color: PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE
    style: PROGRESS               # PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20, SOLID
    radius: 64                    # Distancia maxima para verla
  commands-on-start: []           # Comandos al iniciar; sin / inicial
  commands-on-complete: []        # Comandos al completar
  commands-on-fail: []            # Comandos al fallar
  commands-on-reset: []           # Comandos al reiniciar

spawns:
  oleada1:                         # ID del spawn; puedes tener varios
    type: VANILLA                   # VANILLA o MYTHIC
    entity: ZOMBIE                  # Entidad vanilla o ID de MythicMobs
    location: { world: world, x: 110.5, y: 65.0, z: 110.5 } # Punto base
    amount: 5                       # Cantidad total de la oleada
    max-alive: 5                    # Maximo vivo al mismo tiempo
    respawn-seconds: 0              # 0 = sin respawn; configura segundos para reaparecer
    spawn-interval-seconds: 0       # Intervalo entre spawns iniciales; 0 = juntos
    radius: 3.0                     # Radio aleatorio alrededor del punto
    min-player-distance: 8          # Distancia minima respecto a jugadores
    require-ground: true            # Exigir suelo solido y espacio libre
    health: 40.0                    # Vida maxima; 0 = vida vanilla
    damage: 5.0                     # Danio; 0 = dano vanilla
    speed: 0.25                     # Velocidad; 0 = velocidad vanilla
    follow-range: 32.0              # Distancia de seguimiento; 0 = vanilla
    name: "<red>Zombi de la cripta" # Nombre visible en MiniMessage
    glowing: true                   # Brillo de entidad
    baby: false                     # Crear variante bebe si es posible
    ai: true                        # Activar inteligencia artificial
    persistent: true                # No desaparecer por distancia
    clear-vanilla-drops: true       # Quitar drops vanilla al morir
    burn-in-daylight: false         # Permitir que arda con el sol
    loot-table: comun               # Tabla de loot al morir; vacio = ninguna
    commands-on-spawn: []           # Comandos al aparecer
    equipment:                      # Equipo aplicado al mob vanilla
      head:
        material: IRON_HELMET       # Casco
      hand:
        material: IRON_SWORD        # Objeto de la mano principal

chests:
  cofre1:
    location: { world: world, x: 120, y: 65, z: 120 } # Ubicacion del cofre
    loot-table: comun                # Tabla que se coloca dentro
    refill-seconds: 0                # Tiempo de reabastecimiento; 0 = una vez
    enabled: true                    # Activar este cofre

rewards:
  premio1:
    location: { world: world, x: 130, y: 65, z: 130 } # Bloque que se rompe
    loot-table: raro                 # Loot entregado al romperlo
    material: CHEST                  # Material original del bloque
    block-data: "minecraft:chest[facing=north,type=single,waterlogged=false]" # Estado del bloque
    one-time: true                   # Solo una vez por partida
    respawn-seconds: 0               # 0 = sin respawn; requiere one-time=false
    direct-give: false               # false = soltar al mundo; true = inventario
    announce: true                   # Mostrar mensaje al recibir loot
    commands: []   # Comandos ejecutados al romperlo
```

> `complete-on`:
> - `BOSSES`: se completa al morir todos los jefes.
> - `KILLS`: al alcanzar `required-kills` (si es `-1`, se calcula con los
>   enemigos iniciales).
> - `BOSSES_AND_KILLS`: ambas condiciones.
> - `MANUAL`: solo con `/dungeon stop` o desde otro plugin.

Los campos de tiempo aceptan segundos numéricos y formatos legibles combinados:

```yaml
cooldown-seconds: 1d
time-limit-seconds: 30m
reset-delay-seconds: 1m30s
respawn-seconds: 2h
refill-seconds: 1mes
```

Un mes se interpreta como 30 días. El GUI y los mensajes muestran los tiempos
en formato legible (`45s`, `12m 30s`, `2h 10m`, `3d` o `1 mes`).

### `bosses/<bossId>.yml`

```yaml
id: jefe1
type: VANILLA                       # VANILLA o MYTHIC
entity: ZOMBIE                      # Entidad o ID de MythicMobs
display-name: "<dark_red><bold>El Guardián</bold>" # Nombre visible
location: { world: world, x: 140.5, y: 65.0, z: 140.5 } # Ubicacion
health: 300.0                       # Vida maxima
damage: 12.0                        # Danio de ataque
speed: 0.3                          # Velocidad de movimiento
follow-range: 48.0                  # Rango de seguimiento
glowing: true                       # Brillo
baby: false                         # Variante bebe si existe
ai: true                            # Inteligencia artificial
persistent: true                    # No desaparecer por distancia
clear-vanilla-drops: true           # Quitar drops vanilla
burn-in-daylight: false             # Permitir que arda con el sol
announce-spawn: true                # Avisar cuando aparece
announce-death: true                # Avisar cuando muere
loot-table: jefe                    # Tabla de loot del jefe
bossbar:
  enabled: true                     # Mostrar bossbar propia
  title: "<red><bold>{boss}</bold></red> <dark_gray>| <white>{boss_health}<gray>/<white>{boss_max_health}" # Titulo
  color: RED                        # Color de la barra
  style: NOTCHED_10                 # Estilo de la barra
  radius: 64                        # Distancia para verla
commands-on-spawn: []               # Comandos al aparecer
commands-on-death: []               # Comandos al morir
equipment:
  hand:
    material: NETHERITE_SWORD        # Arma principal
```

### `loot/<lootId>.yml`

```yaml
id: comun                       # ID de la tabla
rolls: 1-3                      # Numero aleatorio de entradas que intenta sacar
unique: false                   # No repetir una misma entrada durante las tiradas
entries:
  - item:
      material: DIAMOND           # Material del item
      amount: 1                   # Cantidad base del item
      name: "<aqua>Gema de poder" # Nombre MiniMessage
      lore:
        - "<gray>Una gema brillante." # Descripcion del item
      enchantments:
        sharpness: 3               # Encantamiento y nivel
    amount: 1-2                    # Cantidad aleatoria entregada
    chance: 0.25                   # Probabilidad de entrar en la tirada: 25 %
    weight: 1                      # Peso relativo si varias entradas pasan chance
    message: "<green>¡Has encontrado una gema!" # Mensaje al jugador
    commands: []                   # Comandos si esta entrada resulta elegida
  - item:
      material: GOLDEN_APPLE       # Segundo item posible
    amount: 1                      # Cantidad
    chance: 1.0                    # 100 % de probabilidad de ser candidato
    weight: 2                      # Tiene el doble de peso que la gema
```

Los items se serializan completos (material, cantidad, nombre, lore,
encantamientos, NBT/componentes, etc.) mediante el serializador de Bukkit.

### `messages.yml`

Todos los textos visibles están ahí y usan **MiniMessage**:

```yaml
prefix: "<gradient:#8e2de2:#4a00e0><bold>QS</bold></gradient> <dark_gray>»</dark_gray> " # Prefijo reutilizable

join:
  success: "{prefix}<green>Has entrado a <white>{dungeon}</white>." # Mensaje al entrar
```

Puedes añadir saltos de línea con `\n`, degradados, negritas, hover, click,
etc. El `prefix` se sustituye automáticamente en `{prefix}`.

Cada bloque de mensajes usa una clave distinta. Por ejemplo, `join.success`
es el mensaje de entrada, `dungeon.completed` se muestra al completar,
`loot.reward-broken` al romper un bloque premiado y
`protection.denied` cuando se bloquea una acción. Los textos son MiniMessage;
los valores entre llaves como `{dungeon}`, `{player}`, `{amount}` o `{time}` se
reemplazan automáticamente.

---

## Placeholders

### Internos (en `messages.yml`, bossbars y comandos)

| Placeholder | Descripción |
|---|---|
| `{prefix}` | Prefijo configurado. |
| `{player}` | Nombre del jugador. |
| `{world}` | Mundo del jugador. |
| `{dungeon}` / `{dungeon_id}` | ID de la dungeon. |
| `{dungeon_name}` | Nombre visible de la dungeon. |
| `{state}` | Estado traducido (`Inactiva`, `En marcha`…). |
| `{players}` / `{max_players}` | Jugadores actuales / máximo. |
| `{time}` | Tiempo transcurrido (`mm:ss`). |
| `{time_left}` | Tiempo restante. |
| `{kills}` / `{total}` | Enemigos derrotados / total. |
| `{mobs}` | Cantidad total de mobs de las oleadas. |
| `{bosses}` | Cantidad total de jefes configurados. |
| `{kills_remaining}` | Enemigos que faltan. |
| `{progress}` | Progreso en porcentaje. |
| `{progress_bar}` | Barra de progreso configurable. |
| `{boss}` | Nombre del jefe principal. |
| `{boss_health}` / `{boss_max_health}` | Vida actual / máxima. |
| `{boss_percent}` | Vida del jefe en porcentaje. |

### PlaceholderAPI

Expansión: **`qsdungeons`**. Ejemplos:

```text
%qsdungeons_dungeon%          %qsdungeons_state%
%qsdungeons_players%          %qsdungeons_max_players%
%qsdungeons_time%             %qsdungeons_time_left%
%qsdungeons_kills%            %qsdungeons_total%
%qsdungeons_progress%         %qsdungeons_progress_bar%
%qsdungeons_boss%             %qsdungeons_boss_health%
%qsdungeons_boss_percent%     %qsdungeons_dungeons%
%qsdungeons_dungeon_count%    %qsdungeons_has_dungeon%
```

También puedes consultar una dungeon concreta:

```text
%qsdungeons_mi_dungeon_kills%
%qsdungeons_mi_dungeon_state%
%qsdungeons_mi_dungeon_progress%
```

Los placeholders de una dungeon concreta funcionan aunque el jugador no esté
dentro de ella (si la instancia existe).

---

## MythicMobs

- Instala MythicMobs y define tus mobs con normalidad.
- Usa el ID del mob de MythicMobs como `entity` con `type: MYTHIC`, o al crear
  el spawn/jefe con el prefijo `mythic:`:

```text
/dungeon spawn add mi_dungeon oleada1 mythic:MiZombi 5
/dungeon boss create mi_dungeon jefe1 mythic:MiJefe
```

- La vida, el daño y el nombre se aplican también a los mobs de MythicMobs,
  respetando su configuración base.
- Los IDs de MythicMobs aparecen en el autocompletado como `mythic:<id>` y en el
  selector de entidades del GUI, separados de las entidades vanilla.
- Si MythicMobs no está presente, los spawns `MYTHIC` se omiten con un aviso en
  consola y el resto de la dungeon sigue funcionando.

---

## WorldEdit e import/export

### Guardar una estructura

1. Configura la región de la dungeon con `pos1`, `pos2` y `region`.
2. Ejecuta `/dungeon save <id> <nombre>`.
3. El archivo se guarda en `dungeons/<id>/schematics/<nombre>.schem`.

### Pegar una estructura

```text
/dungeon paste <id> <nombre>
```

Se pega en tu posición actual, respetando `include-entities` e
`ignore-air-on-paste` de `config.yml`. El jugador necesita permisos de
WorldEdit para construir en esa zona.

### Exportar

```text
/dungeon export <id> config [nombre]   # solo YAML -> exports/<nombre>.zip
/dungeon export <id> all [nombre]      # estructura + YAML
```

El `.zip` contiene `dungeon.yml`, `bosses/`, `loot/` y `schematics/`. Los
nombres se sanean y se evita cualquier ruta fuera de la carpeta de exports.

### Importar

```text
/dungeon import <id> <archivo.zip>          # importa la configuración
/dungeon import <id> <archivo.schem>        # copia la estructura a la dungeon
```

- Los `.zip` se validan contra *zip-slip* y tienen un límite de descompresión
  configurable (`import-export.max-unzip-bytes`).
- Los formatos de estructura aceptados son `.schem`, `.schematic` y `.nbt`.
- Tras importar un `.zip`, las dungeons se recargan automáticamente.

---

## Compilar

```bash
mvn clean package
```

El JAR final queda en `target/QSDungeons-1.0.0.jar`. Requiere JDK 21 y Maven
3.9+.

Dependencias opcionales usadas en tiempo de compilación (todas `provided`):

- `io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT`
- `me.clip:placeholderapi:2.11.6`
- `com.sk89q.worldedit:worldedit-bukkit:7.3.9`
- `com.sk89q.worldedit:worldedit-core:7.3.9`
- `io.lumine:Mythic-Dist:5.6.1`

---

## Notas de diseño

- **Bossbars por proximidad**: cada jugador recibe su propia `BossBar` de
  Adventure. Solo se muestra si está dentro del radio configurado; al salir del
  radio o de la dungeon se oculta y se elimina.
- **Bloques premiados**: se guarda el `BlockData` original y se restaura al
  reiniciar la dungeon o al cumplirse el tiempo de reaparición.
- **Cofres persistentes**: al registrar un cofre se guarda su `BlockData`. Al
  iniciar y al cerrar la partida se vuelve a colocar si faltaba, se vacía su
  inventario y se rellena según la tabla. Si un jugador intenta romperlo, la
  acción se cancela con un aviso, por lo que nunca se pierde el cofre.
- **Protección**: si `settings.protection` está activo, los jugadores sin
  `qsdungeons.admin.bypass` no pueden romper ni colocar bloques dentro de la
  región (salvo los cofres y bloques premiados gestionados por el plugin).
- **Una instancia por dungeon**: todos los jugadores comparten la misma
  instancia; el estado se reinicia al quedarse vacía (si `reset-on-empty`).
- **Recarga en caliente**: `/dungeon reload` recarga `config.yml`,
  `messages.yml`, `menus.yml` y todas las dungeons desde disco.
#   q s d u n g e o n s 
 
 
