package CPU;

import assembler.AssembledInstruction;
import assembler.Assembler;
import assembler.AssemblerException;
import assembler.Isa;
import assembler.Program;
import bus.Bus;
import bus.BusDevice;
import bus.BusException;
import bus.BusTransaction;
import memory.Register;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Emulation des Single-Cycle-MIPS von cristian1604 (https://github.com/cristian1604/mips).
 *
 * Ausgeführt werden nicht die Textzeilen, sondern die assemblierten 32-Bit-Maschinenwörter –
 * also genau das, was später als Hex in Instruction_Memory.vhd geladen wird.
 * Ein Befehl entspricht einem Takt; nach jedem Befehl erhalten alle Busgeräte eine Taktflanke.
 */
public class Cristian1604CPU extends AbstractCPU {

    public static final long DEFAULT_MAX_CYCLES = 1_000_000;

    private final Register register = new Register();
    private final Bus bus;
    private final Assembler assembler = new Assembler();

    private Program program;
    private boolean successful;
    private boolean trace;
    private long maxCycles = DEFAULT_MAX_CYCLES;
    private long cycles;
    private boolean endedWithHalt;
    private final Map<String, Long> instructionCounts = new TreeMap<>();
    private final List<String> warnings = new ArrayList<>();

    public Cristian1604CPU(Bus bus) {
        this.bus = bus;
    }

    @Override
    public void run(List<String> instructions) {
        run("programm", instructions);
    }

    public void run(String programName, List<String> sourceLines) {
        System.out.println("cristian1604-MIPS: Assembliere '" + programName + "' ...");
        try {
            program = assembler.assemble(programName, sourceLines);
        } catch (AssemblerException e) {
            System.err.println("Assemblierung fehlgeschlagen:");
            System.err.println(e.getMessage());
            return;
        }
        System.out.println(program.size() + " Befehle (" + program.sizeInBytes() + " Byte) assembliert.");
        System.out.print("Speicherbelegung des Busses:\n" + bus.describeMemoryMap());
        System.out.println("Starte Simulation ...");

        try {
            execute();
            successful = true;
        } catch (SimulationException | BusException e) {
            System.err.println("Simulation abgebrochen nach " + cycles + " Takten: " + e.getMessage());
        }
        printSummary();
    }

    private void execute() {
        int pc = Assembler.TEXT_BASE; // PC.vhd: Reset -> 0
        int loggedTransactions = 0;

        while (true) {
            if (!program.containsAddress(pc)) {
                addWarning(String.format("PC = 0x%08x liegt hinter dem Programmende. Auf der Hardware läuft der PC "
                        + "weiter in den mit x\"00\" gefüllten Befehlsspeicher – Programm mit 'halt' bzw. "
                        + "'ende: j ende' abschließen.", pc));
                return;
            }
            if (cycles >= maxCycles) {
                throw new SimulationException("Zyklenlimit von " + maxCycles + " Takten erreicht (Endlosschleife?)");
            }

            AssembledInstruction current = program.at(pc);
            int nextPc;
            try {
                nextPc = step(pc, current.word());
            } catch (BusException e) {
                throw new SimulationException(String.format("PC = 0x%08x (Zeile %d: %s): %s",
                        pc, current.lineNumber(), current.sourceText(), e.getMessage()));
            }

            if (trace) {
                System.out.printf("[%6d] 0x%08x: %08x  %-28s # Zeile %d%n", cycles, pc, current.word(),
                        current.disassembly(), current.lineNumber());
                List<BusTransaction> log = bus.getTransactions();
                for (; loggedTransactions < log.size(); loggedTransactions++) {
                    BusTransaction t = log.get(loggedTransactions);
                    System.out.printf("           Bus %s 0x%08x = 0x%08x (%s)%n",
                            t.write() ? "W" : "R", t.address(), t.value(), t.device());
                }
            }

            bus.tick(); // steigende Taktflanke
            cycles++;

            if (nextPc == pc) {
                // Sprung auf sich selbst (halt / "ende: j ende") – die Hardware bleibt hier stehen
                endedWithHalt = true;
                return;
            }
            pc = nextPc;
        }
    }

    /** Führt einen Befehl so aus, wie ihn der Datenpfad (DataPath.vhd) in einem Takt verarbeitet. */
    private int step(int pc, int instr) {
        int opcode = instr >>> 26;
        int rs = (instr >>> 21) & 0x1F;
        int rt = (instr >>> 16) & 0x1F;
        int rd = (instr >>> 11) & 0x1F;
        int funct = instr & 0x3F;
        int signImm = (short) instr; // SignExtend.vhd
        int pcPlus4 = pc + 4;
        int nextPc = pcPlus4;

        switch (opcode) {
            case Isa.OP_RTYPE:
                writeReg(rd, alu(funct, readReg(rs), readReg(rt), pc));
                break;
            case Isa.OP_ADDI:
                writeReg(rt, readReg(rs) + signImm);
                break;
            case Isa.OP_LW:
                writeReg(rt, bus.read(readReg(rs) + signImm, cycles, pc));
                break;
            case Isa.OP_SW:
                bus.write(readReg(rs) + signImm, readReg(rt), cycles, pc);
                break;
            case Isa.OP_BEQ:
                // pcsrc = branch and zero; zero kommt aus der ALU-Subtraktion
                if (readReg(rs) - readReg(rt) == 0) {
                    nextPc = pcPlus4 + (signImm << 2);
                }
                break;
            case Isa.OP_J:
                nextPc = (pcPlus4 & 0xF0000000) | ((instr & 0x03FFFFFF) << 2);
                break;
            default:
                throw new SimulationException(String.format("Opcode 0x%02x an PC = 0x%08x wird vom MainDecoder nicht "
                        + "dekodiert", opcode, pc));
        }

        instructionCounts.merge(Isa.mnemonic(instr), 1L, Long::sum);
        return nextPc;
    }

    private int alu(int funct, int a, int b, int pc) {
        switch (funct) {
            case Isa.FUNCT_ADD:
                return a + b; // kein Overflow-Trap in der Hardware (verhält sich wie addu)
            case Isa.FUNCT_SUB:
                return a - b;
            case Isa.FUNCT_AND:
                return a & b;
            case Isa.FUNCT_OR:
                return a | b;
            case Isa.FUNCT_SLT: {
                // ALU.vhd: slt = Vorzeichenbit von (a - b), ohne Überlaufkorrektur
                int hardware = (a - b) >>> 31;
                if (hardware != (a < b ? 1 : 0)) {
                    addWarning(String.format("slt an PC = 0x%08x: Überlauf bei %d - %d, Hardware liefert %d "
                            + "(mathematisch korrekt wäre %d)", pc, a, b, hardware, 1 - hardware));
                }
                return hardware;
            }
            default:
                throw new SimulationException(String.format("Funct-Code 0x%02x an PC = 0x%08x wird vom ALUdecoder "
                        + "nicht dekodiert (aluctrl = \"---\")", funct, pc));
        }
    }

    private void addWarning(String warning) {
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    private int readReg(int index) {
        return bitsToInt(register.get(index));
    }

    private void writeReg(int index, int value) {
        if (index == 0) return; // Register_File.vhd liefert für $0 immer 0
        register.set(index, intToBits(value));
    }

    private void printSummary() {
        register.printAllRegisters();

        System.out.println("=== Statistik ===");
        System.out.println("Takte (= ausgeführte Befehle): " + cycles);
        System.out.println("Befehle: " + instructionCounts);
        System.out.println("Buszugriffe: " + bus.getTransactions().size());
        if (successful) {
            System.out.println("Programmende: " + (endedWithHalt ? "halt (Sprung auf sich selbst)" : "PC hinter letztem Befehl"));
        }

        for (BusDevice device : bus.getDevices()) {
            System.out.println("=== " + device.getName() + " ===");
            System.out.print(device.describeState());
        }

        for (String warning : warnings) {
            System.out.println("Warnung: " + warning);
        }
        System.out.println(successful ? "Simulation erfolgreich." : "Simulation NICHT erfolgreich.");
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    public void setMaxCycles(long maxCycles) {
        this.maxCycles = maxCycles;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public Program getProgram() {
        return program;
    }

    public Bus getBus() {
        return bus;
    }

    public long getCycles() {
        return cycles;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public int getRegisterValue(int index) {
        return readReg(index);
    }
}
