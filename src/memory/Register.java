package memory;

public class Register {

    private final int[][] registers = new int[32][32]; // 32 Register, je 32 Bit

    public Register() {
        initAllRegisters();
    }

    private void initAllRegisters() {
        for (int i = 0; i < registers.length; i++) {
            for (int j = 0; j < 32; j++) {
                registers[i][j] = 0;
            }
        }
    }

    // Read-Only Zugriff auf reg0
    public int[] getReg0() {
        return registers[0].clone(); // Kopie zurückgeben → schützt vor Schreibzugriff
    }

    // Getter für beliebiges Register (z. B. $2)
    public int[] get(int index) {
        if (index < 0 || index >= 32) {
            throw new IllegalArgumentException("Ungültiger Registerindex: " + index);
        }

        // Bei reg0: immer Kopie zurückgeben
        if (index == 0) {
            return getReg0();
        }

        return registers[index];
    }

    // Setter (schreibt neuen Wert in ein Register)
    public void set(int index, int[] value) {
        if (index < 0 || index >= 32) {
            throw new IllegalArgumentException("Ungültiger Registerindex: " + index);
        }

        if (value == null || value.length != 32) {
            throw new IllegalArgumentException("Registerwert muss ein int[32] sein");
        }

        if (index == 0) {
            // reg0 ist read-only
            System.out.println("Warnung: Versuch, in Register $0 zu schreiben – ignoriert.");
            return;
        }

        // Kopie übernehmen, um Manipulationen am Original zu vermeiden
        System.arraycopy(value, 0, registers[index], 0, 32);
    }

    // Optional: Methode zum Setzen eines einzelnen Bits
    public void setBit(int regIndex, int bitIndex, int value) {
        if (regIndex == 0) {
            System.out.println("Warnung: Versuch, in Register $0 zu schreiben – ignoriert.");
            return;
        }

        if (regIndex < 0 || regIndex >= 32 || bitIndex < 0 || bitIndex >= 32) {
            throw new IllegalArgumentException("Ungültiger Index");
        }

        registers[regIndex][bitIndex] = value == 0 ? 0 : 1;
    }

    private String bitArrayToString(int[] bits) {
        StringBuilder sb = new StringBuilder();
        for (int bit : bits) {
            sb.append(bit);
        }
        return sb.toString();
    }

    private int bitsToInt(int[] bits) {
        int value = 0;
        for (int i = 0; i < bits.length; i++) {
            value = (value << 1) | bits[i];
        }
        return value;
    }

    public void printAllRegisters() {
        System.out.println("=== Register nach Ausführung ===");
        for (int i = 0; i < 32; i++) {
            int[] reg = get(i);
            int value = bitsToInt(reg);
            String binStr = bitArrayToString(reg);

            // ANSI-Farbe: Grün für Binärwert
            String green = "\u001B[32m";
            String reset = "\u001B[0m";

            System.out.printf(
                    "$%02d: %s%s%s (dez: %d)%n",
                    i,
                    green,
                    binStr,
                    reset,
                    value
            );
        }
    }


}
