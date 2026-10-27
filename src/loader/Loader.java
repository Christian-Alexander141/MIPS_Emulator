package loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import CPU.CPU;
import CPU.Cristian1604CPU;
import CPU.simpleCPU;
import accelerator.ComplexMultiplier;
import bus.Bus;
import bus.MemoryMap;
import export.HexExporter;
import memory.DataMemory;

public class Loader {

    // ANSI-Farbcode für Gelb/Orange (Textfarbe)
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_ORANGE = "\u001B[33m"; // Gelb (wirkt wie Orange)

    public static final Path DEFAULT_EXPORT_DIR = Path.of("export");

    public static void load() {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Testmodus aktivieren? (j/n): ");
        boolean testMode = scanner.nextLine().trim().equalsIgnoreCase("j");

        System.out.println("Willkommen zum MIPS-Emulator!");
        System.out.println("Bitte wählen Sie eine CPU-Variante:");
        System.out.println("1 - Standard CPU (vereinfachte Lern-CPU)");
        System.out.println("2 - cristian1604-MIPS mit Beschleuniger für komplexe Multiplikation am Bus");
        System.out.print("Ihre Wahl: ");

        int auswahl = scanner.nextInt();
        scanner.nextLine(); // Puffer leeren

        if (auswahl == 1) {
            System.out.println("Standard-CPU ausgewählt.");
        } else if (auswahl == 2) {
            System.out.println("cristian1604-MIPS ausgewählt.");
        } else {
            System.out.println("Ungültige Auswahl. Standard-CPU wird verwendet.");
        }

        System.out.print("Name der zu ladenden .asm-Datei (z. B. 'xy.asm'): ");
        String filename = scanner.nextLine().trim();

        List<String> sourceLines = readSourceLines(filename);
        if (sourceLines == null) {
            System.err.println("Fehler: Datei '" + filename + "' nicht gefunden.");
            return;
        }

        try {
            if (auswahl == 2) {
                int latency = askInt(scanner, "Latenz des Beschleunigers in Takten", ComplexMultiplier.DEFAULT_LATENCY);
                int fracBits = askInt(scanner, "Nachkommabits der Festkommadarstellung (0 = Ganzzahl)", 0);

                Cristian1604CPU cpu = createCristianSystem(latency, fracBits);
                cpu.setTrace(testMode);
                cpu.run(programName(filename), sourceLines);

                if (cpu.isSuccessful()) {
                    HexExporter.printToConsole(cpu.getProgram());
                    System.out.print("Hex-Dateien nach '" + DEFAULT_EXPORT_DIR + "/' schreiben? (j/n): ");
                    if (scanner.nextLine().trim().equalsIgnoreCase("j")) {
                        exportFiles(cpu, DEFAULT_EXPORT_DIR);
                    }
                } else {
                    System.out.println("Simulation nicht erfolgreich – es wird kein Hex-Code ausgegeben.");
                }
            } else {
                // An CPU übergeben
                CPU selectedCPU = new simpleCPU();
                selectedCPU.run(cleanLines(sourceLines));
            }

            // Testmodus-Ausgabe in Orange
            if (testMode) {
                List<String> instructions = cleanLines(sourceLines);
                System.out.println(ANSI_ORANGE + "\n--- Testmodus aktiviert ---");
                System.out.println("Gelesene Instruktionen:");
                for (int i = 0; i < instructions.size(); i++) {
                    System.out.println((i + 1) + ": " + instructions.get(i));
                }
                System.out.println("--- Ende des Testmodus ---" + ANSI_RESET);
            }

        } catch (Exception e) {
            System.err.println("Fehler beim Laden oder Ausführen des Programms: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Nicht-interaktiver Aufruf, z. B. aus Skripten:
     * <pre>
     * java Main datei.asm [--cpu simple|cristian] [--latency N] [--frac N] [--max-cycles N]
     *                     [--trace] [--export VERZEICHNIS] [--no-export]
     * </pre>
     * Liefert 0 bei Erfolg, sonst 1.
     */
    public static int runBatch(String[] args) {
        String filename = null;
        String cpuType = "cristian";
        int latency = ComplexMultiplier.DEFAULT_LATENCY;
        int fracBits = 0;
        long maxCycles = Cristian1604CPU.DEFAULT_MAX_CYCLES;
        boolean trace = false;
        Path exportDir = DEFAULT_EXPORT_DIR;

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--cpu": cpuType = args[++i]; break;
                    case "--latency": latency = Integer.parseInt(args[++i]); break;
                    case "--frac": fracBits = Integer.parseInt(args[++i]); break;
                    case "--max-cycles": maxCycles = Long.parseLong(args[++i]); break;
                    case "--trace": trace = true; break;
                    case "--export": exportDir = Path.of(args[++i]); break;
                    case "--no-export": exportDir = null; break;
                    default:
                        if (args[i].startsWith("--") || filename != null) {
                            throw new IllegalArgumentException("Unbekanntes Argument '" + args[i] + "'");
                        }
                        filename = args[i];
                }
            }
            if (filename == null) {
                throw new IllegalArgumentException("Keine .asm-Datei angegeben");
            }
        } catch (RuntimeException e) {
            System.err.println("Fehler: " + e.getMessage());
            System.err.println("Aufruf: java Main datei.asm [--cpu simple|cristian] [--latency N] [--frac N] "
                    + "[--max-cycles N] [--trace] [--export VERZEICHNIS] [--no-export]");
            return 1;
        }

        List<String> sourceLines = readSourceLines(filename);
        if (sourceLines == null) {
            System.err.println("Fehler: Datei '" + filename + "' nicht gefunden.");
            return 1;
        }

        if (cpuType.equals("simple")) {
            new simpleCPU().run(cleanLines(sourceLines));
            return 0;
        }

        Cristian1604CPU cpu = createCristianSystem(latency, fracBits);
        cpu.setTrace(trace);
        cpu.setMaxCycles(maxCycles);
        cpu.run(programName(filename), sourceLines);

        if (!cpu.isSuccessful()) {
            System.out.println("Simulation nicht erfolgreich – es wird kein Hex-Code ausgegeben.");
            return 1;
        }
        HexExporter.printToConsole(cpu.getProgram());
        if (exportDir != null) {
            return exportFiles(cpu, exportDir) ? 0 : 1;
        }
        return 0;
    }

    /** Baut das System auf: cristian1604-MIPS + Bus mit Datenspeicher und Beschleuniger. */
    public static Cristian1604CPU createCristianSystem(int latency, int fracBits) {
        Bus bus = new Bus();
        bus.attach(new DataMemory(MemoryMap.RAM_BASE, MemoryMap.RAM_SIZE));
        bus.attach(new ComplexMultiplier(MemoryMap.ACC_BASE, latency, fracBits));
        return new Cristian1604CPU(bus);
    }

    /**
     * Liest eine .asm-Datei. Gesucht wird zuerst im Klassenpfad unter "Programm/" (wie bisher),
     * danach im Dateisystem (Pfad wie angegeben bzw. unter src/Programm/). Liefert null, falls nicht gefunden.
     */
    public static List<String> readSourceLines(String filename) {
        try {
            InputStream inputStream = Loader.class.getClassLoader().getResourceAsStream("Programm/" + filename);
            if (inputStream != null) {
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }
                return lines;
            }
            for (Path path : List.of(Path.of(filename), Path.of("src", "Programm", filename))) {
                if (Files.isRegularFile(path)) {
                    return Files.readAllLines(path, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            System.err.println("Fehler beim Lesen von '" + filename + "': " + e.getMessage());
        }
        return null;
    }

    /** Kommentare und Leerzeilen entfernen (Eingabeformat der Standard-CPU). */
    private static List<String> cleanLines(List<String> sourceLines) {
        List<String> instructions = new ArrayList<>();
        for (String line : sourceLines) {
            line = line.trim();
            int commentIndex = line.indexOf('#');
            if (commentIndex != -1) {
                line = line.substring(0, commentIndex).trim();
            }
            if (!line.isEmpty()) {
                instructions.add(line);
            }
        }
        return instructions;
    }

    private static boolean exportFiles(Cristian1604CPU cpu, Path directory) {
        try {
            for (Path file : HexExporter.exportAll(cpu, directory)) {
                System.out.println("Geschrieben: " + file);
            }
            return true;
        } catch (IOException e) {
            System.err.println("Fehler beim Schreiben der Hex-Dateien: " + e.getMessage());
            return false;
        }
    }

    private static String programName(String filename) {
        String name = Path.of(filename).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static int askInt(Scanner scanner, String question, int defaultValue) {
        System.out.print(question + " [" + defaultValue + "]: ");
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.println("Ungültige Eingabe, verwende " + defaultValue + ".");
            return defaultValue;
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            System.exit(runBatch(args));
        }
        load();
    }
}
