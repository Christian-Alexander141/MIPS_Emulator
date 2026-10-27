package bus;

/**
 * Speicherbelegung des Systems (cristian1604-MIPS + Datenspeicher + Beschleuniger).
 *
 * Der cristian1604-MIPS kennt weder lui noch ori, Adressen müssen daher mit einem 16-Bit-Immediate
 * (vorzeichenbehaftet) erreichbar sein: 0x00000000 - 0x00007FFF bzw. über $zero als Basis.
 */
public final class MemoryMap {

    private MemoryMap() {
    }

    /** Datenspeicher: 4 KiB = 1024 Wörter, Auswahl in Hardware über address(31 downto 12) = 0. */
    public static final int RAM_BASE = 0x00000000;
    public static final int RAM_SIZE = 0x00001000;

    /** Beschleuniger: 8 Register, Auswahl über address(31 downto 5) = 0x3F8, Register über address(4 downto 2). */
    public static final int ACC_BASE = 0x00007F00;
    public static final int ACC_SIZE = 0x00000020;
}
