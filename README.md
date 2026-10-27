Das Ziel dieses Projekts ist es, einen einfachen MIPS-Emulator in Java zu entwickeln. „Einfach“ bedeutet in diesem Fall,
möglichst schnell eine funktionsfähige Emulation umzusetzen – mit dem Kompromiss,
dass dieser Emulator vermutlich keinen komplexeren MIPS-Code ausführen können wird.

Geplant ist, den Emulator als Unterstützung beim Erlernen der MIPS-Assemblersprache zu nutzen,
beispielsweise im Rahmen von Einführungsveranstaltungen oder Erstsemestervorlesungen so wie im Rahmen 
von Nachilfe aller Art.

Als Vorbild dient der MARS-Emulator, der ebenfalls in Java geschrieben wurde,
jedoch das Verhalten eines MIPS-Prozessors wesentlich präziser abbildet als meine Lösung.

Der Vorteil meines Ansatzes liegt meiner Ansicht nach darin, dass sich der Emulator auch für Anfänger*innen mit begrenzten 
Java- oder allgemeinen Programmierkenntnissen relativ schnell erschließt.

---

# Erweiterung: Referenzmodell für den cristian1604-MIPS mit Beschleuniger

Im Rahmen der Bachelorarbeit wird ein Beschleuniger für die Multiplikation komplexer Zahlen entwickelt,
der über einen Bus an den Single-Cycle-Prozessor [cristian1604/mips](https://github.com/cristian1604/mips)
(VHDL) angebunden wird. Der Emulator dient dabei als **Referenzmodell**:

1. MIPS-Programm schreiben (`.asm`)
2. Programm im Emulator assemblieren und **auf Maschinencode-Ebene** simulieren – mit Bus, Datenspeicher und
   einem Verhaltensmodell des Beschleunigers
3. **Nur bei erfolgreicher Simulation** wird der Hex-Code ausgegeben und als Dateien exportiert, u. a. als fertige
   `Instruction_Memory.vhd` für den cristian1604-MIPS
4. Zusätzlich werden die erwarteten Buszugriffe und der Endzustand exportiert, um eine VHDL-Testbench damit zu vergleichen

Der ursprüngliche Lern-Emulator (`simpleCPU`) bleibt **unverändert** erhalten und ist weiterhin als Menüpunkt 1 wählbar.

Eine ausführliche Softwaredokumentation mit Grafiken und einer genauen Abgrenzung zwischen der händischen
Implementierung von 2018 und der Erweiterung von 2026 liegt unter
[`Doku/Softwaredokumentation.html`](Doku/Softwaredokumentation.html) bzw. als PDF unter
[`Doku/Softwaredokumentation.pdf`](Doku/Softwaredokumentation.pdf).

## Schnellstart

**IntelliJ:** `Main` starten und im Menü `2` wählen, dann z. B. `complex_mul_acc.asm` eingeben.

**Kommandozeile:**

```bash
javac -encoding UTF-8 -d out $(find src -name '*.java')

# interaktiv (wie bisher)
java -cp out Main

# ohne Rückfragen: simulieren, Hex ausgeben, Dateien nach export/ schreiben
java -cp out Main complex_mul_acc.asm
java -cp out Main complex_mul_acc.asm --latency 8 --trace
java -cp out Main complex_mul_sw.asm --no-export

# Selbsttest
java -cp out selftest.SelfTest
```

Optionen im Kommandozeilenmodus:

| Option | Bedeutung | Standard |
|---|---|---|
| `--cpu simple\|cristian` | alte Lern-CPU oder cristian1604-MIPS | `cristian` |
| `--latency N` | Takte, bis der Beschleuniger fertig ist | `4` |
| `--frac N` | Nachkommabits der Festkommadarstellung (0 = Ganzzahl) | `0` |
| `--max-cycles N` | Abbruch nach N Takten (Schutz vor Endlosschleifen) | `1000000` |
| `--trace` | jeden Befehl und jeden Buszugriff ausgeben | aus |
| `--export DIR` | Zielverzeichnis für die Hex-Dateien | `export` |
| `--no-export` | Hex nur auf der Konsole ausgeben | – |

Der Exit-Code ist `0` bei erfolgreicher Simulation, sonst `1` (für Skripte/CI).

Die `.asm`-Datei wird zuerst wie bisher im Klassenpfad unter `Programm/` gesucht, danach im Dateisystem
(Pfad wie angegeben, dann `src/Programm/`).

## Systemaufbau

```
                 cristian1604-MIPS (Single-Cycle)
 ┌────────────────────────────────────────────────────┐
 │ PC → Instruction_Memory (512 Byte, Hex aus Export)  │
 │ Register_File, ALU, ControlUnit                     │
 └──────┬──────────┬──────────┬──────────┬────────────┘
     address   writedata   memwrite   readdata
        │          │          │          ▲
 ═══════╧══════════╧══════════╧══════════╪═════  Bus (Adressdekodierung, Lese-Mux)
        │                                │
   ┌────┴─────────────┐        ┌─────────┴──────────────┐
   │ RAM              │        │ CMUL – Beschleuniger    │
   │ 0x0000 – 0x0FFF  │        │ 0x7F00 – 0x7F1F         │
   └──────────────────┘        └────────────────────────┘
```

Der cristian1604-MIPS enthält selbst keinen Datenspeicher, sondern führt die Ports `address`, `writedata`,
`readdata` und `memwrite` nach außen. Genau dort sitzt im Emulator der `Bus`, an dem Datenspeicher und
Beschleuniger als `BusDevice` hängen.

### Speicherbelegung (`src/bus/MemoryMap.java`)

| Bereich | Gerät | Dekodierung in Hardware (Vorschlag) |
|---|---|---|
| `0x00000000 – 0x00000FFF` | RAM, 4 KiB (1024 Wörter) | `address(31 downto 12) = 0` |
| `0x00007F00 – 0x00007F1F` | CMUL, 8 Register | `address(31 downto 5) = 0x3F8`, Register = `address(4 downto 2)` |

Der Beschleuniger liegt bewusst unterhalb von `0x8000`: Der cristian1604-MIPS hat weder `lui` noch `ori`,
Adressen müssen deshalb mit einem 16-Bit-Immediate (mit Vorzeichen) erreichbar sein
(`addi $s0, $zero, 0x7F00`). Die Werte lassen sich in `MemoryMap` anpassen.

Zugriffe sind nur wortweise möglich. Nicht ausgerichtete Adressen oder Adressen ohne Gerät führen zum
Abbruch der Simulation (→ kein Hex-Export).

### Registerbelegung des Beschleunigers (`src/accelerator/ComplexMultiplier.java`)

Berechnet `(a_re + j·a_im) · (b_re + j·b_im) = (a_re·b_re − a_im·b_im) + j·(a_re·b_im + a_im·b_re)`.

| Offset | Name | Zugriff | Bedeutung |
|---|---|---|---|
| `0x00` | `A_RE` | R/W | Realteil Operand A |
| `0x04` | `A_IM` | R/W | Imaginärteil Operand A |
| `0x08` | `B_RE` | R/W | Realteil Operand B |
| `0x0C` | `B_IM` | R/W | Imaginärteil Operand B |
| `0x10` | `CTRL` | W | Bit 0 = 1 startet die Berechnung (liest 0) |
| `0x14` | `STATUS` | R | `1` = Ergebnis gültig, sonst `0` |
| `0x18` | `RES_RE` | R | Realteil Ergebnis |
| `0x1C` | `RES_IM` | R | Imaginärteil Ergebnis |

Verhalten:

- **Start:** Schreiben von `1` auf `CTRL` übernimmt die Operanden, setzt `STATUS` auf 0 und startet die Berechnung.
  Ein erneuter Start während einer laufenden Berechnung startet neu.
- **Latenz:** Nach `latency` Taktflanken (Standard 4, einstellbar) stehen `RES_RE/RES_IM` bereit und `STATUS = 1`.
  Bei Latenz 1 kann das Ergebnis bereits mit dem nächsten Befehl gelesen werden.
- **Zahlenformat:** vorzeichenbehaftete Festkommazahlen mit `fracBits` Nachkommabits (0 = Ganzzahl, 15 = Q15 …).
  Die Produkte werden in voller Breite gebildet, addiert/subtrahiert, arithmetisch um `fracBits` nach rechts
  geschoben (Abrundung) und auf die unteren 32 Bit abgeschnitten (kein Sättigen).
- **STATUS ist ein einzelnes Bit**, weil der cristian1604-MIPS nur `beq` kennt – die Warteschleife lautet:
  ```asm
  poll: lw   $t5, 0x14($s0)
        beq  $t5, $zero, poll
  ```
- **Lesen hat keine Seiteneffekte.** Der cristian1604-MIPS besitzt *kein* `MemRead`-Signal; am `address`-Port
  liegt bei *jedem* Befehl das ALU-Ergebnis an. Ein Hardware-Beschleuniger darf deshalb beim Lesen keinen Zustand
  ändern (z. B. kein „DONE löschen beim Lesen“) – sonst würden beliebige Befehle, deren ALU-Ergebnis zufällig im
  Adressbereich liegt, den Zustand verändern. Schreibzugriffe sind über `memwrite` eindeutig.
- Schreiben auf nur lesbare Register bricht die Simulation ab (hilft beim Finden von Programmfehlern).

Das Verhaltensmodell ist das „Golden Model“ für die VHDL-Implementierung; `ComplexMultiplier.multiply(...)` kann
auch direkt zur Berechnung von Erwartungswerten genutzt werden.

### Timing-Modell

- Ein Befehl = ein Takt (Single-Cycle). Lesezugriffe sehen den Zustand vor der Taktflanke.
- Nach jedem Befehl erhalten alle Busgeräte eine Taktflanke (`tick()`).
- Der Takt im Bus-Trace ist der 0-basierte Index des ausgeführten Befehls ab Reset und lässt sich direkt mit
  einer Testbench vergleichen (Takt *n* = *n*-te steigende Flanke nach Reset).

## Assembler für den cristian1604-MIPS (`src/assembler/`)

Die neue CPU arbeitet nicht mehr auf Textzeilen, sondern assembliert das Programm zuerst in 32-Bit-Maschinenwörter
und führt diese aus – exakt so, wie der Datenpfad (`DataPath.vhd`) sie dekodiert. Damit ist sichergestellt, dass
der exportierte Hex-Code genau dem simulierten Programm entspricht.

### Unterstützte Befehle

Nur was `MainDecoder.vhd` und `ALUdecoder.vhd` dekodieren:

| Befehl | Format | Opcode / Funct |
|---|---|---|
| `add rd, rs, rt` | R | `0x00` / `0x20` |
| `sub rd, rs, rt` | R | `0x00` / `0x22` |
| `and rd, rs, rt` | R | `0x00` / `0x24` |
| `or rd, rs, rt` | R | `0x00` / `0x25` |
| `slt rd, rs, rt` | R | `0x00` / `0x2A` |
| `addi rt, rs, imm` | I | `0x08` |
| `lw rt, off(rs)` | I | `0x23` |
| `sw rt, off(rs)` | I | `0x2B` |
| `beq rs, rt, label` | I | `0x04` |
| `j label` | J | `0x02` |

Pseudobefehle (werden auf die obigen Befehle abgebildet):

| Pseudo | wird zu |
|---|---|
| `nop` | `add $zero, $zero, $zero` (`0x00000020`; `0x00000000` kennt der ALUdecoder nicht) |
| `move rd, rs` | `add rd, rs, $zero` |
| `li rt, imm16` | `addi rt, $zero, imm16` |
| `b label` | `beq $zero, $zero, label` |
| `bne rs, rt, label` | `beq rs, rt, 1` + `j label` (2 Befehle) |
| `halt` | `j` auf sich selbst |

Andere MIPS-Befehle (`mult`, `lui`, `ori`, `sll`, `jal`, `jr`, …) werden mit einer Fehlermeldung abgelehnt.

### Syntax

- Kommentare mit `#`, Labels `name:` (auch vor einem Befehl in derselben Zeile)
- Register numerisch (`$5`) oder symbolisch (`$t0`, `$s1`, `$zero`, …)
- Zahlen dezimal, `0x…` oder `0b…`; Ausdrücke mit `+`/`-` (z. B. `ACC_BASE+4`)
- `.eqv NAME WERT` für Konstanten (MARS-kompatibel), `.text`/`.globl` werden ignoriert
- `.data` wird nicht unterstützt (der Datenspeicher ist extern und wird nicht mit dem Programm geladen)
- `beq`-Offset: Label oder Zahl in **Wörtern relativ zu PC+4** (wie bei echtem MIPS/MARS)
- `j`-Ziel: Label oder **Byte-Adresse**
- Immediates müssen in 16 Bit mit Vorzeichen passen (`-32768 … 32767`)
- Alle Fehler werden mit Zeilennummer gesammelt ausgegeben

### Programmende

Die Hardware hat keinen Halt-Befehl. Programme sollten mit `halt` bzw. `ende: j ende` enden – der Emulator
erkennt den Sprung auf sich selbst als Programmende. Läuft der PC hinter den letzten Befehl, endet die Simulation
ebenfalls, aber mit einer Warnung: Auf der Hardware würde der PC in den mit `x"00"` gefüllten Speicher laufen.

### Hardware-getreue Details

Der Emulator bildet bewusst das Verhalten der VHDL-Beschreibung nach, auch wo es von „echtem“ MIPS abweicht:

- **Textsegment beginnt bei `0x00000000`** (Reset-Wert in `PC.vhd`). MARS verwendet `0x00400000`, deshalb
  kodiert MARS `j`-Befehle für diese CPU falsch (`x"08",x"10"` statt `x"08",x"00"`, siehe README von cristian1604).
  Der Emulator erzeugt direkt den richtigen Code.
- **`slt`** liefert das Vorzeichenbit von `a − b` *ohne* Überlaufkorrektur (`ALU.vhd`). Bei einem Überlauf weicht
  das Ergebnis vom mathematisch richtigen Wert ab – der Emulator rechnet wie die Hardware und gibt eine Warnung aus.
- **`add`/`sub`/`addi`** lösen bei Überlauf keine Exception aus (Verhalten wie `addu`).
- **`$zero`** wird beim Lesen immer als 0 geliefert (`Register_File.vhd`), Schreibzugriffe darauf werden ignoriert.
- Unbekannte Opcodes/Funct-Codes (in VHDL `"---"`) führen zum Abbruch der Simulation.

## Hex-Ausgabe und Export (`src/export/HexExporter.java`)

Nach erfolgreicher Simulation wird der Code auf der Konsole ausgegeben und (Kommandozeile: standardmäßig,
interaktiv: nach Rückfrage) in das Verzeichnis `export/` geschrieben:

| Datei | Inhalt |
|---|---|
| `<name>_Instruction_Memory.vhd` | vollständige `Instruction_Memory.vhd` mit dem Programm – ersetzt die Datei im cristian1604-Projekt |
| `<name>_vhdl.txt` | nur die Byte-Tupel zum Einfügen, z. B. `x"20",x"11",x"00",x"06",  -- 0x0000: addi $s1, $zero, 6` |
| `<name>.hex` | ein 32-Bit-Wort pro Zeile (wie „Hexadecimal Text“ in MARS, z. B. für `$readmemh`) |
| `<name>_bus_trace.txt` | alle erwarteten Buszugriffe mit Takt, PC, R/W, Adresse und Daten |
| `<name>_ergebnis.txt` | Endzustand: Taktzahl, Register ≠ 0, RAM-Inhalt, Beschleuniger-Zustand |

Ist das Programm größer als 512 Byte (128 Befehle), wird gewarnt und das Array in der erzeugten
`Instruction_Memory.vhd` entsprechend vergrößert.

Bei Assemblierungsfehlern, Bus-Fehlern, nicht dekodierbaren Befehlen oder Erreichen des Zyklenlimits gilt die
Simulation als nicht erfolgreich – dann wird **kein** Hex-Code ausgegeben.

## Beispielprogramme (`src/Programm/`)

| Datei | Inhalt | Takte |
|---|---|---|
| `complex_mul_acc.asm` | (3 + 4j)·(5 − 2j) mit dem Beschleuniger, Ergebnis 23 + 14j in RAM[0], RAM[4] | 22 (Latenz 4) |
| `complex_mul_sw.asm` | dasselbe rein in Software (Multiplikation durch wiederholte Addition, da kein `mult`) | 93 |
| `cristian_beispiel.asm` | Beispiel 6·4 aus dem cristian1604-README, ergänzt um `halt` | 30 |

Die Taktzahl der Software-Variante hängt von den Operanden ab, die der Beschleuniger-Variante nur von der Latenz.
Damit lässt sich die Beschleunigung direkt im Emulator vergleichen.

## Selbsttest (`src/selftest/SelfTest.java`)

`java -cp out selftest.SelfTest` prüft ohne externes Test-Framework:

- Kodierung des Beispiels aus dem cristian1604-README Byte für Byte gegen den dort angegebenen Hex-Code
- Disassemblieren → erneut Assemblieren ergibt dasselbe Maschinenwort
- Ablehnung nicht unterstützter Befehle
- `complex_mul_acc.asm` mit Latenz 1, 4 und 16 sowie `complex_mul_sw.asm` liefern 23 + 14j
- Festkomma-Berechnung im Q15-Format

## Änderungen gegenüber dem alten System

### Neue Dateien

| Datei | Zweck |
|---|---|
| `src/CPU/Cristian1604CPU.java` | neue CPU: führt Maschinenwörter wie der cristian1604-Datenpfad aus, ein Befehl pro Takt, Trace, Statistik |
| `src/CPU/SimulationException.java` | Fehler während der Simulation |
| `src/assembler/Assembler.java` | Zwei-Pass-Assembler mit Labels, Registernamen, `.eqv`, Pseudobefehlen |
| `src/assembler/Isa.java` | Opcodes/Funct-Codes der Hardware, Registernamen, Disassembler |
| `src/assembler/Program.java`, `AssembledInstruction.java`, `AssemblerException.java` | Ergebnis und Fehler des Assemblers |
| `src/bus/Bus.java` | Datenbus: Adressdekodierung, Weiterleitung, Protokoll, Taktverteilung |
| `src/bus/BusDevice.java` | Schnittstelle für Geräte am Bus |
| `src/bus/BusTransaction.java`, `BusException.java` | protokollierter Buszugriff, Busfehler |
| `src/bus/MemoryMap.java` | Speicherbelegung |
| `src/memory/DataMemory.java` | Datenspeicher als Busgerät |
| `src/accelerator/ComplexMultiplier.java` | Verhaltensmodell des Beschleunigers |
| `src/export/HexExporter.java` | Hex-Ausgabe und Export-Dateien |
| `src/selftest/SelfTest.java` | Selbsttest |
| `src/Programm/complex_mul_acc.asm`, `complex_mul_sw.asm`, `cristian_beispiel.asm` | Beispielprogramme |

### Geänderte Dateien

| Datei | Änderung |
|---|---|
| `src/loader/Loader.java` | Menüpunkt 2 („CPU mit Coprozessor – noch nicht implementiert“) startet jetzt den cristian1604-MIPS mit Bus und Beschleuniger; Abfrage von Latenz und Nachkommabits; Hex-Ausgabe und Export nach erfolgreicher Simulation; neuer Kommandozeilenmodus `runBatch`; Dateien werden zusätzlich im Dateisystem gesucht; Einlesen und Kommentarentfernung in eigene Methoden ausgelagert (für die Standard-CPU unverändertes Verhalten) |
| `src/Main.java` | mit Programmargumenten wird der Kommandozeilenmodus gestartet, ohne Argumente wie bisher das Menü |
| `src/CPU/AbstractCPU.java` | gemeinsame Hilfsmethoden `intToBits`/`bitsToInt` (von der neuen CPU genutzt) |
| `src/memory/Register.java` | Registerausgabe zeigt zusätzlich den Hex-Wert |

### Unverändert

`src/CPU/simpleCPU.java`, `src/CPU/CPU.java`, `src/memory/specialproposeReg.java` und die alten Programme
(`test.asm`, `test2.asm`, `kleine.asm`, `testit.ams`).

### Unterschiede zwischen alter Standard-CPU und cristian1604-CPU

| | `simpleCPU` (Menüpunkt 1) | `Cristian1604CPU` (Menüpunkt 2) |
|---|---|---|
| Ausführung | interpretiert Textzeilen | assembliert in Maschinencode und führt diesen aus |
| PC | Zeilenindex (0, 1, 2, …) | Byte-Adresse (0, 4, 8, …) |
| `beq`/`bne`-Offset | Sprung um *n* Zeilen ab der aktuellen Zeile | *n* Wörter relativ zu PC+4 (echtes MIPS) |
| `j`-Ziel | Zeilenindex | Byte-Adresse bzw. Label |
| Labels | nein | ja |
| Registernamen | nur `$0` … `$31` | zusätzlich `$t0`, `$s0`, `$zero`, … |
| `bne` | nativ | Pseudobefehl (`beq` + `j`), da nicht in Hardware |
| Speicher | `HashMap` in der CPU | Bus mit RAM und Beschleuniger |
| Fehler | Meldung, Ausführung läuft weiter | Abbruch, kein Hex-Export |
| Ausgabe | Register | Register, Statistik, Busgeräte, Hex-Code, Export-Dateien |

Die alten Programme sind daher **nicht** ohne Anpassung auf der neuen CPU lauffähig, da sich die Bedeutung der
numerischen Sprungziele unterscheidet. Für neue Programme empfiehlt sich, Sprünge immer über Labels zu schreiben.

## Lizenzhinweis

Die erzeugte `Instruction_Memory.vhd` folgt dem Aufbau der gleichnamigen Datei aus
[cristian1604/mips](https://github.com/cristian1604/mips) (Cristian Bottazzi, GPL-3.0). Die Quelle ist im Kopf
der erzeugten Datei angegeben.
