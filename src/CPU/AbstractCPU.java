package CPU;

import java.util.List;

public abstract class AbstractCPU implements CPU {

    @Override
    public abstract void run(List<String> instructions);

    // Gemeinsame Methoden (optional)

    // Register speichert Werte als int[32] mit dem MSB an Index 0
    protected static int[] intToBits(int value) {
        int[] bits = new int[32];
        for (int i = 31; i >= 0; i--) {
            bits[i] = value & 1;
            value >>= 1;
        }
        return bits;
    }

    protected static int bitsToInt(int[] bits) {
        int value = 0;
        for (int bit : bits) {
            value = (value << 1) | bit;
        }
        return value;
    }
}
