package loader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Scanner;

import CPU.CPU;
import CPU.simpleCPU;

public class Loader {

    // ANSI-Farbcode für Gelb/Orange (Textfarbe)
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_ORANGE = "\u001B[33m"; // Gelb (wirkt wie Orange)

    public static void load() {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Testmodus aktivieren? (j/n): ");
        boolean testMode = scanner.nextLine().trim().equalsIgnoreCase("j");

        System.out.println("Willkommen zum MIPS-Emulator!");
        System.out.println("Bitte wählen Sie eine CPU-Variante:");
        System.out.println("1 - Standard CPU");
        System.out.println("2 - CPU mit Coprozessor");
        System.out.print("Ihre Wahl: ");

        int auswahl = scanner.nextInt();
        scanner.nextLine(); // Puffer leeren

        CPU selectedCPU;

        switch (auswahl) {
            case 1:
                selectedCPU = new simpleCPU();
                System.out.println("Standard-CPU ausgewählt.");
                break;
            case 2:
                // selectedCPU = new CPUWithCoprocessor();
                System.out.println("CPU mit Coprozessor ausgewählt. (Noch nicht implementiert)");
                selectedCPU = new simpleCPU(); // Platzhalter
                break;
            default:
                System.out.println("Ungültige Auswahl. Standard-CPU wird verwendet.");
                selectedCPU = new simpleCPU();
                break;
        }

        System.out.print("Name der zu ladenden .asm-Datei (z. B. 'xy.asm'): ");
        String filename = scanner.nextLine().trim();
        String fullPath = "Programm/" + filename;

        ArrayList<String> instructions = new ArrayList<>();

        try {
            InputStream inputStream = Loader.class.getClassLoader().getResourceAsStream(fullPath);
            if (inputStream == null) {
                System.err.println("Fehler: Datei '" + fullPath + "' nicht gefunden.");
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                int commentIndex = line.indexOf('#');
                if (commentIndex != -1) {
                    line = line.substring(0, commentIndex).trim();
                }
                if (!line.isEmpty()) {
                    instructions.add(line);
                }
            }

            reader.close();

            // An CPU übergeben
            selectedCPU.run(instructions);

            // Testmodus-Ausgabe in Orange
            if (testMode) {
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

    public static void main(String[] args) {
        load();
    }
}
