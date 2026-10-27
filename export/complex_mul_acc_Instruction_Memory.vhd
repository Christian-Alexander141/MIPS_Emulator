-- Automatisch erzeugt vom MIPS-Emulator – Programm: complex_mul_acc
-- 18 Befehle, 72 Byte
-- Ersetzt Instruction_Memory.vhd aus https://github.com/cristian1604/mips
-- Originalentwurf: Bottazzi, Cristian - 2017 (https://github.com/cristian1604/)

library IEEE;
use IEEE.STD_LOGIC_1164.ALL;
use ieee.std_logic_arith.all;
use ieee.std_logic_unsigned.all;

entity Instruction_Memory is
    Port ( dir : in  STD_LOGIC_VECTOR (31 downto 0);
           instr : out  STD_LOGIC_VECTOR (31 downto 0));
end Instruction_Memory;

architecture Behavioral of Instruction_Memory is
	type mem is array(0 to 511) of std_logic_vector(7 downto 0);
	constant code : mem:=(

--		Quelltext:
--		addi $s0, $zero, ACC_BASE
--		addi $t0, $zero, 3
--		addi $t1, $zero, 4
--		addi $t2, $zero, 5
--		addi $t3, $zero, -2
--		sw   $t0, ACC_A_RE($s0)
--		sw   $t1, ACC_A_IM($s0)
--		sw   $t2, ACC_B_RE($s0)
--		sw   $t3, ACC_B_IM($s0)
--		addi $t4, $zero, 1
--		sw   $t4, ACC_CTRL($s0)
--		poll:   lw   $t5, ACC_STATUS($s0)
--		beq  $t5, $zero, poll
--		lw   $s1, ACC_RES_RE($s0)
--		lw   $s2, ACC_RES_IM($s0)
--		sw   $s1, 0($zero)
--		sw   $s2, 4($zero)
--		halt

		x"20",x"10",x"7f",x"00",  -- 0x0000: addi $s0, $zero, 32512
		x"20",x"08",x"00",x"03",  -- 0x0004: addi $t0, $zero, 3
		x"20",x"09",x"00",x"04",  -- 0x0008: addi $t1, $zero, 4
		x"20",x"0a",x"00",x"05",  -- 0x000c: addi $t2, $zero, 5
		x"20",x"0b",x"ff",x"fe",  -- 0x0010: addi $t3, $zero, -2
		x"ae",x"08",x"00",x"00",  -- 0x0014: sw $t0, 0($s0)
		x"ae",x"09",x"00",x"04",  -- 0x0018: sw $t1, 4($s0)
		x"ae",x"0a",x"00",x"08",  -- 0x001c: sw $t2, 8($s0)
		x"ae",x"0b",x"00",x"0c",  -- 0x0020: sw $t3, 12($s0)
		x"20",x"0c",x"00",x"01",  -- 0x0024: addi $t4, $zero, 1
		x"ae",x"0c",x"00",x"10",  -- 0x0028: sw $t4, 16($s0)
		x"8e",x"0d",x"00",x"14",  -- 0x002c: lw $t5, 20($s0)
		x"11",x"a0",x"ff",x"fe",  -- 0x0030: beq $t5, $zero, -2
		x"8e",x"11",x"00",x"18",  -- 0x0034: lw $s1, 24($s0)
		x"8e",x"12",x"00",x"1c",  -- 0x0038: lw $s2, 28($s0)
		x"ac",x"11",x"00",x"00",  -- 0x003c: sw $s1, 0($zero)
		x"ac",x"12",x"00",x"04",  -- 0x0040: sw $s2, 4($zero)
		x"08",x"00",x"00",x"11",  -- 0x0044: j 0x00000044

	others=> x"00");

begin

	process(dir)
	begin
		instr(31 downto 24)<=code(conv_integer(dir));
		instr(23 downto 16)<=code(conv_integer(dir)+1);
		instr(15 downto 8)<= code(conv_integer(dir)+2);
		instr(7 downto 0)<=code(conv_integer(dir)+3);
	end process;

end Behavioral;
