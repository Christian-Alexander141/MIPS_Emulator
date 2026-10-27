package export;

import CPU.Cristian1604CPU;
import assembler.AssembledInstruction;
import assembler.Isa;
import assembler.Program;
import bus.BusDevice;
import bus.BusTransaction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Wandelt ein erfolgreich simuliertes Programm in die Formate für den cristian1604-MIPS um.
 */
public final class HexExporter {

    // Instruction_Memory.vhd: type mem is array(0 to 511) of std_logic_vector(7 downto 0)
    public static final int DEFAULT_INSTRUCTION_MEMORY_BYTES = 512;

    private HexExporter() {
    }

    /** Format aus dem cristian1604-README: vier Byte-Tupel pro Befehl, z. B. x"20",x"11",x"00",x"06", */
    public static String toVhdlTuples(Program program) {
        StringBuilder sb = new StringBuilder();
        for (AssembledInstruction instruction : program.getInstructions()) {
            int w = instruction.word();
            sb.append(String.format("x\"%02x\",x\"%02x\",x\"%02x\",x\"%02x\",  -- 0x%04x: %s%n",
                    (w >>> 24) & 0xFF, (w >>> 16) & 0xFF, (w >>> 8) & 0xFF, w & 0xFF,
                    instruction.address(), instruction.disassembly()));
        }
        return sb.toString();
    }

    /** Ein 32-Bit-Wort pro Zeile (wie der Export "Hexadecimal Text" von MARS). */
    public static String toHexWords(Program program) {
        StringBuilder sb = new StringBuilder();
        for (AssembledInstruction instruction : program.getInstructions()) {
            sb.append(String.format("%08x%n", instruction.word()));
        }
        return sb.toString();
    }

    /** Vollständige Instruction_Memory.vhd, die die Datei im cristian1604-Projekt ersetzen kann. */
    public static String toInstructionMemoryVhd(Program program, int memoryBytes) {
        int size = Math.max(memoryBytes, program.sizeInBytes());
        StringBuilder sb = new StringBuilder();
        sb.append("-- Automatisch erzeugt vom MIPS-Emulator – Programm: ").append(program.getName()).append('\n');
        sb.append("-- ").append(program.size()).append(" Befehle, ").append(program.sizeInBytes()).append(" Byte\n");
        sb.append("-- Ersetzt Instruction_Memory.vhd aus https://github.com/cristian1604/mips\n");
        sb.append("-- Originalentwurf: Bottazzi, Cristian - 2017 (https://github.com/cristian1604/)\n\n");
        sb.append("library IEEE;\n");
        sb.append("use IEEE.STD_LOGIC_1164.ALL;\n");
        sb.append("use ieee.std_logic_arith.all;\n");
        sb.append("use ieee.std_logic_unsigned.all;\n\n");
        sb.append("entity Instruction_Memory is\n");
        sb.append("    Port ( dir : in  STD_LOGIC_VECTOR (31 downto 0);\n");
        sb.append("           instr : out  STD_LOGIC_VECTOR (31 downto 0));\n");
        sb.append("end Instruction_Memory;\n\n");
        sb.append("architecture Behavioral of Instruction_Memory is\n");
        sb.append("\ttype mem is array(0 to ").append(size - 1).append(") of std_logic_vector(7 downto 0);\n");
        sb.append("\tconstant code : mem:=(\n\n");
        sb.append("--\t\tQuelltext:\n");
        int lastLine = -1;
        for (AssembledInstruction instruction : program.getInstructions()) {
            if (instruction.lineNumber() != lastLine) {
                sb.append("--\t\t").append(instruction.sourceText()).append('\n');
                lastLine = instruction.lineNumber();
            }
        }
        sb.append('\n');
        for (String line : toVhdlTuples(program).split("\\R")) {
            sb.append("\t\t").append(line).append('\n');
        }
        sb.append("\n\tothers=> x\"00\");\n\n");
        sb.append("begin\n\n");
        sb.append("\tprocess(dir)\n");
        sb.append("\tbegin\n");
        sb.append("\t\tinstr(31 downto 24)<=code(conv_integer(dir));\n");
        sb.append("\t\tinstr(23 downto 16)<=code(conv_integer(dir)+1);\n");
        sb.append("\t\tinstr(15 downto 8)<= code(conv_integer(dir)+2);\n");
        sb.append("\t\tinstr(7 downto 0)<=code(conv_integer(dir)+3);\n");
        sb.append("\tend process;\n\n");
        sb.append("end Behavioral;\n");
        return sb.toString();
    }

    /** Erwartete Buszugriffe – Referenz für den Vergleich mit einer VHDL-Testbench. */
    public static String toBusTrace(List<BusTransaction> transactions) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Takt = Index des ausgeführten Befehls ab Reset (0-basiert), ein Befehl pro Takt\n");
        sb.append("# ").append(BusTransaction.header()).append('\n');
        for (BusTransaction t : transactions) {
            sb.append("  ").append(t).append('\n');
        }
        return sb.toString();
    }

    /** Endzustand (Register, Busgeräte) als Erwartungswert für die Hardware. */
    public static String toResultState(Cristian1604CPU cpu) {
        StringBuilder sb = new StringBuilder();
        sb.append("Programm: ").append(cpu.getProgram().getName()).append('\n');
        sb.append("Takte: ").append(cpu.getCycles()).append("\n\n");
        sb.append("Register (ungleich 0):\n");
        for (int i = 1; i < 32; i++) {
            int value = cpu.getRegisterValue(i);
            if (value != 0) {
                sb.append(String.format("  $%-2d %-5s = 0x%08x (dez: %d)%n", i, Isa.registerName(i), value, value));
            }
        }
        for (BusDevice device : cpu.getBus().getDevices()) {
            sb.append('\n').append(device.getName()).append(":\n").append(device.describeState());
        }
        return sb.toString();
    }

    public static void printToConsole(Program program) {
        System.out.println("=== Hex-Code für Instruction_Memory.vhd (cristian1604-MIPS) ===");
        System.out.print(toVhdlTuples(program));
        if (program.sizeInBytes() > DEFAULT_INSTRUCTION_MEMORY_BYTES) {
            System.out.println("Warnung: Das Programm ist größer als der Befehlsspeicher des cristian1604-MIPS ("
                    + DEFAULT_INSTRUCTION_MEMORY_BYTES + " Byte = 128 Befehle). Die erzeugte Instruction_Memory.vhd "
                    + "wird entsprechend vergrößert.");
        }
        System.out.println("=== Hex-Wörter ===");
        System.out.print(toHexWords(program));
    }

    /** Schreibt alle Formate in das Verzeichnis und liefert die erzeugten Dateien. */
    public static List<Path> exportAll(Cristian1604CPU cpu, Path directory) throws IOException {
        Program program = cpu.getProgram();
        Files.createDirectories(directory);
        String base = program.getName();
        List<Path> files = new ArrayList<>();
        files.add(write(directory.resolve(base + "_Instruction_Memory.vhd"),
                toInstructionMemoryVhd(program, DEFAULT_INSTRUCTION_MEMORY_BYTES)));
        files.add(write(directory.resolve(base + "_vhdl.txt"), toVhdlTuples(program)));
        files.add(write(directory.resolve(base + ".hex"), toHexWords(program)));
        files.add(write(directory.resolve(base + "_bus_trace.txt"), toBusTrace(cpu.getBus().getTransactions())));
        files.add(write(directory.resolve(base + "_ergebnis.txt"), toResultState(cpu)));
        return files;
    }

    private static Path write(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
