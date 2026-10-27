package bus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Datenbus zwischen dem cristian1604-MIPS und seinen Peripheriegeräten (Datenspeicher, Beschleuniger).
 * Dekodiert die Adresse, leitet den Zugriff an das passende Gerät weiter und protokolliert ihn.
 */
public class Bus {

    private final List<BusDevice> devices = new ArrayList<>();
    private final List<BusTransaction> transactions = new ArrayList<>();

    public void attach(BusDevice device) {
        long start = Integer.toUnsignedLong(device.getBaseAddress());
        long end = start + device.getSize();
        for (BusDevice other : devices) {
            long otherStart = Integer.toUnsignedLong(other.getBaseAddress());
            long otherEnd = otherStart + other.getSize();
            if (start < otherEnd && otherStart < end) {
                throw new IllegalArgumentException("Adressbereich von " + device.getName()
                        + " überschneidet sich mit " + other.getName());
            }
        }
        devices.add(device);
    }

    public int read(int address, long cycle, int pc) {
        BusDevice device = decode(address);
        int value = device.read(address - device.getBaseAddress());
        transactions.add(new BusTransaction(cycle, pc, false, address, value, device.getName()));
        return value;
    }

    public void write(int address, int value, long cycle, int pc) {
        BusDevice device = decode(address);
        device.write(address - device.getBaseAddress(), value);
        transactions.add(new BusTransaction(cycle, pc, true, address, value, device.getName()));
    }

    /** Taktflanke an alle Geräte weitergeben. */
    public void tick() {
        for (BusDevice device : devices) {
            device.tick();
        }
    }

    private BusDevice decode(int address) {
        if ((address & 3) != 0) {
            throw new BusException(String.format("Nicht wortausgerichteter Zugriff auf Adresse 0x%08x", address));
        }
        for (BusDevice device : devices) {
            if (device.contains(address)) {
                return device;
            }
        }
        throw new BusException(String.format("Kein Gerät an Adresse 0x%08x angeschlossen", address));
    }

    public List<BusDevice> getDevices() {
        return Collections.unmodifiableList(devices);
    }

    public List<BusTransaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    public String describeMemoryMap() {
        StringBuilder sb = new StringBuilder();
        for (BusDevice device : devices) {
            sb.append(String.format("  0x%08x - 0x%08x  %s%n", device.getBaseAddress(),
                    device.getBaseAddress() + device.getSize() - 1, device.getName()));
        }
        return sb.toString();
    }
}
