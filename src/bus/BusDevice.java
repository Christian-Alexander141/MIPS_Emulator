package bus;

/**
 * Ein Gerät am Datenbus des cristian1604-MIPS (Ports address, writedata, readdata, memwrite).
 * Zugriffe erfolgen immer wortweise (32 Bit) mit einem Byte-Offset relativ zur Basisadresse.
 */
public interface BusDevice {

    String getName();

    int getBaseAddress();

    /** Größe des Adressbereichs in Byte. */
    int getSize();

    int read(int offset);

    void write(int offset, int value);

    /** Steigende Taktflanke – wird nach jedem ausgeführten Befehl (= ein Takt) aufgerufen. */
    default void tick() {
    }

    /** Lesbare Beschreibung des Zustands für die Ausgabe nach der Simulation. */
    default String describeState() {
        return "";
    }

    default boolean contains(int address) {
        return Integer.compareUnsigned(address - getBaseAddress(), getSize()) < 0;
    }
}
