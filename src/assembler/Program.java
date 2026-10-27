package assembler;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Ergebnis des Assemblers: Inhalt des Befehlsspeichers ab {@link Assembler#TEXT_BASE}.
 */
public class Program {

    private final String name;
    private final List<AssembledInstruction> instructions;
    private final Map<String, Integer> labels;

    public Program(String name, List<AssembledInstruction> instructions, Map<String, Integer> labels) {
        this.name = name;
        this.instructions = List.copyOf(instructions);
        this.labels = Collections.unmodifiableMap(labels);
    }

    public String getName() {
        return name;
    }

    public List<AssembledInstruction> getInstructions() {
        return instructions;
    }

    public Map<String, Integer> getLabels() {
        return labels;
    }

    public int size() {
        return instructions.size();
    }

    public int sizeInBytes() {
        return instructions.size() * 4;
    }

    public boolean containsAddress(int address) {
        int offset = address - Assembler.TEXT_BASE;
        return (offset & 3) == 0 && offset >= 0 && offset < sizeInBytes();
    }

    public AssembledInstruction at(int address) {
        return instructions.get((address - Assembler.TEXT_BASE) >>> 2);
    }
}
