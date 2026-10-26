# EmuMod

**Creado por [Spar Retro](https://www.youtube.com/@sparretro)** · Mod de Fabric para **Minecraft Java 1.20.1**

Juega **tus propios juegos** de Game Boy Advance, Nintendo DS, Nintendo 3DS, PSP, NES, Nintendo 64, PlayStation 2, PlayStation 3 y Wii **dentro de Minecraft**: consolas en 3D que el personaje sostiene con las dos manos, televisores con cable y control, y soporte para **joystick / gamepad**.

> **No incluye juegos, BIOS ni firmware**, y no descarga nada. Cargas tus propias copias desde el disco.

## Créditos

**EmuMod** es una idea y un proyecto de **Spar Retro**.

- YouTube: **[@sparretro](https://www.youtube.com/@sparretro)** — suscríbete para ver el mod en acción y lo que viene
- Si muestras el mod en un video, stream o modpack, menciona a **Spar Retro** y enlaza este repositorio

Antes se llamaba **Televisor**. Los emuladores, los modelos 3D y demás recursos de terceros tienen sus propios autores y licencias: están todos en [Licencias](#licencias) y en la carpeta [`licenses/`](licenses).

## Instalación

Por ahora funciona en **Windows de 64 bits** (los emuladores son programas nativos de Windows).

1. **Instala Fabric.** Descarga el instalador de [fabricmc.net/use/installer](https://fabricmc.net/use/installer/), ábrelo, elige **Minecraft 1.20.1** y toca **Instalar**. En el launcher de Minecraft aparece un perfil nuevo, **fabric-loader-1.20.1**.
2. **Descarga Fabric API** para **1.20.1** desde [Modrinth](https://modrinth.com/mod/fabric-api/versions?g=1.20.1&l=fabric) (el archivo `fabric-api-0.92.x+1.20.1.jar`).
3. **Descarga EmuMod**: entra a [**Releases**](https://github.com/Sparretro/EmuMod/releases/latest) y baja `emumod-2.2.0.jar`.
4. **Copia los dos `.jar`** a la carpeta `mods` de Minecraft. Para abrirla, aprieta **Windows + R**, escribe `%appdata%\.minecraft\mods` y Enter (si no existe la carpeta `mods`, créala).
5. **Abre el launcher**, elige el perfil **fabric-loader-1.20.1** y juega. Todo lo del mod está en la pestaña creativa **EmuMod**; en supervivencia se fabrica (recetas más abajo).
6. Para jugar, **carga tus juegos** desde el botón **«Cargar juego»** de cada consola (tecla **O** para ver las opciones). Algunas consolas piden BIOS o firmware: mira [BIOS y firmware](#bios-y-firmware).

Si venías del mod **Televisor**, borra `televisor-1.0.0.jar`: tus BIOS, partidas y estados se mueven solos de `config/televisor` a `config/emumod` la primera vez que abras el juego. Los objetos del mod viejo que tuvieras en un mundo no pasan al nuevo.

## BIOS y firmware

| Consola | ¿Necesita? | De dónde sale |
|---|---|---|
| NES, N64, 3DS, PSP, Wii | No | — |
| Nintendo DS | Opcional | Funciona sin nada con las BIOS libres de melonDS. Con las de **tu** DS (`bios7.bin`, `bios9.bin` y `firmware.bin`) se ve igual que una consola real |
| GBA y GBA SP | Opcional | Funciona sin nada con la BIOS libre de [Cult-of-GBA](https://github.com/Cult-of-GBA/BIOS), que ya viene en el mod. Con la original (`gba_bios.bin`, 16 KB) algunos juegos andan mejor y aparece el logo al prender |
| PlayStation 2 | **Sí** | La BIOS de **tu** PS2 (por ejemplo `scph39001.bin`, 4 MB) |
| PlayStation 3 | **Sí** | El firmware oficial `PS3UPDAT.PUP`, **gratis en la página de Sony** |

**PlayStation 3 (gratis y oficial).** Entra a la [página de actualización del software del sistema de PS3](https://www.playstation.com/es-es/support/hardware/ps3/system-software/) y descarga el archivo de actualización `PS3UPDAT.PUP` (unos 200 MB). En el juego, frente a la tele con la PS3, pulsa **O** → **«Firmware: falta»** y elígelo.

**PlayStation 2.** La BIOS tiene copyright de Sony, así que no se puede descargar legalmente de internet: se saca de **tu propia PS2** con un programa como **BIOS Dumper**. La guía oficial de PCSX2 (el emulador que usa el mod) explica cómo: [pcsx2.net/docs/setup/bios](https://pcsx2.net/docs/setup/bios/). Luego, en el juego, **O** → **«BIOS: falta»** y elige el `.bin`.

**Nintendo DS.** Las BIOS y el firmware se copian de **tu propia DS** (con un programa casero como DSBF Dumper o, en una DSi o 3DS, con las herramientas de su homebrew). Pon los tres archivos en la misma carpeta y en el juego toca **O** → **«BIOS: libres»** y elige el `firmware.bin`: los otros dos se encuentran solos.

**Game Boy Advance.** Igual que la de PS2, la original se copia de **tu propia GBA** (por ejemplo con un lector de cartuchos como el GBxCart RW y su programa, o con un cartucho flash y una herramienta casera para volcar la BIOS). En el juego, **O** → **«BIOS: libre»** y elige el `gba_bios.bin`.

Todo lo que eliges se copia a `.minecraft/config/emumod/sistema/` y se usa desde el próximo juego. El mod nunca sube ni comparte esos archivos.

## Consolas

| Consola | Cómo se juega | Emulador |
|---|---|---|
| **Game Boy Advance** | En las manos, delante de los ojos; se apoya en el piso, una mesa, la pared o el techo | SkyEmu |
| **Game Boy Advance SP** | Igual que la GBA, abierta (mismo emulador y partidas que la GBA) | SkyEmu |
| **Nintendo DS** | En las manos, abierta; la pantalla de abajo es táctil con el mouse | melonDS DS |
| **Nintendo 3DS** | En las manos, abierta; la pantalla de abajo es táctil con el mouse | Citra |
| **PSP** | En las manos, delante de los ojos; se apoya acostada | PPSSPP |
| **NES** | Sentado frente a un televisor, con cable y control | QuickNES |
| **Nintendo 64** | Igual que la NES | Mupen64Plus-Next |
| **PlayStation 2** (clásica y Slim) | Igual que la NES; necesita tu BIOS | LRPS2 (PCSX2) |
| **PlayStation 3** (clásica y Slim) | Igual que la NES; necesita el firmware | RPCS3 |
| **Wii** | Igual que la NES; el puntero del Wiimote es el mouse | Dolphin |

Mientras juegas, Minecraft esconde su interfaz (barra de inventario, vida, chat y mira), como con **F1**.

### Consolas portátiles: GBA, GBA SP, DS, 3DS y PSP

| Consola | Receta (forma) |
|---|---|
| **Game Boy Advance** | `Hierro Panel Hierro` / `Botón Redst. Botón` / `Hierro Tinte violeta Hierro` |
| **Game Boy Advance SP** | `Hierro Panel Hierro` / `Botón Redst. Botón` / `Tinte gris claro Hierro Tinte gris claro` |
| **Nintendo DS** | `Hierro Panel Hierro` / `Botón Panel Botón` / `Hierro Tinte rosa Hierro` |
| **Nintendo 3DS** | `Hierro Panel Hierro` / `Botón Panel Botón` / `Hierro Tinte cian Hierro` |
| **PSP** | `Hierro Panel Hierro` / `Botón Redst. Botón` / `Tinte negro Hierro Tinte negro` |

(Panel = panel de vidrio, Botón = botón de piedra, Redst. = redstone.)

- **Click derecho** con la consola en la mano: el personaje se la lleva a los ojos y juegas mirando su pantalla, con el mundo de fondo. **Esc** la baja.
- **O** (configurable en *Opciones > Controles*, categoría *EmuMod*) muestra u oculta las opciones: cargar juego, BIOS, pausa, volumen, velocidad, estados, filtro y FOV. Sin juego cargado aparecen solas. Con la consola en la mano sin estar jugando, **O** la sube a los ojos con las opciones abiertas.
- Los botones del modelo se hunden al apretarlos y **también se clickean con el mouse** (en la cruz puedes arrastrar para las diagonales).
- **Shift + click derecho** sobre un bloque la **apoya** (la GBA acostada, en la pared o en el techo; la SP, la DS y la 3DS abiertas; la PSP acostada). Click derecho sobre la apoyada para jugar; rompiéndola la recuperas.
- **FOV**: se aplica mientras la consola está delante de los ojos; más chico = la consola se ve más grande.

**GBA y SP.** Comparten el emulador con el bloque **Televisor**: el juego, la partida y los estados son los mismos. *(Opcional, recomendado)* toca **«BIOS: libre»** y elige tu `gba_bios.bin` (16 KB); sin ella se usa la BIOS libre de [Cult-of-GBA](https://github.com/Cult-of-GBA/BIOS), menos precisa. Filtros de pantalla:

| Filtro | Qué hace |
|---|---|
| **Original** | Los colores tal cual los da el emulador |
| **Color GBA** (por defecto) | Colores como en la pantalla real de la GBA, sin luz de fondo: más apagados y cálidos (shader `gba-color` de libretro) |
| **LCD de GBA** | Color GBA más la grilla de subpíxeles y las líneas del LCD (shader `lcd3x` de libretro) |

**DS.** Como la 3DS: la pantalla de abajo se toca con el mouse. Carga juegos `.nds`. El modelo es una DSi, pero emula una DS (los juegos exclusivos de DSi no andan). Funciona sin BIOS; con las tuyas arranca con el menú y el idioma de tu consola (ver [BIOS y firmware](#bios-y-firmware)).

**3DS.** Haz click (y arrastra) sobre la pantalla de abajo: es el stylus. La palanca circular se arrastra con el mouse. Carga juegos **desencriptados** (`.3ds`, `.cci`, `.cxi`, `.cia`) y programas caseros (`.3dsx`); no hace falta BIOS. Citra guarda su SD virtual y su NAND en `config/emumod/sistema/Citra/`.

**PSP.** Carga imágenes de UMD (`.iso`, `.cso`, `.chd`), juegos `.pbp` y programas caseros (`.elf`, `.prx`). No hace falta BIOS ni firmware: PPSSPP emula el sistema de la PSP. Los botones del modelo se hunden y la palanca analógica se arrastra con el mouse. Las partidas de los juegos quedan en `config/emumod/sistema/PSP/SAVEDATA/`.

### Consolas de televisor: NES, N64, PS2, PS3 y Wii

Todas se arman igual (al fabricar una, el chat explica los pasos):

1. **Coloca la consola** en el piso o arriba de cualquier bloque.
2. **Coloca un televisor a un bloque** (Televisor CRT o de plasma). Con la **mano vacía**, click derecho en la consola: sale un **cable**; click derecho en el televisor para conectarlo.
3. **Click derecho en la consola con su control** para enchufarlo.
4. Con el **control en la mano**, click derecho en la consola: el personaje **se sienta frente al televisor** con el control en las manos. Hay que estar a menos de 5 bloques. **Esc** te levanta.

| Cosa | Receta (forma) |
|---|---|
| **Televisor CRT** | `Hormigón negro x3` / `Hormigón negro Panel Hormigón negro` / `Hierro Redst. Hierro` |
| **Televisor de plasma** | `Hormigón negro x3` / `Panel x3` / `Hierro Redst. Hierro` |
| **NES** | `Hierro x3` / `Tinte gris claro Redst. Tinte gris claro` / `Tinte gris x3` |
| **Control de NES** | ` - Cuerda - ` / `Botón Redst. Botón` / `Hierro x3` |
| **Nintendo 64** | `Hierro x3` / `Tinte negro Redst. Tinte negro` / `Tinte negro x3` |
| **Control de N64** | ` - Cuerda - ` / `Botón Redst. Botón` / `Hierro Tinte gris claro Hierro` |
| **PlayStation 2** | `Tinte negro x3` / `Hierro Redst. Hierro` / `Tinte negro x3` |
| **PlayStation 2 Slim** | `Tinte negro Redst. Tinte negro` / `Hierro x3` |
| **Control de PS2** | ` - Cuerda - ` / `Botón Redst. Botón` / `Tinte negro Hierro Tinte negro` |
| **PlayStation 3** | `Tinte negro x3` / `Hierro Diamante Hierro` / `Tinte negro x3` |
| **PlayStation 3 Slim** | `Tinte negro Diamante Tinte negro` / `Hierro x3` |
| **Control de PS3** | ` - Redst. - ` / `Botón Tinte negro Botón` / `Tinte negro Hierro Tinte negro` |
| **Wii** | `Tinte blanco Cuarzo Tinte blanco` / `Tinte blanco Redst. Tinte blanco` / `Tinte blanco Hierro Tinte blanco` |
| **Wiimote** | `Tinte blanco Botón Tinte blanco` / `Tinte blanco Redst. Tinte blanco` / `Tinte blanco Cuarzo Tinte blanco` |

- **NES**: juegos `.nes`. Tiene **filtro CRT**.
- **N64**: juegos `.z64`, `.n64` o `.v64`. Dibuja por software con Angrylion (el más fiel): pide bastante CPU.
- **PS2**: necesita **tu BIOS** (por ejemplo `scph39001.bin`): pulsa **O** y toca **«BIOS: falta»**. Lee `.iso`, `.bin`/`.cue`, `.chd`, `.cso` o `.m3u` directo del disco. Renderizador por software: pide una CPU fuerte.
- **PS3**: necesita el **firmware** (`PS3UPDAT.PUP`, de la página de Sony): **O** y **«Firmware: falta»**. Abre `.iso`, juegos en carpeta (`PS3_DISC.SFB` o `PARAM.SFO`), `EBOOT.BIN` o `.pkg`. La primera vez tarda varios minutos compilando módulos. Dibuja con Vulkan: pide una PC fuerte.
- **Wii**: juegos `.iso`, `.wbfs`, `.rvz`, `.gcz`, `.ciso`, `.wia`, `.wad`, `.dol` (también GameCube). El Wiimote se usa de costado; el puntero sigue al mouse.

### Opciones

| Botón | Qué hace |
|---|---|
| **Pausa** / **Seguir** | Congela el juego y lo retoma |
| **Reiniciar** | Como apagar y prender la consola; la partida guardada se conserva |
| **Volumen** | Se multiplica por el volumen general de Minecraft |
| **Velocidad** | x1, x2, x4, x6, x8 o x16 (acelerado no suena). Si tu PC no llega, aparece «Tu PC llega a xN» |
| **Ranura** / **Guardar estado** / **Cargar estado** | Cinco ranuras de estado por juego |
| **Keybinds** | Cambia la tecla de cada botón de la consola |

Las partidas se guardan solas en `config/emumod/partidas/` (como en la consola real, y también al pausar, cambiar de juego o cerrar Minecraft); los estados, en `config/emumod/estados/`. Se identifican por el nombre del archivo del juego.

## Controles

### Joystick / gamepad

Conecta un control **antes o durante** el juego (Xbox, PlayStation, Switch Pro y la mayoría de los genéricos: Minecraft los reconoce con la base de mapeos de SDL). Funciona a la vez que el teclado y el mouse; abajo a la derecha aparece el nombre del control conectado.

Los botones van **por posición**: el de abajo del gamepad (A en Xbox, ✕ en PlayStation) es el de abajo de la consola, y así con los cuatro.

| Gamepad | GBA / SP / NES | DS / 3DS | N64 | PS2 / PS3 / PSP | Wii (de costado) |
|---|---|---|---|---|---|
| Botón de abajo | B | B | A | ✕ | 2 |
| Botón de la derecha | A | A | C abajo | ◯ | A |
| Botón de la izquierda | — | Y | B | □ | 1 |
| Botón de arriba | — | X | C izquierda | △ | B |
| LB / RB | L / R | L / R | L / R | L1 / R1 | Home / Sacudir |
| LT / RT | — | ZL / ZR | Z | L2 / R2 | B / Sacudir |
| Cruz | Cruz | Cruz | Cruz | Cruz | Cruz |
| Palanca izquierda | Cruz | Cruz (DS), palanca circular (3DS) | Palanca | Palanca izquierda | Cruz |
| Palanca derecha | — | C-Stick (3DS) | Botones C | Palanca derecha | Inclinar |
| Start / Back | Start / Select | Start / Select | Start / — | Start / Select | + / − |
| **Back + Start** | **Abre o cierra las opciones** | | | | |

### Teclado (por defecto; se cambian en «Keybinds»)

| Consola | Teclas |
|---|---|
| GBA, SP, NES | Flechas = cruz, **Z / X** = A / B, **Enter** = Start, **Shift** = Select, **A / S** = L / R |
| DS | Flechas = cruz, **X Z S A** = A B X Y, **Q / E** = L / R, **Enter / Retroceso** = Start / Select |
| 3DS | Flechas = palanca circular (Shift = a la mitad), **X Z S A** = A B X Y, **Q / E** = L / R, **1 / 3** = ZL / ZR, **T F G H** = cruz, **Enter / Retroceso** = Start / Select |
| N64 | Flechas = palanca (Shift = a la mitad), **Z / X** = A / B, **C** o **Espacio** = Z, **A / S** = L / R, **I J K L** = botones C, **T F G H** = cruz, **Enter** = Start |
| PS2 / PS3 | Flechas = cruz, **W A S D** = palanca izquierda, **I J K L** = palanca derecha, **Z X C V** = ✕ ◯ □ △, **Q / E** = L1 / R1, **1 / 3** = L2 / R2, **N / M** = L3 / R3, **Enter / Retroceso** = Start / Select |
| PSP | Flechas = cruz, **W A S D** = palanca analógica, **Z X C V** = ✕ ◯ □ △, **Q / E** = L / R, **Enter / Retroceso** = Start / Select |
| Wii | Flechas = cruz, **Z / X** = 2 / 1, **C / V** = A / B, **Espacio** = sacudir, **Enter / Retroceso** = + / −, **H** = Home, **W A S D** = inclinar; el mouse es el puntero |

**Esc** cierra la pantalla (el juego queda en pausa). Con el mouse también se aprietan los botones de los modelos y se arrastran las palancas.

## Limitaciones

- Solo **Windows x64** por ahora. En otros sistemas el mod carga, pero la pantalla avisa que no hay emulador.
- **Un juego por consola a la vez** en todo el cliente.
- Los demás jugadores te ven sentado o con la consola en la mano, pero no ven la pantalla del juego.
- El juego solo avanza con su pantalla abierta.

## Cómo compilar

| Herramienta | Versión | Para qué |
|---|---|---|
| Git | cualquiera | Clonar con los submódulos |
| JDK | **25** | Ejecutar Gradle (Fabric Loom 1.18 lo exige) |
| JDK | **17** | Ejecutar el juego de desarrollo (`runClient`, `runGametest`) |
| [Zig](https://ziglang.org/download/) | 0.17.0 | Compilar en C SkyEmu, QuickNES y el puente (`zig cc`), sin Visual Studio |

```sh
git clone --recurse-submodules https://github.com/Sparretro/EmuMod.git
cd EmuMod
```

Pon las rutas de tu PC en el `gradle.properties` de tu usuario (`~/.gradle/gradle.properties`, no el del proyecto):

```properties
org.gradle.java.home=C:/ruta/al/jdk-25                  # o define JAVA_HOME con un JDK 25
org.gradle.java.installations.paths=C:/ruta/al/jdk-17   # JDK 17 para el juego de desarrollo
emumod.zig=C:/ruta/a/zig.exe                            # no hace falta si zig está en el PATH
```

```sh
./gradlew build
```

El mod queda en `build/libs/emumod-2.2.0.jar`, con todos los emuladores adentro.

| Tarea | Qué hace |
|---|---|
| `./gradlew runClient` | Minecraft de desarrollo con el mod |
| `./gradlew runGametest` | Pruebas automáticas en un servidor sin ventana |
| `./gradlew probarNucleo -Pbios=<ruta>`, `probarNes`, `probarN64`, `probarPs2 -Pbios=<ruta>`, `probarPs3 -Pfirmware=<ruta>`, `probarWii -Pjuego=<ruta>`, `probarN3ds`, `probarNds`, `probarPsp` | Prueban cada emulador fuera de Minecraft (las de NES, N64, DS, 3DS y PSP con programas de prueba propios) |
| `./gradlew runClientCaptura -PdevMundo=true [-PdevSolo=n3ds, nds, gba_sp o psp]` | Crea un mundo y guarda capturas de las consolas en `run/screenshots/` |

| Ruta | Contenido |
|---|---|
| `src/main` | Bloques, ítems, asiento, cable, recetas y recursos |
| `src/client` | Pantalla de juego, controles (`Controles`, `Joystick`), modelos 3D, manos, créditos, mixins y el frontend libretro (`emu/`) |
| `src/gametest`, `src/pruebasNucleo` | Pruebas (no van en el jar) |
| `native/` | Submódulos con el código fuente de cada emulador (sin modificar), las DLL oficiales del buildbot de libretro (con su SHA-256 verificado) y el puente propio en C (`native/puente`) |
| `tools/` | Conversores de modelos GLB al formato del mod (`ConvertirModelo.java` con `--separar`, `--simplificar`, `--quitar`...) |
| `licenses/` | Avisos de licencia de todo lo de terceros (también van en el jar) |

## Licencias

### EmuMod

El código y los recursos propios de EmuMod son de **Spar Retro**, bajo licencia **MIT** (ver [`LICENSE`](LICENSE)): puedes usarlo y modificarlo, **manteniendo el aviso de copyright de Spar Retro**.

### Emuladores (DLL dentro del jar)

Cada emulador es una DLL aparte que se carga en tiempo de ejecución por la API de libretro. Textos completos en [`licenses/`](licenses).

| Componente | Licencia | Código fuente |
|---|---|---|
| [SkyEmu](https://github.com/skylersaleh/SkyEmu) (GBA) + [Cult-of-GBA BIOS](https://github.com/Cult-of-GBA/BIOS) | MIT | `native/SkyEmu` (compilado sin modificar; este proyecto no contribuye código a SkyEmu) |
| [QuickNES](https://github.com/libretro/QuickNES_Core) (NES) | GPL-2.0 + LGPL-2.1 | `native/QuickNES` |
| [Mupen64Plus-Next](https://github.com/libretro/mupen64plus-libretro-nx) (N64) | GPL-2.0 | `native/Mupen64PlusNext` |
| [LRPS2](https://github.com/libretro/ps2) (PS2) | GPL-3.0 | `native/LRPS2` |
| [RPCS3](https://github.com/WizzardSK/rpcs3-libretro) (PS3) | GPL-2.0 | `native/RPCS3` |
| [Dolphin](https://github.com/libretro/dolphin) (Wii y GameCube) | GPL-2.0+ | [libretro/dolphin](https://github.com/libretro/dolphin) |
| [Citra](https://github.com/libretro/citra) (3DS) | GPL-2.0 | [libretro/citra](https://github.com/libretro/citra), commit `a0483e9` |
| [PPSSPP](https://github.com/hrydgard/ppsspp) (PSP) + sus archivos de sistema | GPL-2.0+ | [hrydgard/ppsspp](https://github.com/hrydgard/ppsspp), commit `09b8998` |
| [melonDS DS](https://github.com/JesseTG/melonds-ds) (DS) | GPL-3.0 | [JesseTG/melonds-ds](https://github.com/JesseTG/melonds-ds), v1.4.0 |
| `libretro.h`, runtime de mingw-w64, `compiler_rt` de Zig | MIT / ZPL-2.1 / MIT | |

### Modelos 3D

Todos de [Sketchfab](https://sketchfab.com), convertidos con `tools/`. Hay que mantener estos créditos:

| Modelo | Autor | Licencia |
|---|---|---|
| Game Boy Advance («Game Boy Retreat») | [ForkyForklift](https://sketchfab.com/ForkyForklift) | CC BY 4.0 |
| Game Boy Advance SP («Gameboy Advance SP») | [Smoggybeard](https://sketchfab.com/Smoggybeard) | CC BY 4.0 |
| Nintendo 3DS | [Broeach8972](https://sketchfab.com/Broeach8972) | CC BY 4.0 |
| PSP («Sony PSP») | [Ilya Ostrovsky](https://sketchfab.com/strov) | CC BY 4.0 |
| Nintendo DS («Nintendo DSi Console») | [AlanaSierra](https://sketchfab.com/AlanaSierra) | CC BY 4.0 |
| NES, control de NES, televisor CRT | [A.stark](https://sketchfab.com/a.stark), [donnichols](https://sketchfab.com/donnichols), [aut2](https://sketchfab.com/aut2) | CC BY 4.0 |
| Televisor de plasma | [CN Entertainment](https://sketchfab.com/cn-entertainment) | CC BY 4.0 |
| Nintendo 64 y su control | [Ethanboor](https://sketchfab.com/Ethanboor), [synthetic worlds](https://sketchfab.com/syntheticworlds) | CC BY 4.0 |
| PS2 clásica, PS2 Slim, control de PS2 | [jei3323](https://sketchfab.com/jei3323), [alexdperdomo](https://sketchfab.com/alexdperdomo), [rhcreations](https://sketchfab.com/rhcreations) | CC BY 4.0 |
| PS3 clásica, PS3 Slim, control de PS3 | [jei3323](https://sketchfab.com/jei3323), [Vasily](https://sketchfab.com/3D-Models-Geek), [DarkFire](https://sketchfab.com/vlakas) | CC BY 4.0 |
| Wii | [elouisetrewartha](https://sketchfab.com/etrewartha) | **CC BY-NC 4.0** (no comercial) |
| Wiimote | [Twisty_z](https://sketchfab.com/Twisty_z) | CC BY 4.0 |

Los filtros de pantalla de GBA vienen de los shaders de dominio público de [libretro/glsl-shaders](https://github.com/libretro/glsl-shaders) (`gba-color` y `lcd3x`); el filtro CRT es propio.

### Marcas, juegos y BIOS

Game Boy, Game Boy Advance, Nintendo DS, Nintendo DSi, Nintendo 3DS, NES, Nintendo 64, Wii y Nintendo son marcas de Nintendo; PlayStation y PSP son marcas de Sony Interactive Entertainment. EmuMod no está afiliado a ninguna de las dos. Los juegos, BIOS y firmware tienen copyright de sus dueños: el mod no los incluye ni los descarga; usa solo copias que tengas derecho a usar.
