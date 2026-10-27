package accelerator;

import bus.BusDevice;
import bus.BusException;

/**
 * Verhaltensmodell (Golden Model) des Beschleunigers für die Multiplikation komplexer Zahlen:
 *
 *   (a_re + j*a_im) * (b_re + j*b_im) = (a_re*b_re - a_im*b_im) + j*(a_re*b_im + a_im*b_re)
 *
 * Registerbelegung (Byte-Offset zur Basisadresse, je 32 Bit):
 *   0x00 A_RE    R/W  Realteil Operand A
 *   0x04 A_IM    R/W  Imaginärteil Operand A
 *   0x08 B_RE    R/W  Realteil Operand B
 *   0x0C B_IM    R/W  Imaginärteil Operand B
 *   0x10 CTRL    W    Bit 0 = 1 startet die Berechnung (liest 0)
 *   0x14 STATUS  R    1 = Ergebnis gültig (DONE), sonst 0
 *   0x18 RES_RE  R    Realteil Ergebnis
 *   0x1C RES_IM  R    Imaginärteil Ergebnis
 *
 * Zahlenformat: vorzeichenbehaftete Festkommazahlen mit {@code fracBits} Nachkommabits (0 = Ganzzahl).
 * Produkte werden mit voller Breite gebildet, summiert, arithmetisch um fracBits nach rechts geschoben
 * und auf die unteren 32 Bit abgeschnitten.
 */
public class ComplexMultiplier implements BusDevice {

    public static final int REG_A_RE = 0x00;
    public static final int REG_A_IM = 0x04;
    public static final int REG_B_RE = 0x08;
    public static final int REG_B_IM = 0x0C;
    public static final int REG_CTRL = 0x10;
    public static final int REG_STATUS = 0x14;
    public static final int REG_RES_RE = 0x18;
    public static final int REG_RES_IM = 0x1C;

    public static final int CTRL_START = 0x1;
    public static final int DEFAULT_LATENCY = 4;

    private final int baseAddress;
    private final int latency;
    private final int fracBits;

    private int aRe, aIm, bRe, bIm;
    private int resRe, resIm;
    private int pendingRe, pendingIm;
    private int remainingCycles;
    private boolean done;

    // Statistik
    private long starts;
    private long busyCycles;

    /**
     * @param latency  Takte vom Schreiben auf CTRL bis STATUS = 1 (mindestens 1)
     * @param fracBits Nachkommabits der Festkommadarstellung (0 = Ganzzahl)
     */
    public ComplexMultiplier(int baseAddress, int latency, int fracBits) {
        if (latency < 1) {
            throw new IllegalArgumentException("Latenz muss mindestens 1 Takt sein");
        }
        if (fracBits < 0 || fracBits > 31) {
            throw new IllegalArgumentException("Nachkommabits müssen zwischen 0 und 31 liegen");
        }
        this.baseAddress = baseAddress;
        this.latency = latency;
        this.fracBits = fracBits;
    }

    /** Referenzberechnung – liefert {Realteil, Imaginärteil}. */
    public static int[] multiply(int aRe, int aIm, int bRe, int bIm, int fracBits) {
        long re = ((long) aRe * bRe - (long) aIm * bIm) >> fracBits;
        long im = ((long) aRe * bIm + (long) aIm * bRe) >> fracBits;
        return new int[]{(int) re, (int) im};
    }

    @Override
    public String getName() {
        return "CMUL";
    }

    @Override
    public int getBaseAddress() {
        return baseAddress;
    }

    @Override
    public int getSize() {
        return 0x20;
    }

    @Override
    public int read(int offset) {
        // Lesezugriffe haben bewusst keine Seiteneffekte: der cristian1604-MIPS hat kein MemRead-Signal,
        // die Adresse liegt bei jedem Befehl (ALU-Ergebnis) am Bus an.
        switch (offset) {
            case REG_A_RE: return aRe;
            case REG_A_IM: return aIm;
            case REG_B_RE: return bRe;
            case REG_B_IM: return bIm;
            case REG_CTRL: return 0;
            case REG_STATUS: return done ? 1 : 0;
            case REG_RES_RE: return resRe;
            case REG_RES_IM: return resIm;
            default:
                throw new BusException(String.format("%s: ungültiger Register-Offset 0x%02x", getName(), offset));
        }
    }

    @Override
    public void write(int offset, int value) {
        switch (offset) {
            case REG_A_RE: aRe = value; break;
            case REG_A_IM: aIm = value; break;
            case REG_B_RE: bRe = value; break;
            case REG_B_IM: bIm = value; break;
            case REG_CTRL:
                if ((value & CTRL_START) != 0) {
                    start();
                }
                break;
            default:
                throw new BusException(String.format("%s: Register-Offset 0x%02x ist nicht beschreibbar",
                        getName(), offset));
        }
    }

    private void start() {
        // Operanden werden beim Start übernommen; ein Start während einer Berechnung startet neu
        int[] result = multiply(aRe, aIm, bRe, bIm, fracBits);
        pendingRe = result[0];
        pendingIm = result[1];
        remainingCycles = latency;
        done = false;
        starts++;
    }

    @Override
    public void tick() {
        if (remainingCycles > 0) {
            busyCycles++;
            if (--remainingCycles == 0) {
                resRe = pendingRe;
                resIm = pendingIm;
                done = true;
            }
        }
    }

    public int getResultRe() {
        return resRe;
    }

    public int getResultIm() {
        return resIm;
    }

    public boolean isDone() {
        return done;
    }

    public long getStarts() {
        return starts;
    }

    @Override
    public String describeState() {
        return String.format("  Latenz: %d Takte, Nachkommabits: %d%n", latency, fracBits)
                + String.format("  A = %d %+d j, B = %d %+d j%n", aRe, aIm, bRe, bIm)
                + String.format("  Ergebnis = %d %+d j (RES_RE = 0x%08x, RES_IM = 0x%08x), DONE = %d%n",
                resRe, resIm, resRe, resIm, done ? 1 : 0)
                + String.format("  Berechnungen gestartet: %d, Takte belegt: %d%n", starts, busyCycles);
    }
}
