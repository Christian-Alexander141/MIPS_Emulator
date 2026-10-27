# Beispiel aus dem README von cristian1604/mips: 6 x 4 durch wiederholte Addition
# Ergebnis steht in $s3 (= 24)
# Ergänzt um 'halt' am Ende, damit die Hardware nicht in den leeren Befehlsspeicher läuft.

addi $s1, $0, 6
addi $s2, $0, 4
# Set counters
addi $s0, $0, 0
addi $s3, $0, 0
for:
beq $s0, $s1, continue
  add $s3, $s3, $s2
  addi $s0, $s0, 1
  j for
continue:
  halt
