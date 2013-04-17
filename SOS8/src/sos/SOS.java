package sos;

import java.util.Collections;
import java.util.ListIterator;
import java.util.Random;
import java.util.Vector;

/**
 * This class contains the simulated operating system (SOS). Realistically it
 * would run on the same processor (CPU) that it is managing but instead it uses
 * the real-world processor in order to allow a focus on the essentials of
 * operating system design using a high level programming language.
 * 
 * HW1 Authors: John Olennikov, Robert Rodriguez HW2 Authors: Raphael Ramos John
 * Olennikov HW3 Authors: Scott Matsuo, John Olennikov HW4 Authors: John
 * Olennikov, Ross Hallauer HW5 Authors: Joseph Devlin, John Olennikov
 * HW6 Authors: Carl Lulay, Et Begert, John Olennikov
 * HW7 Authors: Etienne Begert.
 */
public class SOS implements CPU.TrapHandler
{

    // ======================================================================
    // Constants
    // ----------------------------------------------------------------------

    // These constants define the system calls this OS can currently handle
    public static final int SYSCALL_EXIT = 0; /* exit the current program */
    public static final int SYSCALL_OUTPUT = 1; /* outputs a number */
    public static final int SYSCALL_GETPID = 2; /* get current process id */
    public static final int SYSCALL_OPEN = 3; /* access a device */
    public static final int SYSCALL_CLOSE = 4; /* release a device */
    public static final int SYSCALL_READ = 5; /* get input from device */
    public static final int SYSCALL_WRITE = 6; /* send output to device */
    public static final int SYSCALL_EXEC = 7; /* spawn a new process */
    public static final int SYSCALL_YIELD = 8; /* yield CPU to another process */
    public static final int SYSCALL_COREDUMP = 9; /*
                                                   * print process state and
                                                   * exit
                                                   */

    // Success code that gets pushed onto stack after a successful system call
    public static final int SUCCESS = 0;

    // These constants define errors codes the OS pushes to a program's stack
    public static final int ERROR_DEV_DNE = -1; /* device does not exist */
    public static final int ERROR_DEV_NO_SHARE = -2; /* device is not sharable */
    public static final int ERROR_DEV_IS_OPEN = -3; /* device is already open */
    public static final int ERROR_DEV_NOT_OPEN = -4; /* device is not open */
    public static final int ERROR_DEV_READONLY = -5; /* device is read-only */
    public static final int ERROR_DEV_WRITEONLY = -6; /* device is write-only */

    /** This process is used as the idle process' id */
    public static final int IDLE_PROC_ID = 999;

    // ======================================================================
    // Member variables
    // ----------------------------------------------------------------------

    /**
     * This flag causes the SOS to print lots of potentially helpful status
     * messages
     **/
    public static boolean m_verbose = true;

    /**
     * ID number for next process to be loaded
     */
    private int m_nextProcessID = 1001;

    /**
     * The CPU the operating system is managing.
     **/
    private CPU m_CPU = null;

    /**
     * The RAM attached to the CPU.
     **/
    private RAM m_RAM = null;

    /**
     * Information about the currently running process
     */
    private ProcessControlBlock m_currProcess = null;

    /**
     * All the devices recognized by the OS
     */
    private Vector<DeviceInfo> m_devices = null;

    /**
     * All the Programs available to the operating system
     */
    private Vector<Program> m_programs = null;

    /**
     * List of all processes that are currently load into RAM
     */
    private Vector<ProcessControlBlock> m_processes = null;
    
    /**
     * List of all blocks of RAM not allocated to a process
     */
    private Vector<MemBlock> m_freeList = null;
    
    /**
     * reference to memory management unit
     */
    private MMU m_MMU = null;

    /*
     * ======================================================================
     * Constructors & Debugging
     * ----------------------------------------------------------------------
     */

    /**
     * The constructor does nothing special
     */
    public SOS(CPU c, RAM r, MMU mmu)
    {
        // Init member list
        m_CPU = c;
        m_RAM = r;
        m_MMU = mmu;
        //Initialize vectors
        m_programs = new Vector<Program>();
        m_processes = new Vector<ProcessControlBlock>();
        m_freeList = new Vector<MemBlock>();
       
        // OS becomes the trap handler
        m_CPU.registerTrapHandler(this);
        // Keeps track off all the devices recognized by OS
        m_devices = new Vector<DeviceInfo>();
        //TODO #10a HW 8 should NOT assume initial amount of free memory = size of total virtual memory (m_MMU or m_RAM?)
        // Initializes to all available RAM (exception being page table)
        m_freeList.add(new MemBlock(m_MMU.getNumPages(), m_MMU.getSize()));
        initPageTable();
    }// SOS ctor

    /**
     * Does a System.out.print as long as m_verbose is true
     **/
    public static void debugPrint(String s)
    {
        if (m_verbose)
        {
            System.out.print(s);
        }
    }

    /**
     * Does a System.out.println as long as m_verbose is true
     **/
    public static void debugPrintln(String s)
    {
        if (m_verbose)
        {
            System.out.println(s);
        }
    }

    /*
     * ======================================================================
     * Stack Management Methods
     * ----------------------------------------------------------------------
     */

    /**
     * pop
     * 
     * Pop a value off the stack directly using the stack pointer
     * 
     * @return the popped value
     */
    public int pop()
    {
        // stack is at the LIMIT of program
        // SP max value is LIMIT
        if (m_CPU.getSP() < m_CPU.getLIM())
        {
            // Move up RAM (popping)
            m_CPU.setSP(m_CPU.getSP() + 1);
            // Return value from stack
            return m_MMU.read(m_CPU.getSP());
        }
        // stack is empty
        else
        {
            // Create a system call to exit the program
            push(SYSCALL_EXIT);
            systemCall();

            // Should never reach this point
            return 0;
        }
    }

    /**
     * push
     * 
     * Pushes a values onto the stack directly using the stack pointer
     * 
     * @param val
     *            the value to push onto stack
     */
    public void push(int val)
    {
        // stack is at the LIMIT of program
        // SP max value is LIMIT size - 1 (0-indexed)
        if (m_CPU.getSP() > m_CPU.getBASE())
        {
            // Write value to stack
            m_MMU.write(m_CPU.getSP(), val);
            // "Increment" the stack pointer (from top, down)
            m_CPU.setSP(m_CPU.getSP() - 1);

        }
        // stack has overflowed past BASE
        else
        {
            // Create a system call to exit the program
            push(SYSCALL_EXIT);
            systemCall();
        }

    }

    /**
     * pushToProcess
     * 
     * pushes a value onto the provided program's stack
     * 
     * @param val
     *            the value to push onto the stack
     * @param pcb
     *            the process control block of process to which the value is
     *            pushed
     */
    public void pushToProcess(int val, ProcessControlBlock pcb)
    {
        // Get stack pointer value
        int sp = pcb.getRegisterValue(CPU.SP);
        // Write new data to
        m_MMU.write(sp, val);
        // Update processes' stack pointer
        pcb.setRegisterValue(CPU.SP, sp - 1);
    }

    /**
     * popFromProcess
     * 
     * pops a value from the provided program's stack Note: don't think this is
     * every used... ever...
     * 
     * @param pcb
     *            the process control block of process from which to pop value
     * 
     * @return the popped value
     */
    public int popFromProcess(ProcessControlBlock pcb)
    {
        // Get stack pointer value and increment before read
        int sp = pcb.getRegisterValue(CPU.SP) + 1;
        // Update processes' stack pointer
        pcb.setRegisterValue(CPU.SP, sp);
        // Read processes' popped value
        return m_MMU.read(sp);
    }

    

    /*======================================================================
     * Memory Block Management Methods
     *----------------------------------------------------------------------
     */
 

    
    /**
     * Allocates a block of memory with the requested size (if possible)
     * @param size is the requested memory block size
     * @return the base address of the memory block that is to be allocated
     */
    private int allocBlock(int size)
    {
    	//TODO #8 HW 8 (No changes made to this method yet, maybe none needed??)
    	int totalMem = 0;
    	for(MemBlock m : m_freeList)
    	{
    		//If perfect fit found, allocate block
    		if(m.getSize() == size)
    		{
    			m_freeList.remove(m);
    			return m.getAddr();
    		}
    		//If greater, allocate block, add new
    		// MemBlock with size equal to the difference
    		else if(m.getSize() > size)
    		 {
    			 m_freeList.remove(m);
    			 m_freeList.add(new MemBlock(m.getAddr() + size, m.getSize() - size));
    			 return m.getAddr();
    		 }
    		//If smaller, add size to total fragmented memory
    		 else
    		 {
    			 totalMem += m.getSize();
    		 }
    	}
    	//Not enough memory available
    	if(totalMem < size)
    	{
    		return -1;
    	}
    	//Total memory greater than size, defragment and 
    	// allocate as necessary
    	else
    	{
    		return(defragmentAndAllocate(size));
    	}
    }//allocBlock
    
    /**
     * Completely defragments memory and allocates the memory as needed
     *  by allocBlock
     * @param size is the block size we are allocating (from allocBlock)
     * @return the address to allocate the process to (from allocBlock)
     */
    private int defragmentAndAllocate(int size)
    {
    	Collections.sort(m_processes);
    	//TODO #10b HW 8
    	int nextBase = m_MMU.getNumPages();
    	for (ProcessControlBlock pi:m_processes)
    	{
    		pi.move(nextBase);
    		nextBase = pi.getRegisterValue(CPU.LIM);
    	}// for
    	m_freeList.removeAllElements();
    	m_freeList.add(new MemBlock(nextBase + size, m_MMU.getSize() - nextBase - size));
    	return nextBase;
    }//defragmentAndAllocate

    
    /**
     * Frees the memory block associated with the currently running
     * process
     */
    private void freeCurrProcessMemBlock()
    {
    	int baseAddr = m_currProcess.getRegisterValue(CPU.BASE);
    	int procSize = m_currProcess.getRegisterValue(CPU.LIM) - baseAddr; 
        m_freeList.add(new MemBlock(baseAddr, procSize));    
        memoryCheck();
    }//freeCurrProcessMemBlock
    
    /**
     * Checks for contiguous memory blocks and merges any that are 
     * found
     */
    private void memoryCheck()
    {
    	Collections.sort(m_freeList);
    	for(int i = 0;i < m_freeList.size()-1;i++)
    	{
    		MemBlock m1 = m_freeList.get(i);
    		MemBlock m2 = m_freeList.get(i+1);
    		int toCheck = m1.getAddr() + m1.getSize();
    		//
    		if(toCheck == m2.getAddr())
    		{
    			m_freeList.remove(m1);
    			m_freeList.remove(m2);
    			m_freeList.add(i, new MemBlock(m1.getAddr(), m1.getSize() + m2.getSize()));
    			i--;
    		}
    	}
    }
    
    /**
     * printMemAlloc                 *DEBUGGING*
     *
     * outputs the contents of m_freeList and m_processes to the console and
     * performs a fragmentation analysis.  It also prints the value in
     * RAM at the BASE and LIMIT registers.  This is useful for
     * tracking down errors related to moving process in RAM.
     *
     * SIDE EFFECT:  The contents of m_freeList and m_processes are sorted.
     *
     */
    private void printMemAlloc()
    {
        //If verbose mode is off, do nothing
        if (!m_verbose) return;

        //Print a header
        System.out.println("\n----------========== Memory Allocation Table ==========----------");
        
        //Sort the lists by address
        Collections.sort(m_processes);
        Collections.sort(m_freeList);

        //Initialize references to the first entry in each list
        MemBlock m = null;
        ProcessControlBlock pi = null;
        ListIterator<MemBlock> iterFree = m_freeList.listIterator();
        ListIterator<ProcessControlBlock> iterProc = m_processes.listIterator();
        if (iterFree.hasNext()) m = iterFree.next();
        if (iterProc.hasNext()) pi = iterProc.next();

        //Loop over both lists in order of their address until we run out of
        //entries in both lists
        while ((pi != null) || (m != null))
        {
            //Figure out the address of pi and m.  If either is null, then assign
            //them an address equivalent to +infinity
            int pAddr = Integer.MAX_VALUE;
            int mAddr = Integer.MAX_VALUE;
            if (pi != null)  pAddr = pi.getRegisterValue(CPU.BASE);
            if (m != null)  mAddr = m.getAddr();

            //If the process has the lowest address then print it and get the
            //next process
            if ( mAddr > pAddr )
            {
                int size = pi.getRegisterValue(CPU.LIM) - pi.getRegisterValue(CPU.BASE);
                System.out.print(" Process " + pi.processId +  " (addr=" + pAddr + " size=" + size + " words");
                System.out.print(" / " + (size / m_MMU.getPageSize()) + " pages)" );
                System.out.print(" @BASE=" + m_MMU.read(pi.getRegisterValue(CPU.BASE))
                                 + " @SP=" + m_MMU.read(pi.getRegisterValue(CPU.SP)));
                System.out.println();
                if (iterProc.hasNext())
                {
                    pi = iterProc.next();
                }
                else
                {
                    pi = null;
                }
            }//if
            else
            {
                //The free memory block has the lowest address so print it and
                //get the next free memory block
                System.out.println("    Open(addr=" + mAddr + " size=" + m.getSize() + ")");
                if (iterFree.hasNext())
                {
                    m = iterFree.next();
                }
                else
                {
                    m = null;
                }
            }//else
        }//while
            
        //Print a footer
        System.out.println("-----------------------------------------------------------------");
        
    }//printMemAlloc

    
    /*======================================================================
     * Virtual Memory Methods
     *----------------------------------------------------------------------
     */

   
    /**
     * Initializes the page table to the bottom of RAM.
     * Each page (number) initially corresponds to the same frame (number).
     */
    private void initPageTable()
    {
    	//TODO #7 HW 8
    	//Create page table at RAM location 0, so frames start
    	// right above the top of the page table.
        int frameLocation = m_MMU.getNumPages();
        for(int page = 0; page < m_MMU.getNumPages(); page++)
        {
        	m_RAM.write(page, frameLocation);
        	frameLocation += m_MMU.getPageSize();
        }
    }//initPageTable


    /**
     * printPageTable      *DEBUGGING*
     *
     * prints the page table in a human readable format
     *
     */
    private void printPageTable()
    {
        //If verbose mode is off, do nothing
        if (!m_verbose) return;

        //Print a header
        System.out.println("\n----------========== Page Table ==========----------");
        
        for(int i = 0; i < m_MMU.getNumPages(); i++)
        {
            int entry = m_RAM.read(i);
            int status = entry & m_MMU.getStatusMask();
            int frame = entry & m_MMU.getPageMask();

            System.out.println("" + i + "-->" + frame);
        }
        
        //Print a footer
        System.out.println("-----------------------------------------------------------------");

    }//printPageTable
    
    
    /*
     * ======================================================================
     * Device Management Methods
     * ----------------------------------------------------------------------
     */

    /**
     * registerDevice
     * 
     * adds a new device to the list of devices managed by the OS
     * 
     * @param dev
     *            the device driver
     * @param id
     *            the id to assign to this device
     * 
     */
    public void registerDevice(Device dev, int id)
    {
        m_devices.add(new DeviceInfo(dev, id));
    }// registerDevice

    /**
     * getDeviceInfo
     * 
     * Search the devices until your device is found Device id is not
     * necessarily the index of the device in vector
     * 
     * Try to find device by id == index. If that's not your device, search
     * entire vector
     * 
     * @param id
     *            the device id
     */
    public DeviceInfo getDeviceInfo(int id)
    {
        DeviceInfo dev = null;
        int ix = 0;

        /*
         * Check id by index for efficiency. If id is less than number of
         * devices registered, chances are, its id is equal to its location in
         * the devices vector (prevent out of bound exception)
         */
        if (id < m_devices.size())
        {
            // get device info
            dev = m_devices.get(id);

            // If returned DeviceInfo has the same id as
            // the id passed in, it is correct. Return it.
            if (dev.getId() == id)
            {
                return dev;
            }

        }

        // manually search through each object
        for (ix = 0; ix < m_devices.size(); ++ix)
        {
            dev = m_devices.get(ix);

            // If returned DeviceInfo has the same id as
            // the id passed in, it is correct. Return it.
            if (dev.getId() == id)
            {
                return dev;
            }
        }

        // No device with that id found
        return null;
    }

    /*
     * ======================================================================
     * Process Management Methods
     * ----------------------------------------------------------------------
     */

    /**
     * printProcessTable **DEBUGGING**
     * 
     * prints all the processes in the process table
     */
    private void printProcessTable()
    {
        debugPrintln("");
        debugPrintln("Process Table (" + m_processes.size() + " processes)");
        debugPrintln("======================================================================");
        for (ProcessControlBlock pi : m_processes)
        {
            debugPrintln("    " + pi);
        }// for
        debugPrintln("----------------------------------------------------------------------");

    }// printProcessTable

    /**
     * removeCurrentProcess
     * 
     * removes the current process from the processes vector and "unsets" the
     * m_currProcess var and frees the process' memory block
     */
    public void removeCurrentProcess()
    {
    	freeCurrProcessMemBlock();
        m_processes.remove(m_currProcess);
        m_currProcess = null;
        scheduleNewProcess();
    }// removeCurrentProcess

    /**
     * getRandomProcess
     * 
     * selects a non-Blocked process at random from the ProcessTable.
     * 
     * @return a reference to the ProcessControlBlock struct of the selected
     *         process -OR- null if no non-blocked process exists
     */
    ProcessControlBlock getRandomProcess()
    {
        // Calculate a random offset into the m_processes list
        int offset = ((int) (Math.random() * 2147483647)) % m_processes.size();

        
        
        // Iterate until a non-blocked process is found
        ProcessControlBlock newProc = null;
        for (int i = 0; i < m_processes.size(); i++)
        {
            newProc = m_processes.get((i + offset) % m_processes.size());
            if (!newProc.isBlocked())
            {
                return newProc;
            }
        }// for

        return null; // no processes are Ready
    }// getRandomProcess

    /**
     * getNextProcess
     *      
     * selects a non-Blocked process from the ProcessTable giving higher
     * priority to those which have idled the longest and have been
     * chosen to run the least amount of times (low numReady result).
     * 
     * @return the ProcessControlBlock of the next process to run
     * 
     */
    ProcessControlBlock getNextProcess()
    {    
        ProcessControlBlock newProc = null;
        double highestPriority = -1;
        for (ProcessControlBlock pi:m_processes)
        {
            // Assign priority based on ratio idle time to the number of times the process
            //  has entered the ready state
            double timeIdle = (m_CPU.getTicks() - pi.lastReadyTime);
            double priority = (double) timeIdle/pi.getNumReady();
            if (priority > highestPriority)
            {
                if (!pi.isBlocked())
                {
                    highestPriority = priority;
                    newProc=pi;
                }
            }
        }// for
        
        //No ready process found
        if (highestPriority < 0)
        {
            return null; // no processes are Ready
        }
        return newProc;
    }// getRandomProcess
    
    /**
     * createIdleProcess
     * 
     * creates a one instruction process that immediately exits. This is used to
     * buy time until device I/O completes and unblocks a legitimate process.
     * 
     */
    public void createIdleProcess()
    {
        int progArr[] =
        { 0, 0, 0, 0, // SET r0=0
                0, 0, 0, 0, // SET r0=0 (repeated instruction to account for
                // vagaries in student implementation of the CPU
                // class)
                10, 0, 0, 0, // PUSH r0
                15, 0, 0, 0 }; // TRAP
        
        //TODO #9 HW 8
        //Make the allocated address space an exact multiple of the MMU's page size
        int blockSize = m_MMU.getPageSize();
        while(blockSize < progArr.length)
        {
        	blockSize += blockSize;
        }
        // Initialize the starting position for this program
        int baseAddr = allocBlock(blockSize);
        if(baseAddr == -1)
        {
        	System.out.println("Could not allocate block of size " + blockSize + ". Exiting.");
        	System.exit(-1);
        }

        // Load the program into RAM
        for (int i = 0; i < progArr.length; i++)
        {
            m_MMU.write(baseAddr + i, progArr[i]);
        }

        // Save the register info from the current process (if there is one)
        if (m_currProcess != null)
        {
            m_currProcess.save(m_CPU);
        }

        //TODO Do these registers need to be + blockSize or will that mess them up because there won't be data there?
        // Set the appropriate registers
        m_CPU.setPC(baseAddr);
        m_CPU.setSP(baseAddr + progArr.length + 10);
        m_CPU.setBASE(baseAddr);
        m_CPU.setLIM(baseAddr + blockSize + 20);

        // Save the relevant info as a new entry in m_processes
        m_currProcess = new ProcessControlBlock(IDLE_PROC_ID);
        m_processes.add(m_currProcess);
    }// createIdleProcess

    /**
     * selectBlockedProcess
     * 
     * select a process to unblock that might be waiting to perform a given
     * action on a given device. This is a helper method for system calls and
     * interrupts that deal with devices.
     * 
     * @param dev
     *            the Device that the process must be waiting for
     * @param op
     *            the operation that the process wants to perform on the device.
     *            Use the SYSCALL constants for this value.
     * @param addr
     *            the address the process is reading from. If the operation is a
     *            Write or Open then this value can be anything
     * 
     * @return the process to unblock -OR- null if none match the given criteria
     */
    public ProcessControlBlock selectBlockedProcess(Device dev, int op, int addr)
    {
        ProcessControlBlock selected = null;
        for (ProcessControlBlock pi : m_processes)
        {
            if (pi.isBlockedForDevice(dev, op, addr))
            {
                selected = pi;
                break;
            }
        }// for

        return selected;
    }// selectBlockedProcess

    /**
     * scheduleNewProcess
     * 
     * save the currently running process' gen regs, SP, and PC into PCB.
     * selects a new process from the ProcessTable, makes it the currently
     * running process, and restores its gen regs, SP, and PC from PCB
     */
    public void scheduleNewProcess()
    {

        // If no more processes exist, exit
        if (m_processes.size() <= 0)
        {
            System.exit(SYSCALL_EXIT);
        }

        // Get a new "unblocked" process with non-random scheduling algorithm
        ProcessControlBlock temp = getNextProcess();

        // Get a new "unblocked" process with random scheduling algorithm (left in for testing ease)
        //ProcessControlBlock temp = getRandomProcess();
        
        // If the currently running process is returned, continue
        if (temp != null && m_currProcess != null && m_currProcess.equals(temp))
        {
            return;
        }
        
        // If the new process is not null (a non-blocked process is found)
        if (temp != null)
        {

            // save gen regs, SP, and 1PC into PCB
            if (m_currProcess != null)
            {
                m_currProcess.save(m_CPU);
            }

            // restore gen regs, SP, and PC from PCB
            temp.restore(m_CPU);

            m_currProcess = temp;
        }
        // If all processes are blocked
        else if (m_processes.size() > 0)
        {
            // Continue with all processes currently blocked
            createIdleProcess();
        }

    }// scheduleNewProcess

    /**
     * 
     * createProcess
     * 
     * reads a new program from the "pseudo filesystem", loads it into RAM and
     * makes it the currently running project
     * 
     * 
     * @param prog
     *            original pidgin code translated into assembly
     * @param allocSize
     *            default memory allocated size
     */
    public boolean createProcess(Program prog, int allocSize)
    {
    	// If current process is set, that process needs to be saved
        if (m_currProcess != null)
        {
            m_currProcess.save(m_CPU);
        }
        //TODO #9 HW 8
        //Make the allocated address space an exact multiple of the MMU's page size
        int blockSize = m_MMU.getPageSize();
        while(blockSize < allocSize)
        {
        	blockSize += blockSize;
        }
        
        int addr = allocBlock(blockSize);
        // If allocBlock fails, return to caller
        if(addr == -1)
        {
        	System.out.println("Could not allocate block of size " + blockSize);
        	return false;
        }
        
     // Return parsed pidgin code
        int[] program = prog.export();
        
       
        
        
        // Assign a base location to load program in RAM
        m_CPU.setBASE(addr);
        
        // setting limit to allocSize above current base
        m_CPU.setLIM(m_CPU.getBASE() + blockSize - 1);

        // Set PC register to start of program (at base)
        m_CPU.setPC(m_CPU.getBASE());

        // Set stack pointer to edge of allocated memory
        m_CPU.setSP(m_CPU.getLIM());
        
        // Write program to RAM
        for (int i = 0; i < program.length; i++)
        {
            m_MMU.write(m_CPU.getBASE() + i, program[i]);

        }

        // Create new process and increment process ID counter
        ProcessControlBlock tempProc = new ProcessControlBlock(m_nextProcessID);
        m_nextProcessID += 1;
        m_processes.add(tempProc);
        m_currProcess = tempProc;
        m_currProcess.save(m_CPU);
        printMemAlloc();
        return true;

    }// createProcess

    /*
     * ======================================================================
     * Program Management Methods
     * ----------------------------------------------------------------------
     */
    /**
     * addProgram
     * 
     * registers a new program with the simulated OS that can be used when the
     * current process makes an Exec system call. (Normally the program is
     * specified by the process via a filename but this is a simulation so the
     * calling process doesn't actually care what program gets loaded.)
     * 
     * @param prog
     *            the program to add
     * 
     */
    public void addProgram(Program prog)
    {
        m_programs.add(prog);
    }// addProgram

    /*
     * ======================================================================
     * Interrupt Handlers
     * ----------------------------------------------------------------------
     */
    /**
     * interruptIllegalMemoryAccess
     * 
     * Interrupt handles out-of-bounds/not-your-memory errors
     * 
     * @param addr
     *            the memory address being accessed
     * 
     */
    @Override
    public void interruptIllegalMemoryAccess(int addr)
    {
        System.out.println("Illegal Memory Access @" + addr);
        System.exit(0);
    }

    /**
     * interruptClock
     * 
     * interrupt to handle clock interrupts
     */
    @Override
    public void interruptClock()
    {
        SOS.debugPrintln("Clock Interrupt!");
        scheduleNewProcess();
    }

    /**
     * interruptDivideByZero
     * 
     * Interrupt to handle "divide by zero" errors
     */
    @Override
    public void interruptDivideByZero()
    {
        System.out.println("Can't Divide by Zero");
        System.exit(0);
    }

    /**
     * interruptIllegalInstruction
     * 
     * @param inst
     *            the invalid INSTRSIZE instruction
     */
    @Override
    public void interruptIllegalInstruction(int[] instr)
    {
        System.out.println("Illegal Instruction" + instr);
        System.exit(0);
    }

    /**
     * interruptIOReadComplete
     * 
     * is called whenever an I/O device has data for the CPU
     * 
     * @param devID
     *            the id of device that is returning data
     * @param addr
     *            the address of read location
     * @param data
     *            returned by device
     */
    @Override
    public void interruptIOReadComplete(int devID, int addr, int data)
    {
        // Find the blocked process waiting for data from I/O device
        ProcessControlBlock pcb = selectBlockedProcess(getDeviceInfo(devID)
                .getDevice(), SYSCALL_READ, addr);

        // Move process from waiting to ready state
        pcb.unblock();

        // Push read data to processes' stack
        pushToProcess(data, pcb);

        // Return success code to stack
        pushToProcess(SUCCESS, pcb);

    }

    /**
     * 
     * interruptIOWriteComplete
     * 
     * is called whenever an I/O device has finished writing its data
     * 
     * @param devID
     *            the id of device that is writing the data
     * @param addr
     *            the address of write location
     */
    @Override
    public void interruptIOWriteComplete(int devID, int addr)
    {
        // Find the blocked process waiting for write complete by I/O device
        ProcessControlBlock pcb = selectBlockedProcess(getDeviceInfo(devID)
                .getDevice(), SYSCALL_WRITE, addr);

        if (pcb == null)
            System.out.println("Write interrupt but no blocked devices");

        // Move process from waiting to ready state
        pcb.unblock();

        // Return success code to stack
        pushToProcess(SUCCESS, pcb);
    }

    /*
     * ======================================================================
     * System Calls
     * ----------------------------------------------------------------------
     */
    /**
     * systemCall
     * 
     * Interrupt request handler Figures out type of interrupt and deals
     * appropriately
     * 
     */
    @Override
    public void systemCall()
    {
        switch (pop())
        {
        case SYSCALL_EXIT:
            sysCallExitHandler();
            break;
        case SYSCALL_OUTPUT:
            sysCallOutputHandler();
            break;
        case SYSCALL_GETPID:
            sysCallGetPidHandler();
            break;
        case SYSCALL_COREDUMP:
            sysCallCoreDumpHandler();
            break;
        case SYSCALL_OPEN:
            syscallOpen();
            break;
        case SYSCALL_CLOSE:
            syscallClose();
            break;
        case SYSCALL_WRITE:
            syscallWrite();
            break;
        case SYSCALL_READ:
            syscallRead();
            break;
        case SYSCALL_EXEC:
            syscallExec();
            break;
        case SYSCALL_YIELD:
            syscallYield();
            break;
        }
    }

    /**
     * Handle Exit system call
     */
    private void sysCallExitHandler()
    {
        removeCurrentProcess();
    }

    /**
     * Handle send to output device system call
     */
    private void sysCallOutputHandler()
    {
        // Print out the last stack entry
        System.out.println("OUTPUT:" + pop());
    }

    /**
     * Handle program id system call
     */
    private void sysCallGetPidHandler()
    {
        // Push the program id to the stack for use after syscall
        push(m_currProcess.getProcessId());
    }

    /**
     * Handle core dump system call
     */
    private void sysCallCoreDumpHandler()
    {
        m_CPU.regDump();

        // Print out values of stack
        for (int ix = 3; ix > 0; --ix)
        {
            System.out.print("sval" + ix + "=" + pop() + " ");
        }
        System.out.println();

        // Create a system call to end process
        push(SYSCALL_EXIT);
        systemCall();
    }

    /**
     * Check to make sure device can be opened and open
     * 
     * 1. Device exists 2. Device is not sharable and does not contain a process
     */
    private void syscallOpen()
    {
        // find device from device id, which is popped off the stack
        DeviceInfo dev = getDeviceInfo(pop());

        // device doesn't exist
        if (dev == null)
        {
            push(ERROR_DEV_DNE);
        }
        // a non-sharable device is already opened by a different process
        else if (!dev.getDevice().isSharable() && (dev.procs.size() > 0)
                && !dev.getDevice().isAvailable())
        {

            // Add process to list of processes that need to open device
            dev.addProcess(m_currProcess);
            m_currProcess.block(m_CPU, dev.getDevice(), SYSCALL_OPEN, 1234);

            // Push current information back to stack to repeat this operation
            // after the process becomes unblocked
            push(dev.getId());
            push(SYSCALL_OPEN);
            m_CPU.setPC(m_CPU.getPC() - CPU.INSTRSIZE);

            scheduleNewProcess();
        }
        // successfully open the device
        else
        {
            dev.addProcess(m_currProcess);
            push(SUCCESS);
        }
    }

    /**
     * Check to make sure device can be closed and close
     * 
     * 1. Device exists 2. Device is open
     */
    private void syscallClose()
    {
        // find device from device id, which is popped off the stack
        DeviceInfo dev = getDeviceInfo(pop());
        ProcessControlBlock proc = null;

        // device doesn't exist
        if (dev == null)
        {
            push(ERROR_DEV_DNE);
        }
        // device is closed
        else if (!dev.containsProcess(m_currProcess))
        {
            push(ERROR_DEV_NOT_OPEN);
        }
        // successfully closed device
        else
        {
            // Remove process from open processes vector in the device
            dev.removeProcess(m_currProcess);

            proc = selectBlockedProcess(dev.getDevice(), SYSCALL_OPEN, 1234);

            if (proc != null)
            {
                proc.unblock();
            }
            push(SUCCESS);
        }
    }

    /**
     * Check to make sure device can be written to and write
     * 
     * 1. Device exists 2. Device is open 3. Device can be written to
     */
    private void syscallWrite()
    {
        // Get values off of stack
        int val = pop();
        int address = pop();
        int devNum = pop();

        // find device from device id, which is popped off the stack
        DeviceInfo dev = getDeviceInfo(devNum);

        // device doesn't exist
        if (dev == null)
        {
            push(ERROR_DEV_DNE);
        }
        // you must open a device in order to write
        else if (!dev.containsProcess(m_currProcess))
        {
            push(ERROR_DEV_NOT_OPEN);
        }
        // device must be writable
        else if (!dev.getDevice().isWriteable())
        {
            push(ERROR_DEV_READONLY);
        }
        // device is not available
        else if (!dev.getDevice().isAvailable())
        {

            // Push current information back to stack to repeat this operation
            // after the process becomes unblocked
            push(devNum);
            push(address);
            push(val);
            push(SYSCALL_WRITE);
            m_CPU.setPC(m_CPU.getPC() - CPU.INSTRSIZE);

            // Move current process to ready state and start new process
            scheduleNewProcess();
        }
        // successfully write to device
        else
        {
            dev.getDevice().write(address, val);
            // Block device for I/O
            m_currProcess.block(m_CPU, dev.getDevice(), SYSCALL_WRITE, address);
            scheduleNewProcess();
        }
    }

    /**
     * Check to make sure device can be read from and read data
     * 
     * 1. Device exists 2. Device is open 3. Device can be written to
     */
    private void syscallRead()
    {
        // Get values off of stack
        int address = pop();
        int devNum = pop();

        // Find device from device id, which is popped off the stack
        DeviceInfo dev = getDeviceInfo(devNum);

        // device doesn't exist
        if (dev == null)
        {
            push(ERROR_DEV_DNE);
        }
        // you must open a device in order to read
        else if (!dev.containsProcess(m_currProcess))
        {
            push(ERROR_DEV_NOT_OPEN);
        }
        // device must be readable
        else if (!dev.getDevice().isReadable())
        {
            push(ERROR_DEV_WRITEONLY);
        }
        else if (!dev.getDevice().isAvailable())
        {

            // Push current information back to stack to repeat this operation
            // after the process becomes unblocked
            push(devNum);
            push(address);
            push(SYSCALL_READ);
            m_CPU.setPC(m_CPU.getPC() - CPU.INSTRSIZE);

            // Move current process to ready state and start new process
            scheduleNewProcess();
        }
        // successfully read from device and value pushed to stack
        else
        {
            // push value read by device onto process stack
            dev.getDevice().read(address);

            // Block device and wait for I/O completion
            m_currProcess.block(m_CPU, dev.getDevice(), SYSCALL_READ, address);
            scheduleNewProcess();
        }
    }

    /**
     * syscallExec
     * 
     * creates a new process. The program used to create that process is chosen
     * semi-randomly from all the programs that have been registered with the OS
     * via {@link #addProgram}. Limits are put into place to ensure that each
     * process is run an equal number of times. If no programs have been
     * registered then the simulation is aborted with a fatal error.
     * 
     */
    private void syscallExec()
    {
        // If there is nothing to run, abort. This should never happen.
        if (m_programs.size() == 0)
        {
            System.err.println("ERROR!  syscallExec has no programs to run.");
            System.exit(-1);
        }

        // find out which program has been called the least and record how many
        // times it has been called
        int leastCallCount = m_programs.get(0).callCount;
        for (Program prog : m_programs)
        {
            if (prog.callCount < leastCallCount)
            {
                leastCallCount = prog.callCount;
            }
        }

        // Create a vector of all programs that have been called the least
        // number
        // of times
        Vector<Program> cands = new Vector<Program>();
        for (Program prog : m_programs)
        {
            cands.add(prog);
        }

        // Select a random program from the candidates list
        Random rand = new Random();
        int pn = rand.nextInt(m_programs.size());
        Program prog = cands.get(pn);

        // Determine the address space size using the default if available.
        // Otherwise, use a multiple of the program size.
        int allocSize = prog.getDefaultAllocSize();
        if (allocSize <= 0)
        {
            allocSize = prog.getSize() * 2;
        }

        // Load the program into RAM
        if(createProcess(prog, allocSize))
        {
        	//m_CPU.setPC(m_CPU.getPC() - CPU.INSTRSIZE);
        }
        

    }// syscallExec

    /**
     * syscallYield
     * 
     * a process tells the OS that it can sleep for a while and the OS schedules
     * a new process to run instead
     */
    private void syscallYield()
    {
        scheduleNewProcess();
    }// syscallYield

    /*
     * ======================================================================
     * Nested Classes
     * ----------------------------------------------------------------------
     */

    /**
     * class ProcessControlBlock
     * 
     * This class contains information about a currently active process.
     */
    private class ProcessControlBlock implements
    Comparable<ProcessControlBlock>
    {
        /**
         * a unique id for this process
         */
        private int processId = 0;
        
        /**
         * These are the process' current registers. If the process is in the
         * "running" state then these are out of date
         */
        private int[] registers = null;

        /**
         * If this process is blocked a reference to the Device is stored here
         */
        private Device blockedForDevice = null;

        /**
         * If this process is blocked a reference to the type of I/O operation
         * is stored here (use the SYSCALL constants defined in SOS)
         */
        private int blockedForOperation = -1;

        /**
         * If this process is blocked reading from a device, the requested
         * address is stored here.
         */
        private int blockedForAddr = -1;

        /**
         * the time it takes to load and save registers, specified as a number
         * of CPU ticks
         */
        private static final int SAVE_LOAD_TIME = 30;

        /**
         * Used to store the system time when a process is moved to the Ready
         * state.
         */
        private int lastReadyTime = -1;

        /**
         * Used to store the number of times this process has been in the ready
         * state
         */
        private int numReady = 0;

        /**
         * Used to store the maximum starve time experienced by this process
         */
        private int maxStarve = -1;

        /**
         * Used to store the average starve time for this process
         */
        private double avgStarve = 0;

        /**
         * constructor
         * 
         * @param pid
         *            a process id for the process. The caller is responsible
         *            for making sure it is unique.
         */
        public ProcessControlBlock(int pid)
        {
            this.processId = pid;
        }

        /**
         * @return the current process' id
         */
        public int getProcessId()
        {
            return this.processId;
        }

        /**
         * @return the last time this process was put in the Ready state
         */
        public long getLastReadyTime()
        {
            return lastReadyTime;
        }
        
        /**
         * getNumReady()
         * Returns number of times the process has been moved to the ready state
         * 
         * @return the number of times the process has been made ready
         */
        public int getNumReady()
        {
            return numReady;
        }
        
        /**
         * save
         * 
         * saves the current CPU registers into this.registers
         * 
         * @param cpu
         *            the CPU object to save the values from
         */
        public void save(CPU cpu)
        {
            // A context switch is expensive. We simulate that here by
            // adding ticks to m_CPU
            m_CPU.addTicks(SAVE_LOAD_TIME);

            // Save the registers
            int[] regs = cpu.getRegisters();
            this.registers = new int[CPU.NUMREG];
            for (int i = 0; i < CPU.NUMREG; i++)
            {
                this.registers[i] = regs[i];
            }

            // Assuming this method is being called because the process is
            // moving
            // out of the Running state, record the current system time for
            // calculating starve times for this process. If this method is
            // being called for a Block, we'll adjust lastReadyTime in the
            // unblock method.
            numReady++;
            lastReadyTime = m_CPU.getTicks();

        }// save

        /**
         * restore
         * 
         * restores the saved values in this.registers to the current CPU's
         * registers
         * 
         * @param cpu
         *            the CPU object to restore the values to
         */
        public void restore(CPU cpu)
        {
            // A context switch is expensive. We simluate that here by
            // adding ticks to m_CPU
            m_CPU.addTicks(SAVE_LOAD_TIME);

            // Restore the register values
            int[] regs = cpu.getRegisters();
            for (int i = 0; i < CPU.NUMREG; i++)
            {
                regs[i] = this.registers[i];
            }

            // Record the starve time statistics
            int starveTime = m_CPU.getTicks() - lastReadyTime;
            if (starveTime > maxStarve)
            {
                maxStarve = starveTime;
            }
            double d_numReady = (double) numReady;
            avgStarve = avgStarve * (d_numReady - 1.0) / d_numReady;
            avgStarve = avgStarve + (starveTime * (1.0 / d_numReady));
        }// restore

        /**
         * unblock
         * 
         * moves this process from the Blocked (waiting) state to the Ready
         * state.
         * 
         */
        public void unblock()
        {
            // Reset the info about the block
            blockedForDevice = null;
            blockedForOperation = -1;
            blockedForAddr = -1;

            // Assuming this method is being called because the process is
            // moving
            // from the Blocked state to the Ready state, record the current
            // system time for calculating starve times for this process.
            lastReadyTime = m_CPU.getTicks();

        }// unblock

        /**
         * block
         * 
         * blocks the current process to wait for I/O. This includes saving the
         * process' registers. The caller is responsible for calling
         * {@link CPU#scheduleNewProcess} after calling this method.
         * 
         * @param cpu
         *            the CPU that the process is running on
         * @param dev
         *            the Device that the process must wait for
         * @param op
         *            the operation that the process is performing on the
         *            device. Use the SYSCALL constants for this value.
         * @param addr
         *            the address the process is reading from. If the operation
         *            is a Write or Open then this value can be anything
         */
        public void block(CPU cpu, Device dev, int op, int addr)
        {
            blockedForDevice = dev;
            blockedForOperation = op;
            blockedForAddr = addr;

        }// block

        /**
         * isBlocked
         * 
         * @return true if the process is blocked
         */
        public boolean isBlocked()
        {
            return (blockedForDevice != null);
        }// isBlocked

        /**
         * isBlockedForDevice
         * 
         * Checks to see if the process is blocked for the given device,
         * operation and address. If the operation is not an open, the given
         * address is ignored.
         * 
         * @param dev
         *            check to see if the process is waiting for this device
         * @param op
         *            check to see if the process is waiting for this operation
         * @param addr
         *            check to see if the process is reading from this address
         * 
         * @return true if the process is blocked by the given parameters
         */
        public boolean isBlockedForDevice(Device dev, int op, int addr)
        {
            if ((blockedForDevice == dev) && (blockedForOperation == op))
            {
                if (op == SYSCALL_OPEN)
                {
                    return true;
                }

                if (addr == blockedForAddr)
                {
                    return true;
                }
            }// if

            return false;
        }// isBlockedForDevice

        /**
         * compareTo
         * 
         * compares this to another ProcessControlBlock object based on the BASE
         * addr register. Read about Java's Collections class for info on how
         * this method can be quite useful to you.
         */
        public int compareTo(ProcessControlBlock pi)
        {
            return this.registers[CPU.BASE] - pi.registers[CPU.BASE];
        }

        /**
         * getRegisterValue
         * 
         * Retrieves the value of a process' register that is stored in this
         * object (this.registers).
         * 
         * @param idx
         *            the index of the register to retrieve. Use the constants
         *            in the CPU class
         * @return one of the register values stored in in this object or -999
         *         if an invalid index is given
         */
        public int getRegisterValue(int idx)
        {
            if ((idx < 0) || (idx >= CPU.NUMREG))
            {
                return -999; // invalid index
            }

            return this.registers[idx];
        }// getRegisterValue

        /**
         * setRegisterValue
         * 
         * Sets the value of a process' register that is stored in this object
         * (this.registers).
         * 
         * @param idx
         *            the index of the register to set. Use the constants in the
         *            CPU class. If an invalid index is given, this method does
         *            nothing.
         * @param val
         *            the value to set the register to
         */
        public void setRegisterValue(int idx, int val)
        {
            if ((idx < 0) || (idx >= CPU.NUMREG))
            {
                return; // invalid index
            }

            this.registers[idx] = val;
        }// setRegisterValue

        /**
         * overallAvgStarve
         * 
         * @return the overall average starve time for all currently running
         *         processes
         * 
         */
        public double overallAvgStarve()
        {
            double result = 0.0;
            int count = 0;
            for (ProcessControlBlock pi : m_processes)
            {
                if (pi.avgStarve > 0)
                {
                    result = result + pi.avgStarve;
                    count++;
                }
            }
            if (count > 0)
            {
                result = result / count;
            }

            return result;
        }// overallAvgStarve
        
        
        /**
         * @param newBase the new base (virtual) address this process is to be moved to
         * @return true if this process was moved, false if not
         */
        public boolean move(int newBase)
        {
        	//TODO #8 HW 8
        	if(newBase > m_MMU.getSize() || newBase < 0)
        	{
        		return false;
        	}
        	//Get new page number to move to
        	int page = 0;
        	int oldBase = this.getRegisterValue(CPU.BASE);
        	for(int i = 0; i < m_MMU.getSize(); i += m_MMU.getPageSize())
        	{
        		if(newBase > i && newBase < i + m_MMU.getPageSize())
        		{
        			m_RAM.write(page, m_MMU.read(oldBase));
        		}
        		else
        		{
        			page++;
        		}
        	}
        	debugPrintln("Process + " + this.getProcessId() + " moved from " + oldBase + " to " + newBase + ".");
        	return true;
        	
        	
        	
        	
        	//TODO UNNECESSARY CODE NOW!
        	
        	/*int oldLim = this.getRegisterValue(CPU.LIM);
        	int oldStack = this.getRegisterValue(CPU.SP);
        	int oldCount = this.getRegisterValue(CPU.PC);
        	int change = newBase - oldBase;
        	int dataLocation = newBase;
        	//Adjust the process' register values & move it
        	for(int i = oldBase;i < oldLim; i++, dataLocation++)
        	{
        		int data = m_MMU.read(i);
        		//Invalid memory location
        		if(dataLocation < 0 || dataLocation >= m_MMU.getSize())
        		{
        			return false;
        		}
        		//TODO NO COPY TO PHYSICAL RAM
        		//m_MMU.write(dataLocation, data);
        	}

        	//If current process adjust CPU registers as well
        	if(this == m_currProcess)
        	{
        		m_CPU.setBASE(newBase);
        		m_CPU.setLIM(change + oldLim);
        		m_CPU.setSP(change + oldStack);
        		m_CPU.setPC(change + oldCount);
        		save(m_CPU);
        	}
        	//Not current process, set registers
        	else
        	{
        		this.setRegisterValue(CPU.BASE, newBase);
        		this.setRegisterValue(CPU.LIM, change + oldLim);
        		this.setRegisterValue(CPU.SP, change + oldStack);
        		this.setRegisterValue(CPU.PC, change + oldCount);
        	}
        	debugPrintln("Process " + this.getProcessId() + " moved from " + oldBase + " to " + newBase);
        	return true;*/
        }//move
        

        /**
         * toString **DEBUGGING**
         * 
         * @return a string representation of this class
         */
        public String toString()
        {
            // Print the Process ID and process state (READY, RUNNING, BLOCKED)
            String result = "Process id " + processId + " ";
            if (isBlocked())
            {
                result = result + "is BLOCKED for ";
                // Print device, syscall and address that caused the BLOCKED
                // state
                if (blockedForOperation == SYSCALL_OPEN)
                {
                    result = result + "OPEN";
                } else
                {
                    result = result + "WRITE @" + blockedForAddr;
                }
                for (DeviceInfo di : m_devices)
                {
                    if (di.getDevice() == blockedForDevice)
                    {
                        result = result + " on device #" + di.getId();
                        break;
                    }
                }
                result = result + ": ";
            } else if (this == m_currProcess)
            {
                result = result + "is RUNNING: ";
            } else
            {
                result = result + "is READY: ";
            }

            // Print the register values stored in this object. These don't
            // necessarily match what's on the CPU for a Running process.
            if (registers == null)
            {
                result = result + "<never saved>";
                return result;
            }

            for (int i = 0; i < CPU.NUMGENREG; i++)
            {
                result = result + ("r" + i + "=" + registers[i] + " ");
            }// for
            result = result + ("PC=" + registers[CPU.PC] + " ");
            result = result + ("SP=" + registers[CPU.SP] + " ");
            result = result + ("BASE=" + registers[CPU.BASE] + " ");
            result = result + ("LIM=" + registers[CPU.LIM] + " ");

            // Print the starve time statistics for this process
            result = result + "\n\t\t\t";
            result = result + " Max Starve Time: " + maxStarve;
            result = result + " Avg Starve Time: " + avgStarve;

            return result;
        }// toString

    }// class ProcessControlBlock

    /**
     * class DeviceInfo
     * 
     * This class contains information about a device that is currently
     * registered with the system.
     */
    private class DeviceInfo
    {
        /** every device has a unique id */
        private int id;
        /** a reference to the device driver for this device */
        private Device device;
        /** a list of processes that have opened this device */
        private Vector<ProcessControlBlock> procs;

        /**
         * constructor
         * 
         * @param d
         *            a reference to the device driver for this device
         * @param initID
         *            the id for this device. The caller is responsible for
         *            guaranteeing that this is a unique id.
         */
        public DeviceInfo(Device d, int initID)
        {
            this.id = initID;
            this.device = d;
            this.procs = new Vector<ProcessControlBlock>();
        }

        /** @return the device's id */
        public int getId()
        {
            return this.id;
        }

        /** @return this device's driver */
        public Device getDevice()
        {
            return this.device;
        }

        /** Register a new process as having opened this device */
        public void addProcess(ProcessControlBlock pi)
        {
            procs.add(pi);
        }

        /** Register a process as having closed this device */
        public void removeProcess(ProcessControlBlock pi)
        {
            procs.remove(pi);
        }

        /** Does the given process currently have this device opened? */
        public boolean containsProcess(ProcessControlBlock pi)
        {
            return procs.contains(pi);
        }

        /** Is this device currently not opened by any process? */
        public boolean unused()
        {
            return procs.size() == 0;
        }

    }// class DeviceInfo
    
    /**
     * class MemBlock
     *
     * This class contains relevant info about a memory block in RAM.
     *
     */
    private class MemBlock implements Comparable<MemBlock>
    {
        /** the address of the block */
        private int m_addr;
        /** the size of the block */
        private int m_size;

        /**
         * ctor does nothing special
         */
        public MemBlock(int addr, int size)
        {
            m_addr = addr;
            m_size = size;
        }

        /** accessor methods */
        public int getAddr() { return m_addr; }
        public int getSize() { return m_size; }
        
        /**
         * compareTo              
         *
         * compares this to another MemBlock object based on address
         */
        public int compareTo(MemBlock m)
        {
            return this.m_addr - m.m_addr;
        }

    }//class MemBlock

};// class SOS
