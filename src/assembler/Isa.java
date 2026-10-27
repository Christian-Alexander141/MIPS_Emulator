package assembler;

import java.util.HashMap;
import java.util.Map;

/**
 * Befehlssatz des cristian1604/mips-Prozessors (Single-Cycle-MIPS nach Harris &amp; Harris).
 * Nur diese Opcodes (MainDecoder.vhd) und Funct-Codes (ALUdecoder.vhd) werden von der Hardware dekodiert.
 */
public final class Isa {

    private Isa() {
    }

    // Opcodes (instr[31:26]) – siehe MainDecoder.vhd
    public static final int OP_RTYPE = 0x00;
    public static final int OP_J = 0x02;
    public static final int OP_BEQ = 0x04;
    public static final int OP_ADDI = 0x08;
    public static final int OP_LW = 0x23;
    public static final int OP_SW = 0x2B;

    // Funct-Codes (instr[5:0]) – siehe ALUdecoder.vhd
    public static final int FUNCT_ADD = 0x20;
    public static final int FUNCT_SUB = 0x22;
    public static final int FUNCT_AND = 0x24;
    public static final int FUNCT_OR = 0x25;
    public static final int FUNCT_SLT = 0x2A;

    private static final String[] REGISTER_NAMES = {
            "$zero", "$at", "$v0", "$v1", "$a0", "$a1", "$a2", "$a3",
            "$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7",
            "$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7",
            "$t8", "$t9", "$k0", "$k1", "$gp", "$sp", "$fp", "$ra"
    };

    private static final Map<String, Integer> REGISTER_INDEX = new HashMap<>();

    static {
        for (int i = 0; i < REGISTER_NAMES.length; i++) {
            REGISTER_INDEX.put(REGISTER_NAMES[i], i);
            REGISTER_INDEX.put("$" + i, i); // numerische Schreibweise wie im alten Emulator ($5)
        }
    }

    public static String registerName(int index) {
        return REGISTER_NAMES[index];
    }

    /** Liefert den Registerindex oder -1, falls der Name unbekannt ist. */
    public static int registerIndex(String name) {
        return REGISTER_INDEX.getOrDefault(name.trim().toLowerCase(), -1);
    }

    /** Mnemonic eines Maschinenworts (für Statistiken), "?" falls die Hardware es nicht dekodiert. */
    public static String mnemonic(int word) {
        int opcode = word >>> 26;
        switch (opcode) {
            case OP_RTYPE:
                switch (word & 0x3F) {
                    case FUNCT_ADD: return "add";
                    case FUNCT_SUB: return "sub";
                    case FUNCT_AND: return "and";
                    case FUNCT_OR: return "or";
                    case FUNCT_SLT: return "slt";
                    default: return "?";
                }
            case OP_ADDI: return "addi";
            case OP_LW: return "lw";
            case OP_SW: return "sw";
            case OP_BEQ: return "beq";
            case OP_J: return "j";
            default: return "?";
        }
    }

    /**
     * Rückübersetzung eines Maschinenworts in Assembler-Syntax, die der {@link Assembler} wieder einlesen kann
     * (beq mit numerischem Offset in Wörtern, j mit Byte-Adresse).
     */
    public static String disassemble(int word, int pc) {
        int rs = (word >>> 21) & 0x1F;
        int rt = (word >>> 16) & 0x1F;
        int rd = (word >>> 11) & 0x1F;
        int imm = (short) word;
        String m = mnemonic(word);

        switch (m) {
            case "add":
            case "sub":
            case "and":
            case "or":
            case "slt":
                return String.format("%s %s, %s, %s", m, registerName(rd), registerName(rs), registerName(rt));
            case "addi":
                return String.format("addi %s, %s, %d", registerName(rt), registerName(rs), imm);
            case "lw":
            case "sw":
                return String.format("%s %s, %d(%s)", m, registerName(rt), imm, registerName(rs));
            case "beq":
                return String.format("beq %s, %s, %d", registerName(rs), registerName(rt), imm);
            case "j":
                int target = ((pc + 4) & 0xF0000000) | ((word & 0x03FFFFFF) << 2);
                return String.format("j 0x%08x", target);
            default:
                return String.format(".word 0x%08x", word);
        }
    }
}
