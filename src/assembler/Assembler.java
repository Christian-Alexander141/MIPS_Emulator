package assembler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zwei-Pass-Assembler für den cristian1604-MIPS.
 * Pass 1: Labels und .eqv-Konstanten sammeln, Adressen vergeben.
 * Pass 2: Befehle in 32-Bit-Maschinenwörter übersetzen.
 *
 * Es werden nur Befehle erzeugt, die die Hardware dekodieren kann (siehe {@link Isa}).
 * Pseudobefehle werden auf diese Befehle abgebildet.
 */
public class Assembler {

    // PC.vhd setzt den PC beim Reset auf 0 – das Textsegment beginnt daher bei 0
    // (MARS verwendet 0x00400000, deshalb kodiert MARS j-Befehle für diese CPU falsch).
    public static final int TEXT_BASE = 0x00000000;

    private static final String SUPPORTED_LIST =
            "add, sub, and, or, slt, addi, lw, sw, beq, j (Pseudo: nop, move, li, b, bne, halt)";

    // MIPS-Befehle, die der MainDecoder/ALUdecoder des cristian1604-MIPS nicht kennt
    private static final Set<String> NOT_SUPPORTED = Set.of(
            "addu", "subu", "addiu", "andi", "ori", "xori", "xor", "nor", "lui", "slti", "sltiu", "sltu",
            "sll", "srl", "sra", "sllv", "srlv", "srav", "mult", "multu", "mul", "div", "divu",
            "mfhi", "mflo", "mthi", "mtlo", "jal", "jr", "jalr", "bgtz", "blez", "bltz", "bgez",
            "lb", "lbu", "lh", "lhu", "sb", "sh", "syscall", "break", "la", "blt", "bgt", "ble", "bge");

    private static final Pattern LABEL = Pattern.compile("^([A-Za-z_][A-Za-z0-9_.]*)\\s*:");
    private static final Pattern MEM_OPERAND = Pattern.compile("^(.*?)\\(\\s*(\\$[A-Za-z0-9]+)\\s*\\)$");
    private static final Pattern TERM = Pattern.compile("([+-]?)([^+-]+)");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");

    private record Statement(int lineNumber, String sourceText, String mnemonic, String[] operands, int address) {
    }

    private final Map<String, Integer> labels = new LinkedHashMap<>();
    private final Map<String, Integer> constants = new HashMap<>();
    private final List<String> errors = new ArrayList<>();

    public Program assemble(String name, List<String> sourceLines) throws AssemblerException {
        labels.clear();
        constants.clear();
        errors.clear();

        List<Statement> statements = firstPass(sourceLines);
        List<AssembledInstruction> instructions = new ArrayList<>();

        for (Statement statement : statements) {
            try {
                encode(statement, instructions);
            } catch (IllegalArgumentException e) {
                errors.add(formatError(statement.lineNumber(), statement.sourceText(), e.getMessage()));
            }
        }

        if (!errors.isEmpty()) {
            throw new AssemblerException(errors);
        }
        return new Program(name, instructions, new LinkedHashMap<>(labels));
    }

    // ------------------------------------------------------------------ Pass 1

    private List<Statement> firstPass(List<String> sourceLines) {
        List<Statement> statements = new ArrayList<>();
        int address = TEXT_BASE;

        for (int i = 0; i < sourceLines.size(); i++) {
            int lineNumber = i + 1;
            String line = stripComment(sourceLines.get(i)).trim();
            String sourceText = line;

            try {
                Matcher m;
                while ((m = LABEL.matcher(line)).find()) {
                    String label = m.group(1);
                    if (labels.containsKey(label)) {
                        throw new IllegalArgumentException("Label '" + label + "' ist doppelt definiert");
                    }
                    labels.put(label, address);
                    line = line.substring(m.end()).trim();
                }
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\s+", 2);
                String mnemonic = parts[0].toLowerCase();
                String rest = parts.length > 1 ? parts[1].trim() : "";

                if (mnemonic.startsWith(".")) {
                    handleDirective(mnemonic, rest);
                    continue;
                }

                statements.add(new Statement(lineNumber, sourceText, mnemonic, splitOperands(rest), address));
                address += 4 * instructionCount(mnemonic);
            } catch (IllegalArgumentException e) {
                errors.add(formatError(lineNumber, sourceText, e.getMessage()));
            }
        }
        return statements;
    }

    private void handleDirective(String directive, String rest) {
        switch (directive) {
            case ".text":
            case ".globl":
                break; // ohne Bedeutung, es gibt nur ein Textsegment
            case ".eqv": {
                // .eqv NAME WERT  (MARS-kompatibel)
                String[] parts = rest.split("[\\s,]+", 2);
                if (parts.length != 2 || !IDENTIFIER.matcher(parts[0]).matches()) {
                    throw new IllegalArgumentException("Syntax: .eqv NAME WERT");
                }
                constants.put(parts[0], parseValue(parts[1]));
                break;
            }
            case ".data":
                throw new IllegalArgumentException(".data wird nicht unterstützt – der Datenspeicher des "
                        + "cristian1604-MIPS liegt außerhalb des Prozessors und wird nicht mit dem Programm geladen. "
                        + "Daten bitte per addi/sw initialisieren.");
            default:
                throw new IllegalArgumentException("Unbekannte Direktive '" + directive + "'");
        }
    }

    private int instructionCount(String mnemonic) {
        // bne wird zu beq + j aufgelöst
        return mnemonic.equals("bne") ? 2 : 1;
    }

    // ------------------------------------------------------------------ Pass 2

    private void encode(Statement s, List<AssembledInstruction> out) {
        String[] ops = s.operands();
        int pc = s.address();

        switch (s.mnemonic()) {
            case "add":
                emit(out, s, pc, rType(Isa.FUNCT_ADD, ops));
                break;
            case "sub":
                emit(out, s, pc, rType(Isa.FUNCT_SUB, ops));
                break;
            case "and":
                emit(out, s, pc, rType(Isa.FUNCT_AND, ops));
                break;
            case "or":
                emit(out, s, pc, rType(Isa.FUNCT_OR, ops));
                break;
            case "slt":
                emit(out, s, pc, rType(Isa.FUNCT_SLT, ops));
                break;
            case "addi":
                expectOperands(ops, 3, "addi rt, rs, imm");
                emit(out, s, pc, iType(Isa.OP_ADDI, reg(ops[1]), reg(ops[0]), imm16(ops[2])));
                break;
            case "lw":
                emit(out, s, pc, memory(Isa.OP_LW, ops));
                break;
            case "sw":
                emit(out, s, pc, memory(Isa.OP_SW, ops));
                break;
            case "beq":
                expectOperands(ops, 3, "beq rs, rt, label");
                emit(out, s, pc, iType(Isa.OP_BEQ, reg(ops[0]), reg(ops[1]), branchOffset(ops[2], pc)));
                break;
            case "j":
                expectOperands(ops, 1, "j label");
                emit(out, s, pc, jType(jumpTarget(ops[0], pc)));
                break;

            // ---- Pseudobefehle (werden auf Hardware-Befehle abgebildet)
            case "nop":
                // 0x00000000 (sll) kennt der ALUdecoder nicht -> nop = add $zero, $zero, $zero
                expectOperands(ops, 0, "nop");
                emit(out, s, pc, rType(Isa.FUNCT_ADD, 0, 0, 0));
                break;
            case "move":
                expectOperands(ops, 2, "move rd, rs");
                emit(out, s, pc, rType(Isa.FUNCT_ADD, reg(ops[0]), reg(ops[1]), 0));
                break;
            case "li":
                expectOperands(ops, 2, "li rt, imm16");
                emit(out, s, pc, iType(Isa.OP_ADDI, 0, reg(ops[0]), imm16(ops[1])));
                break;
            case "b":
                expectOperands(ops, 1, "b label");
                emit(out, s, pc, iType(Isa.OP_BEQ, 0, 0, branchOffset(ops[0], pc)));
                break;
            case "bne":
                // bne rs, rt, L  ->  beq rs, rt, +1 (überspringt j) ; j L
                expectOperands(ops, 3, "bne rs, rt, label");
                emit(out, s, pc, iType(Isa.OP_BEQ, reg(ops[0]), reg(ops[1]), 1));
                emit(out, s, pc + 4, jType(jumpTarget(ops[2], pc + 4)));
                break;
            case "halt":
                // Sprung auf sich selbst: Hardware bleibt stehen, Emulator beendet die Simulation
                expectOperands(ops, 0, "halt");
                emit(out, s, pc, jType(pc));
                break;

            default:
                if (NOT_SUPPORTED.contains(s.mnemonic())) {
                    throw new IllegalArgumentException("'" + s.mnemonic() + "' wird vom cristian1604-MIPS nicht "
                            + "unterstützt. Verfügbar: " + SUPPORTED_LIST);
                }
                throw new IllegalArgumentException("Unbekannter Befehl '" + s.mnemonic() + "'. Verfügbar: "
                        + SUPPORTED_LIST);
        }
    }

    private void emit(List<AssembledInstruction> out, Statement s, int address, int word) {
        out.add(new AssembledInstruction(address, word, s.lineNumber(), s.sourceText(),
                Isa.disassemble(word, address)));
    }

    private int rType(int funct, String[] ops) {
        expectOperands(ops, 3, "rd, rs, rt");
        return rType(funct, reg(ops[0]), reg(ops[1]), reg(ops[2]));
    }

    private int rType(int funct, int rd, int rs, int rt) {
        return (Isa.OP_RTYPE << 26) | (rs << 21) | (rt << 16) | (rd << 11) | funct;
    }

    private int iType(int opcode, int rs, int rt, int imm) {
        return (opcode << 26) | (rs << 21) | (rt << 16) | (imm & 0xFFFF);
    }

    private int jType(int targetAddress) {
        return (Isa.OP_J << 26) | ((targetAddress >>> 2) & 0x03FFFFFF);
    }

    /** lw/sw rt, offset(rs) – oder rt, offset (Basis $zero). */
    private int memory(int opcode, String[] ops) {
        expectOperands(ops, 2, "rt, offset(rs)");
        int rt = reg(ops[0]);
        Matcher m = MEM_OPERAND.matcher(ops[1]);
        if (m.matches()) {
            String offset = m.group(1).trim();
            return iType(opcode, reg(m.group(2)), rt, offset.isEmpty() ? 0 : imm16(offset));
        }
        return iType(opcode, 0, rt, imm16(ops[1]));
    }

    /** beq: Label oder numerischer Offset in Wörtern relativ zu PC+4 (wie in MARS). */
    private int branchOffset(String operand, int pc) {
        int offset;
        if (labels.containsKey(operand)) {
            offset = (labels.get(operand) - (pc + 4)) >> 2;
        } else {
            offset = parseValue(operand);
        }
        if (offset < -32768 || offset > 32767) {
            throw new IllegalArgumentException("Sprungziel '" + operand + "' liegt außerhalb der Reichweite von beq");
        }
        return offset;
    }

    /** j: Label oder Byte-Adresse. Ziel = (PC+4)[31:28] & addr & "00" (DataPath.vhd). */
    private int jumpTarget(String operand, int pc) {
        int target = labels.containsKey(operand) ? labels.get(operand) : parseValue(operand);
        if ((target & 3) != 0) {
            throw new IllegalArgumentException(String.format("Sprungziel 0x%08x ist nicht wortausgerichtet", target));
        }
        if ((target & 0xF0000000) != ((pc + 4) & 0xF0000000)) {
            throw new IllegalArgumentException(String.format("Sprungziel 0x%08x liegt nicht im selben 256-MB-Block", target));
        }
        return target;
    }

    private int reg(String operand) {
        int index = Isa.registerIndex(operand);
        if (index < 0) {
            throw new IllegalArgumentException("Unbekanntes Register '" + operand + "'");
        }
        return index;
    }

    private int imm16(String operand) {
        int value = parseValue(operand);
        if (value < -32768 || value > 32767) {
            throw new IllegalArgumentException("Wert " + value + " passt nicht in 16 Bit mit Vorzeichen "
                    + "(der cristian1604-MIPS hat kein lui/ori für größere Konstanten)");
        }
        return value;
    }

    /** Zahl (dezimal, 0x.., 0b..), .eqv-Konstante oder Label, verknüpft mit + und -. */
    private int parseValue(String expression) {
        String expr = expression.replaceAll("\\s+", "");
        if (expr.isEmpty()) {
            throw new IllegalArgumentException("Fehlender Wert");
        }
        long sum = 0;
        int pos = 0;
        Matcher m = TERM.matcher(expr);
        while (pos < expr.length()) {
            if (!m.find(pos) || m.start() != pos) {
                throw new IllegalArgumentException("Ungültiger Ausdruck '" + expression + "'");
            }
            long term = parseTerm(m.group(2));
            sum += m.group(1).equals("-") ? -term : term;
            pos = m.end();
        }
        if (sum < Integer.MIN_VALUE || sum > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("Wert '" + expression + "' passt nicht in 32 Bit");
        }
        return (int) sum;
    }

    private long parseTerm(String term) {
        if (constants.containsKey(term)) {
            return constants.get(term);
        }
        if (labels.containsKey(term)) {
            return labels.get(term);
        }
        try {
            String lower = term.toLowerCase();
            if (lower.startsWith("0x")) {
                return Long.parseLong(lower.substring(2), 16);
            }
            if (lower.startsWith("0b")) {
                return Long.parseLong(lower.substring(2), 2);
            }
            return Long.parseLong(lower);
        } catch (NumberFormatException e) {
            if (IDENTIFIER.matcher(term).matches()) {
                throw new IllegalArgumentException("Unbekanntes Symbol '" + term + "'");
            }
            throw new IllegalArgumentException("Ungültige Zahl '" + term + "'");
        }
    }

    // ------------------------------------------------------------------ Hilfsmethoden

    private static String stripComment(String line) {
        int commentIndex = line.indexOf('#');
        return commentIndex == -1 ? line : line.substring(0, commentIndex);
    }

    private static String[] splitOperands(String rest) {
        if (rest.isEmpty()) {
            return new String[0];
        }
        String[] ops = rest.split(",");
        for (int i = 0; i < ops.length; i++) {
            ops[i] = ops[i].trim();
        }
        return ops;
    }

    private static void expectOperands(String[] ops, int count, String syntax) {
        if (ops.length != count) {
            throw new IllegalArgumentException("Erwartet " + count + " Operanden: " + syntax);
        }
    }

    private static String formatError(int lineNumber, String source, String message) {
        return "Zeile " + lineNumber + " ('" + source + "'): " + message;
    }
}
