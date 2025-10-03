# Ein kleines MIPS-Programm
# Ziel: Zwei Zahlen vergleichen, das größere in $5 speichern

# Initialisierung
addi $1, $0, 7      # $1 = 7
addi $2, $0, 12     # $2 = 12

# Vergleiche: ist $1 < $2 ?
slt  $3, $1, $2     # $3 = 1, wenn $1 < $2, sonst 0

# Falls $1 < $2, dann springe zu label_groesser
beq  $3, $0, 2      # Wenn $3 == 0, dann $1 >= $2, also überspringe nächste 2 Zeilen

# $2 ist größer
add  $5, $0, $2     # $5 = $2
j    2              # Springe ans Ende

# $1 ist größer oder gleich
add  $5, $0, $1     # $5 = $1

# Speicher und Lese-Test
addi $6, $0, 100    # Adresse = 100
sw   $5, 0($6)      # Speichere $5 bei Adresse 100
lw   $7, 0($6)      # Lade Wert zurück nach $7

# Programmende – $5 enthält größeres Element, $7 = $5
