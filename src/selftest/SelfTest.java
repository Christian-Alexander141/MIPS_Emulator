package selftest;

import CPU.Cristian1604CPU;
import accelerator.ComplexMultiplier;
import assembler.AssembledInstruction;
import assembler.Assembler;
import assembler.AssemblerException;
import assembler.Program;
import bus.BusDevice;
import bus.MemoryMap;
import loader.Loader;
import memory.DataMemory;

import java.util.List;

/**
 * Selbsttest ohne externes Test-Framework: java -cp out selftest.SelfTest
 */
public class SelfTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testEncodingMatchesCristianReadme();
        testDisassemblyRoundTrip();
        testUnsupportedInstructionRejected();
        testAcceleratorProgram();
        testSoftwareReferenceProgram();
        testFixedPoint();

        System.out.println();
        System.out.println(failures == 0 ? "Alle Selbsttests bestanden." : failures + " Selbsttest(s) fehlgeschlagen.");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Hex-Code des Beispiels aus https://github.com/cristian1604/mips (README / Instruction_Memory.vhd). */
    private static void testEncodingMatchesCristianReadme() throws AssemblerException {
        List<String> source = List.of(
                "addi $s1, $0, 6",
                "addi $s2, $0, 4",
                "addi $s0, $0, 0",
                "addi $s3, $0, 0",
                "for:",
                "beq $s0, $s1, continue",
                "  add $s3, $s3, $s2",
                "  addi $s0, $s0, 1",
                "  j for",
                "continue:");
        int[] expected = {0x20110006, 0x20120004, 0x20100000, 0x20130000,
                0x12110003, 0x02729820, 0x22100001, 0x08000004};

        Program program = new Assembler().assemble("readme", source);
        check("Anzahl Befehle README-Beispiel", expected.length, program.size());
        for (int i = 0; i < expected.length && i < program.size(); i++) {
            check("Kodierung Befehl " + i, expected[i], program.getInstructions().get(i).word());
        }
    }

    private static void testDisassemblyRoundTrip() throws AssemblerException {
        Program program = new Assembler().assemble("acc", Loader.readSourceLines("complex_mul_acc.asm"));
        for (AssembledInstruction instruction : program.getInstructions()) {
            // Disassemblat an derselben Adresse erneut assemblieren (vorher passend viele nops)
            List<String> source = new java.util.ArrayList<>();
            for (int a = 0; a < instruction.address(); a += 4) {
                source.add("nop");
            }
            source.add(instruction.disassembly());
            Program again = new Assembler().assemble("roundtrip", source);
            check("Roundtrip '" + instruction.disassembly() + "'", instruction.word(),
                    again.getInstructions().get(again.size() - 1).word());
        }
    }

    private static void testUnsupportedInstructionRejected() {
        try {
            new Assembler().assemble("x", List.of("mult $t0, $t1"));
            fail("mult hätte abgelehnt werden müssen");
        } catch (AssemblerException e) {
            pass("mult wird abgelehnt: " + e.getErrors().get(0));
        }
    }

    private static void testAcceleratorProgram() {
        for (int latency : new int[]{1, 4, 16}) {
            Cristian1604CPU cpu = Loader.createCristianSystem(latency, 0);
            cpu.run("complex_mul_acc", Loader.readSourceLines("complex_mul_acc.asm"));
            check("Beschleuniger-Programm erfolgreich (Latenz " + latency + ")", true, cpu.isSuccessful());
            DataMemory ram = ram(cpu);
            check("Realteil (Latenz " + latency + ")", 23, ram.peek(0));
            check("Imaginärteil (Latenz " + latency + ")", 14, ram.peek(4));
        }
    }

    private static void testSoftwareReferenceProgram() {
        Cristian1604CPU cpu = Loader.createCristianSystem(ComplexMultiplier.DEFAULT_LATENCY, 0);
        cpu.run("complex_mul_sw", Loader.readSourceLines("complex_mul_sw.asm"));
        check("Software-Referenz erfolgreich", true, cpu.isSuccessful());
        DataMemory ram = ram(cpu);
        check("Software Realteil", 23, ram.peek(0));
        check("Software Imaginärteil", 14, ram.peek(4));
    }

    private static void testFixedPoint() {
        // Q15: (0.5 + 0.5j)^2 = 0 + 0.5j
        int half = 1 << 14;
        int[] r = ComplexMultiplier.multiply(half, half, half, half, 15);
        check("Q15 Realteil", 0, r[0]);
        check("Q15 Imaginärteil", half, r[1]);
    }

    private static DataMemory ram(Cristian1604CPU cpu) {
        for (BusDevice device : cpu.getBus().getDevices()) {
            if (device instanceof DataMemory memory && memory.getBaseAddress() == MemoryMap.RAM_BASE) {
                return memory;
            }
        }
        throw new IllegalStateException("Kein RAM am Bus");
    }

    private static void check(String name, Object expected, Object actual) {
        if (expected.equals(actual)) {
            pass(name);
        } else {
            String e = expected instanceof Integer i ? String.format("0x%08x", i) : String.valueOf(expected);
            String a = actual instanceof Integer i ? String.format("0x%08x", i) : String.valueOf(actual);
            fail(name + ": erwartet " + e + ", erhalten " + a);
        }
    }

    private static void pass(String name) {
        System.out.println("[OK]     " + name);
    }

    private static void fail(String message) {
        failures++;
        System.out.println("[FEHLER] " + message);
    }
}
