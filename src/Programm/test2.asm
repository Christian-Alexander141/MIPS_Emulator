# MIPS-Programm zum Testen der wichtigsten Instruktionen

addi $1, $0, 10     # $1 = 10
addi $2, $0, 20     # $2 = 20
add  $3, $1, $2     # $3 = $1 + $2 = 30
sub  $4, $2, $1     # $4 = $2 - $1 = 10

and  $5, $1, $2     # $5 = $1 & $2
or   $6, $1, $2     # $6 = $1 | $2
slt  $7, $1, $2     # $7 = 1 (weil $1 < $2)

# Speicheroperationen
addi $8, $0, 100     # $8 = 100 (Adresse)
sw   $3, 0($8)       # speichere $3 an Adresse 100
lw   $9, 0($8)       # lade aus Adresse 100 in $9

# Bedingter Sprung
beq  $1, $1, 2       # überspringe nächste Instruktion
addi $10, $0, 999    # sollte übersprungen werden
addi $11, $0, 111    # wird ausgeführt

# Unbedingter Sprung
j    16              # springe ans Ende
addi $12, $0, 999    # sollte übersprungen werden

addi $13, $0, 42     # letzter Befehl
