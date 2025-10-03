package CPU;

import memory.Register;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class simpleCPU implements CPU {

   private final Register register = new Register();
   private final Map<Integer, Integer> memory = new HashMap<>(); // Simulierter Speicher

   @Override
   public void run(List<String> instructions) {
      System.out.println("Simple CPU: Starte Programmausführung...");
      int pc = 0;

      while (pc < instructions.size()) {
         String instruction = instructions.get(pc).trim();

         if (instruction.isEmpty()) {
            pc++;
            continue;
         }

         String[] parts = instruction.split("\\s+", 2);
         String opcode = parts[0].toLowerCase();
         String[] args = parts.length > 1 ? parts[1].split(",") : new String[0];

         for (int i = 0; i < args.length; i++) {
            args[i] = args[i].trim();
         }

         try {
            switch (opcode) {
               case "add":
                  writeReg(args[0], readReg(args[1]) + readReg(args[2]));
                  break;
               case "addi":
                  writeReg(args[0], readReg(args[1]) + Integer.parseInt(args[2]));
                  break;
               case "sub":
                  writeReg(args[0], readReg(args[1]) - readReg(args[2]));
                  break;
               case "and":
                  writeReg(args[0], readReg(args[1]) & readReg(args[2]));
                  break;
               case "or":
                  writeReg(args[0], readReg(args[1]) | readReg(args[2]));
                  break;
               case "slt":
                  writeReg(args[0], (readReg(args[1]) < readReg(args[2])) ? 1 : 0);
                  break;
               case "beq":
                  if (readReg(args[0]) == readReg(args[1])) {
                     pc += Integer.parseInt(args[2]);
                     continue;
                  }
                  break;
               case "bne":
                  if (readReg(args[0]) != readReg(args[1])) {
                     pc += Integer.parseInt(args[2]);
                     continue;
                  }
                  break;
               case "j":
                  pc = Integer.parseInt(args[0]);
                  continue;
               case "lw":
                  // lw $t0, 4($t1)
                  int rt = getRegisterIndex(args[0]);
                  String[] offsetParts = args[1].split("[()]+");
                  int offset = Integer.parseInt(offsetParts[0]);
                  int base = readReg(offsetParts[1]);
                  int address = base + offset;
                  writeReg(rt, memory.getOrDefault(address, 0));
                  break;
               case "sw":
                  // sw $t0, 4($t1)
                  int value = readReg(args[0]);
                  String[] swParts = args[1].split("[()]+");
                  int swOffset = Integer.parseInt(swParts[0]);
                  int swBase = readReg(swParts[1]);
                  int swAddr = swBase + swOffset;
                  memory.put(swAddr, value);
                  break;
               default:
                  System.out.println("Unbekannter Opcode: " + opcode);
                  break;
            }
         } catch (Exception e) {
            System.err.println("Fehler bei Instruktion: '" + instruction + "' → " + e.getMessage());
         }

         pc++;
      }

      register.printAllRegisters(); // Ausgabe der Register nach der Ausführung
   }

   private int readReg(String regName) {
      int[] bits = register.get(getRegisterIndex(regName));
      return bitsToInt(bits);
   }

   private void writeReg(String regName, int value) {
      int reg = getRegisterIndex(regName);
      if (reg == 0) return; // $0 darf nicht überschrieben werden
      register.set(reg, intToBits(value));
   }

   private void writeReg(int regIndex, int value) {
      if (regIndex == 0) return; // $0 darf nicht überschrieben werden
      register.set(regIndex, intToBits(value));
   }

   private int[] intToBits(int value) {
      int[] bits = new int[32];
      for (int i = 31; i >= 0; i--) {
         bits[i] = value & 1;
         value >>= 1;
      }
      return bits;
   }

   private int bitsToInt(int[] bits) {
      int value = 0;
      for (int bit : bits) {
         value = (value << 1) | bit;
      }
      return value;
   }

   private int getRegisterIndex(String regStr) {
      regStr = regStr.replace("$", "").trim();
      return Integer.parseInt(regStr);
   }
}
