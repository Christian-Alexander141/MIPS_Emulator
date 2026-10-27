package assembler;

/**
 * Ein assemblierter Befehl: Adresse im Befehlsspeicher, 32-Bit-Maschinenwort und Herkunft im Quelltext.
 * Pseudobefehle (z. B. bne) erzeugen mehrere Einträge mit derselben Quellzeile.
 */
public record AssembledInstruction(int address, int word, int lineNumber, String sourceText, String disassembly) {
}
