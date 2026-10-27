package bus;

/**
 * Ein protokollierter Buszugriff. Die Liste aller Transaktionen kann als Erwartungswert
 * für eine VHDL-Testbench exportiert werden.
 */
public record BusTransaction(long cycle, int pc, boolean write, int address, int value, String device) {

    @Override
    public String toString() {
        return String.format("%8d  0x%08x  %s  0x%08x  0x%08x  %11d  %s",
                cycle, pc, write ? "W" : "R", address, value, value, device);
    }

    public static String header() {
        return String.format("%8s  %-10s  %s  %-10s  %-10s  %11s  %s",
                "Takt", "PC", "R/W", "Adresse", "Daten", "(dez)", "Gerät");
    }
}
