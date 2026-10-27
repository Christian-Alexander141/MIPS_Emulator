# Komplexe Multiplikation mit dem Beschleuniger am Bus
# (3 + 4j) * (5 - 2j) = 23 + 14j
# Ergebnis: Realteil -> RAM[0x0], Imaginärteil -> RAM[0x4]
# Gleiches Ergebnis wie complex_mul_sw.asm (reine Software)

.eqv ACC_BASE    0x7F00     # Basisadresse des Beschleunigers (bus/MemoryMap.java)
.eqv ACC_A_RE    0x00
.eqv ACC_A_IM    0x04
.eqv ACC_B_RE    0x08
.eqv ACC_B_IM    0x0C
.eqv ACC_CTRL    0x10
.eqv ACC_STATUS  0x14
.eqv ACC_RES_RE  0x18
.eqv ACC_RES_IM  0x1C

        addi $s0, $zero, ACC_BASE   # $s0 = Basisadresse Beschleuniger

        addi $t0, $zero, 3          # a_re
        addi $t1, $zero, 4          # a_im
        addi $t2, $zero, 5          # b_re
        addi $t3, $zero, -2         # b_im

        sw   $t0, ACC_A_RE($s0)     # Operanden über den Bus schreiben
        sw   $t1, ACC_A_IM($s0)
        sw   $t2, ACC_B_RE($s0)
        sw   $t3, ACC_B_IM($s0)

        addi $t4, $zero, 1
        sw   $t4, ACC_CTRL($s0)     # Berechnung starten

poll:   lw   $t5, ACC_STATUS($s0)   # warten bis STATUS = 1
        beq  $t5, $zero, poll

        lw   $s1, ACC_RES_RE($s0)   # Ergebnis abholen
        lw   $s2, ACC_RES_IM($s0)

        sw   $s1, 0($zero)          # Ergebnis im RAM ablegen
        sw   $s2, 4($zero)

        halt                        # = j auf sich selbst
