package memory;

import bus.BusDevice;

import java.util.Map;
import java.util.TreeMap;

/**
 * Datenspeicher am Bus. Der cristian1604-MIPS enthält selbst keinen Datenspeicher (siehe README dort),
 * er wird extern über address/writedata/readdata/memwrite angeschlossen.
 */
public class DataMemory implements BusDevice {

    private final int baseAddress;
    private final int size;
    private final Map<Integer, Integer> words = new TreeMap<>(); // Schlüssel: Byte-Offset

    public DataMemory(int baseAddress, int size) {
        this.baseAddress = baseAddress;
        this.size = size;
    }

    @Override
    public String getName() {
        return "RAM";
    }

    @Override
    public int getBaseAddress() {
        return baseAddress;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public int read(int offset) {
        return words.getOrDefault(offset, 0);
    }

    @Override
    public void write(int offset, int value) {
        words.put(offset, value);
    }

    /** Lesen ohne Busprotokoll, z. B. für Tests. */
    public int peek(int address) {
        return read(address - baseAddress);
    }

    @Override
    public String describeState() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : words.entrySet()) {
            if (entry.getValue() != 0) {
                sb.append(String.format("  [0x%08x] = 0x%08x (dez: %d)%n",
                        baseAddress + entry.getKey(), entry.getValue(), entry.getValue()));
            }
        }
        return sb.length() == 0 ? "  (alle Wörter 0)" + System.lineSeparator() : sb.toString();
    }
}
