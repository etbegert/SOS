package sos;

/**
 * This class is the centerpiece of a simulation of the essential hardware of a
 * microcomputer. This includes a processor chip, RAM and I/O devices. It is
 * designed to demonstrate a simulated operating system (SOS).
 * 
 * @see RAM
 * @see SOS
 * @see Program
 * @see Sim
 * 
 *      HW1 Authors: John Olennikov, Robert Rodriguez
 *      HW2 Authors: Raphael Ramos, John Olennikov
 *      HW3 Authors: Scott Matsuo, John Olennikov
 *      HW4 Authors: John Olennikov, Ross Hallauer
 *      HW5 Authors: Joseph Devlin, John Olennikov
 *      HW6 Authors: Carl Lulay, Et Begert, John Olennikov
 *      HW7 Authors: Et Begert, Ben Rumptz
 */

public class CPU implements Runnable {
	
	// ======================================================================
	// Constants
	// ----------------------------------------------------------------------
	
	// These constants define the instructions available on the chip
	public static final int SET = 0; /* set value of reg */
	public static final int ADD = 1; // put reg1 + reg2 into reg3
	public static final int SUB = 2; // put reg1 - reg2 into reg3
	public static final int MUL = 3; // put reg1 * reg2 into reg3
	public static final int DIV = 4; // put reg1 / reg2 into reg3
	public static final int COPY = 5; // copy reg1 to reg2
	public static final int BRANCH = 6; // goto address in reg
	public static final int BNE = 7; // branch if not equal
	public static final int BLT = 8; // branch if less than
	public static final int POP = 9; // load value from stack
	public static final int PUSH = 10; // save value to stack
	public static final int LOAD = 11; // load value from heap
	public static final int SAVE = 12; // save value to heap
	public static final int TRAP = 15; // system call
	
	// These constants define the indexes to each register
	public static final int R0 = 0; // general purpose registers
	public static final int R1 = 1;
	public static final int R2 = 2;
	public static final int R3 = 3;
	public static final int R4 = 4;
	public static final int PC = 5; // program counter
	public static final int SP = 6; // stack pointer
	public static final int BASE = 7; // bottom of currently accessible RAM
	public static final int LIM = 8; // top of accessible RAM
	public static final int NUMREG = 9; // number of registers
	
	// Misc constants
	public static final int NUMGENREG = PC; // the number of general registers
	public static final int INSTRSIZE = 4; // number of ints in a single instr +
										   // args. (Set to a fixed value for
										   // simplicity.)
	public static final int CLOCK_FREQ = 15;
	
	// error codes
	public static final int ERROR_OUT_OF_BOUND = 0; // out of bounds
	public static final int ERROR_FULL_STACK = 1; // stack is full
	public static final int ERROR_EMPTY_STACK = 2; // stack is empty
	public static final int ERROR_REG_DNE = 3; // register does not exist
	public static final int ERROR_LARGE_PROG = 4; // program larger than size
	public static final int ERROR_LOW_RAM = 5; // not enough ram for program
	
	// ======================================================================
	// Member variables
	// ----------------------------------------------------------------------
	/**
	 * specifies whether the CPU should output details of its work
	 **/
	public static boolean m_verbose = false;
	
	/**
	 * This array contains all the registers on the "chip".
	 **/
	private int m_registers[];
	
	/**
	 * A pointer to the RAM used by this CPU
	 * 
	 * @see RAM
	 **/
	private RAM m_RAM = null;
	
	/**
	 * contains the cpu instruction
	 */
	private int[] m_instr = null;
	
	/**
	 * a reference to the trap handler for this CPU. On a real CPU this would
	 * simply be an address that the PC register is set to.
	 * 
	 * @see TrapHandler
	 */
	private TrapHandler m_TH = null;
	
	/**
	 * stores reference to the CPU' s Interrupt Controller
	 */
	private InterruptController m_IC = null;
	
	/**
	 * reference to the memory management unit
	 */
	private MMU m_MMU = null;
	
	/**
	 * counts how many cpu cycles have elapsed
	 */
	private int m_ticks = 0;
	
	
	// ======================================================================
	// Methods
	// ----------------------------------------------------------------------
	
	/**
	 * addTicks:increments m_ticks by toAdd
	 * 
	 * @param toAdd: the number to increment m_ticks by
	 */
	public void addTicks(int toAdd)
	{
		m_ticks += toAdd;
	}
	
	/**
	 * CPU ctor
	 * 
	 * Initializes all member variables.
	 */
	public CPU(RAM ram, InterruptController ic, MMU mmu) {
		m_registers = new int[NUMREG];
		for (int i = 0; i < NUMREG; i++) {
			m_registers[i] = 0;
		}
		m_RAM = ram;
		//Initiate memory management unit
		m_MMU = mmu;
		//Initiate Interrupt Control
		m_IC = ic;
		
	}// CPU ctor
	
	/**
	 * getPC
	 * 
	 * @return the value of the program counter
	 */
	public int getPC() {
		return m_registers[PC];
	}
	
	/**
	 * getSP
	 * 
	 * @return the value of the stack pointer
	 */
	public int getSP() {
		return m_registers[SP];
	}
	
	/**
	 * getBASE
	 * 
	 * @return the value of the base register
	 */
	public int getBASE() {
		return m_registers[BASE];
	}
	
	/**
	 * getLIMIT
	 * 
	 * @return the value of the limit register
	 */
	public int getLIM() {
		return m_registers[LIM];
	}
	
	/**
	 * getRegisters
	 * 
	 * @return the registers
	 */
	public int[] getRegisters() {
		return m_registers;
	}
	
	/**
	 * getReg
	 * 
	 * helper function that returns the correct value of the id of
	 * register specified by reg. Ensures that code won't break if
	 * the register locations of R0 - R4 ever change from 0 - 4.
	 * 
	 * Sure there is some performance loss, but its JAVA anyways
	 * 
	 * @param reg
	 *            the register number
	 * 
	 * @return returns value in register
	 */
	public int getReg(int reg) {
		switch (reg) {
			case 0:
				return m_registers[R0];
			case 1:
				return m_registers[R1];
			case 2:
				return m_registers[R2];
			case 3:
				return m_registers[R3];
			case 4:
				return m_registers[R4];
			default:
				// Return default (should never be reached)
				returnError(ERROR_REG_DNE);
				return 0;
		}
	}
	
	/**
	 * getTicks: returns the elapsed number of cpu cycles for the given process
	 * 
	 * @return:the elapsed ticks
	 */
	public int getTicks()
	{
		return m_ticks;
	}
	
	/**
	 * setPC
	 * 
	 * @param v
	 *            the new value of the program counter
	 */
	public void setPC(int v) {
		m_registers[PC] = v;
	}
	
	/**
	 * setSP
	 * 
	 * @param v
	 *            the new value of the stack pointer
	 */
	public void setSP(int v) {
		m_registers[SP] = v;
	}
	
	/**
	 * setBASE
	 * 
	 * @param v
	 *            the new value of the base register
	 */
	public void setBASE(int v) {
		m_registers[BASE] = v;
	}
	
	/**
	 * setLIM
	 * 
	 * @param v
	 *            the new value of the limit register
	 */
	public void setLIM(int v) {
		m_registers[LIM] = v;
	}
	
	/**
	 * setReg
	 * 
	 * helper function that sets the value of the reg
	 * specified by the reg var to the val var value.
	 * If the register locations of R0 - R4 ever change
	 * from 0 - 4, this ensures the code won't break
	 * 
	 * Sure there is some performance loss, but its JAVA anyways
	 * 
	 * @param reg
	 *            the register number
	 * @param val
	 *            the value to set the register
	 */
	public void setReg(int reg, int val) {
		
		switch (reg) {
			case 0:
				m_registers[R0] = val;
				break;
			case 1:
				m_registers[R1] = val;
				break;
			case 2:
				m_registers[R2] = val;
				break;
			case 3:
				m_registers[R3] = val;
				break;
			case 4:
				m_registers[R4] = val;
				break;
			default:
				returnError(ERROR_REG_DNE);
		}
	}
	
	/**
	 * setVerbose
	 * 
	 * Setter method to bypass hard coding
	 * 
	 * @param v
	 *            set or unset verbose
	 */
	public void setVerbose(Boolean v) {
		m_verbose = v;
	}
	
	/**
	 * regDump
	 * 
	 * Prints the values of the registers. Useful for debugging.
	 */
	public void regDump() {
		// Print each available register (id) and its value
		for (int i = 0; i < NUMGENREG; i++) {
			System.out.print("r" + i + "=" + m_registers[i] + " ");
		}// for
		
		// Removed unnecessary concatenation
		System.out.print("PC=" + getPC());
		System.out.print(" SP=" + getSP());
		System.out.print(" BASE=" + getBASE());
		System.out.print(" LIM=" + getLIM());
		System.out.println("");
	}// regDump
	
	/**
	 * returnError
	 * 
	 * displays appropriate error according to ID
	 * 
	 */
	public void returnError(int e) {
		switch (e) {
			case ERROR_OUT_OF_BOUND:
				System.out.println("Error: Out of Bounds.");
				break;
			case ERROR_FULL_STACK:
				System.out.println("Error: Stack is full.");
				break;
			case ERROR_EMPTY_STACK:
				System.out.println("Error: Stack is empty.");
				break;
			case ERROR_REG_DNE:
				System.out.println("Error: Specified register doesn't exist.");
				break;
			case ERROR_LARGE_PROG:
				System.out.println("Error: Program size is "
						+ "greater than allocated size.");
				break;
			case ERROR_LOW_RAM:
				System.out.println("Error: Not enough RAM to load program.");
				break;
		}
	}
	
	/**
	 * pushToStack
	 * 
	 * pushes value to top of stack and decrements stack pointer
	 * 
	 * @param value
	 *            the value to push to stack
	 */
	public void pushToStack(int val) {
		// Make sure stack does not grow past BASE
		if (getSP() > getBASE()) {
			// Write value to stack
			m_MMU.write(getSP(), getReg(val));
			
			// SP is moving down RAM (pushing)
			setSP(getSP() - 1);
		}
		else {
			returnError(ERROR_FULL_STACK);
		}
	}
	
	/**
	 * popFromStack
	 * 
	 * takes the value at top of stack and returns
	 * 
	 */
	public void popFromStack(int reg) {
		// stack is at the top of RAM
		// SP max value is total RAM size - 1 (starting at 0)
		if (getSP() < getLIM()) {
			// SP is moving up RAM (popping)
			setSP(getSP() + 1);
			
			// Read value from stack and store in reg
			setReg(reg, m_MMU.read(getSP()));
		}
		else {
			returnError(ERROR_EMPTY_STACK);
		}
	}
	
	/**
	 * branchCheck
	 * 
	 * Verifies branch (out of bounds) locations
	 * 
	 * @param addr
	 *            is branch location
	 * @return returns whether branch or not
	 */
	public Boolean boundaryCheck(int addr) {
		// Make sure address is less than LIMIT and SP, and greater than base
		if (addr < getLIM() && addr < getSP() && addr >= getBASE()) {
			return true;
		}
		else {
			m_TH.interruptIllegalMemoryAccess(addr);
			return false;
		}
	}
	
	/**
	 * printInstr
	 * 
	 * Prints a given instruction in a user readable format. Useful for
	 * debugging.
	 * 
	 * @param instr
	 *            the current instruction
	 */
	public void printInstr(int[] instr) {
		switch (instr[0]) {
			case SET:
				System.out.println("SET R" + instr[1] + " = " + instr[2]);
				break;
			case ADD:
				System.out.println("ADD R" + instr[1] + " = R" + instr[2]
						+ " + R" + instr[3]);
				break;
			case SUB:
				System.out.println("SUB R" + instr[1] + " = R" + instr[2]
						+ " - R" + instr[3]);
				break;
			case MUL:
				System.out.println("MUL R" + instr[1] + " = R" + instr[2]
						+ " * R" + instr[3]);
				break;
			case DIV:
				System.out.println("DIV R" + instr[1] + " = R" + instr[2]
						+ " / R" + instr[3]);
				break;
			case COPY:
				System.out.println("COPY R" + instr[1] + " = R" + instr[2]);
				break;
			case BRANCH:
				System.out.println("BRANCH @" + instr[1]);
				break;
			case BNE:
				System.out.println("BNE (R" + instr[1] + " != R" + instr[2]
						+ ") @" + instr[3]);
				break;
			case BLT:
				System.out.println("BLT (R" + instr[1] + " < R" + instr[2]
						+ ") @" + instr[3]);
				break;
			case POP:
				System.out.println("POP R" + instr[1]);
				break;
			case PUSH:
				System.out.println("PUSH R" + instr[1]);
				break;
			case LOAD:
				System.out.println("LOAD R" + instr[1] + " <-- @R" + instr[2]);
				break;
			case SAVE:
				System.out.println("SAVE R" + instr[1] + " --> @R" + instr[2]);
				break;
			case TRAP:
				System.out.print("TRAP ");
				break;
			default: // should never be reached
				System.out.println("?? ");
				break;
		}// switch
		
	}// printInstr
	
	/**
	 * executeInstr
	 * 
	 * Executes appropriate instruction
	 * 
	 * @param instr
	 *            the current instruction
	 */
	public void executeInstr(int[] instr) {
		// First parameter is pidgin command
		switch (instr[0]) {
			case SET:
				setReg(instr[1], instr[2]);
				break;
			case ADD:
				setReg(instr[1], getReg(instr[2]) + getReg(instr[3]));
				break;
			case SUB:
				setReg(instr[1], getReg(instr[2]) - getReg(instr[3]));
				break;
			case MUL:
				setReg(instr[1], getReg(instr[2]) * getReg(instr[3]));
				break;
			case DIV:
				// Make sure no "Divide by Zero"
				if (getReg(instr[3]) == 0) {
					m_TH.interruptDivideByZero();
				}
				setReg(instr[1], getReg(instr[2]) / getReg(instr[3]));
				break;
			case COPY:
				setReg(instr[1], getReg(instr[2]));
				break;
			case BRANCH:
				if (boundaryCheck(getBASE() + instr[1])) {
					setPC(getBASE() + instr[1]);
				}
				break;
			case BNE:
				if (getReg(instr[1]) != getReg(instr[2])) {
					// Check branch boundaries with physical addresses
					if (boundaryCheck(getBASE() + instr[3])) {
						setPC(getBASE() + instr[3]);
					}
				}
				break;
			case BLT:
				if (getReg(instr[1]) < getReg(instr[2])) {
					// Check branch boundaries with physical addresses
					if (boundaryCheck(getBASE() + instr[3])) {
						setPC(getBASE() + instr[3]);
					}
				}
				break;
			case POP:
				popFromStack(instr[1]);
				break;
			case PUSH:
				pushToStack(instr[1]);
				break;
			case LOAD:
				// Convert from logical to physical memory before read
				boundaryCheck(getBASE() + getReg(instr[2]));
				setReg(instr[1], m_MMU.read(getBASE() + getReg(instr[2])));
				break;
			case SAVE:
				// Convert from logical to physical memory before save
				boundaryCheck(getBASE() + getReg(instr[2]));
				m_MMU.write(getBASE() + getReg(instr[2]), getReg(instr[1]));
				break;
			case TRAP:
				// Create a system call interrupt
				m_TH.systemCall();
				break;
			default: // should never be reached
				// Interrupt if command not recognized
				m_TH.interruptIllegalInstruction(instr);
				break;
		}// switch
		
	}// executeInstr
	
	/**
	 * checkForIOInterrupt
	 * 
	 * Checks the databus for signals from the interrupt controller and, if
	 * found, invokes the appropriate handler in the operating system.
	 * 
	 */
	private void checkForIOInterrupt() {
		// If there is no interrupt to process, do nothing
		if (m_IC.isEmpty()) {
			return;
		}
		
		// Retreive the interrupt data
		int[] intData = m_IC.getData();
		
		// Report the data if in verbose mode
		if (m_verbose) {
			System.out.println("CPU received interrupt: type=" + intData[0]
					+ " dev=" + intData[1] + " addr=" + intData[2] + " data="
					+ intData[3]);
		}
		
		// Dispatch the interrupt to the OS
		switch (intData[0]) {
			case InterruptController.INT_READ_DONE:
				m_TH.interruptIOReadComplete(intData[1], intData[2], intData[3]);
				break;
			case InterruptController.INT_WRITE_DONE:
				m_TH.interruptIOWriteComplete(intData[1], intData[2]);
				break;
			default:
				System.out.println("CPU ERROR:  Illegal Interrupt Received.");
				System.exit(-1);
				break;
		}// switch
		
	}// checkForIOInterrupt
	
	/**
	 * Get next instruction from RAM and execute
	 */
	public void run() {
		
		// While the PC is less than allocated memory and less than SP
		while (getPC() < getLIM() && getPC() < getSP()) {
			m_instr = m_MMU.fetch(getPC());
			// Increment PC counter by Instruction Size now
			// since nowhere lower does it get called again
			setPC(getPC() + INSTRSIZE);
			
	         //increment clock
            addTicks(1);
			
			if (m_verbose) {
				regDump();
				printInstr(m_instr);
			}
			
			// Parse and execute instruction
			executeInstr(m_instr);
			
			// check interrupts
			checkForIOInterrupt();
		}
		
	}// run
	
	/**
	 * registerTrapHandler
	 * 
	 * allows SOS to register itself as the trap handler
	 */
	public void registerTrapHandler(TrapHandler th) {
		m_TH = th;
	}
	
	// ======================================================================
	// Callback Interface
	// ----------------------------------------------------------------------
	/**
	 * TrapHandler
	 * 
	 * This interface should be implemented by the operating system to allow the
	 * simulated CPU to generate hardware interrupts and system calls.
	 */
	public interface TrapHandler {
		void interruptClock();
		
		void interruptIllegalMemoryAccess(int addr);
		
		void interruptDivideByZero();
		
		void interruptIllegalInstruction(int[] instr);
		
		void systemCall();
		
		public void interruptIOReadComplete(int devID, int addr, int data);
		
		public void interruptIOWriteComplete(int devID, int addr);
	};// interface TrapHandler
	
};// class CPU
