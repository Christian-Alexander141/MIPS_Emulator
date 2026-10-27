# Software-Referenz ohne Beschleuniger: (3 + 4j) * (5 - 2j) = 23 + 14j
# Der cristian1604-MIPS hat keinen mult-Befehl – jede Teilmultiplikation
# wird als wiederholte Addition berechnet (vgl. Beispiel im cristian1604-README).
# Ergebnis: Realteil -> RAM[0x0], Imaginärteil -> RAM[0x4]
# Zum Vergleich der Taktzahl mit complex_mul_acc.asm

        addi $s0, $zero, 3          # a_re
        addi $s1, $zero, 4          # a_im
        addi $s2, $zero, 5          # b_re
        addi $s3, $zero, -2         # b_im

# --- $s4 = a_re * b_re ---
        add  $t0, $s0, $zero        # Multiplikand
        add  $t1, $s2, $zero        # Multiplikator = Schleifenzähler
        add  $s4, $zero, $zero      # Produkt = 0
        slt  $t2, $t1, $zero        # Multiplikator negativ?
        beq  $t2, $zero, m1_loop
        sub  $t1, $zero, $t1        # dann beide Vorzeichen umdrehen
        sub  $t0, $zero, $t0
m1_loop:
        beq  $t1, $zero, m1_done
        add  $s4, $s4, $t0
        addi $t1, $t1, -1
        j    m1_loop
m1_done:

# --- $s5 = a_im * b_im ---
        add  $t0, $s1, $zero
        add  $t1, $s3, $zero
        add  $s5, $zero, $zero
        slt  $t2, $t1, $zero
        beq  $t2, $zero, m2_loop
        sub  $t1, $zero, $t1
        sub  $t0, $zero, $t0
m2_loop:
        beq  $t1, $zero, m2_done
        add  $s5, $s5, $t0
        addi $t1, $t1, -1
        j    m2_loop
m2_done:

# --- $s6 = a_re * b_im ---
        add  $t0, $s0, $zero
        add  $t1, $s3, $zero
        add  $s6, $zero, $zero
        slt  $t2, $t1, $zero
        beq  $t2, $zero, m3_loop
        sub  $t1, $zero, $t1
        sub  $t0, $zero, $t0
m3_loop:
        beq  $t1, $zero, m3_done
        add  $s6, $s6, $t0
        addi $t1, $t1, -1
        j    m3_loop
m3_done:

# --- $s7 = a_im * b_re ---
        add  $t0, $s1, $zero
        add  $t1, $s2, $zero
        add  $s7, $zero, $zero
        slt  $t2, $t1, $zero
        beq  $t2, $zero, m4_loop
        sub  $t1, $zero, $t1
        sub  $t0, $zero, $t0
m4_loop:
        beq  $t1, $zero, m4_done
        add  $s7, $s7, $t0
        addi $t1, $t1, -1
        j    m4_loop
m4_done:

        sub  $t3, $s4, $s5          # Realteil     = a_re*b_re - a_im*b_im
        add  $t4, $s6, $s7          # Imaginärteil = a_re*b_im + a_im*b_re
        sw   $t3, 0($zero)
        sw   $t4, 4($zero)

ende:   j    ende
