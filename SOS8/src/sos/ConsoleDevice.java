package sos;

/**
 * This class simulates a simple, sharable write-only device.
 * 
 * @see Sim
 * @see CPU
 * @see SOS
 * @see Device
 */
public class ConsoleDevice implements Device, Runnable {
	private int m_maxLatency = 1000;   // maximum latency in ns
	private int m_minLatency = 500;    // minimum latnecy in ns
	private int m_Id = -999;           // the OS assigned device ID
	private boolean m_request = false; // is the device currently processing a
									   // request?
	private int m_addr = 0;            // address to write to
	private int m_data = 0;            // data associated with the current request
	private InterruptController m_IC;  // reference to the interrupt controller
	
	/**
	 * This constructor uses the default values for latency)
	 */
	public ConsoleDevice(InterruptController ic) {
		m_IC = ic;
	}
	
	/**
	 * This constructor expects values for the minimum and maximum latency
	 * of this device expressed as a number of nanoseconds
	 */
	public ConsoleDevice(InterruptController ic, int min, int max) {
		// If latencies are out of order swap them
		if (min > max) {
			int tmp = max;
			max = min;
			min = tmp;
		}
		
		m_minLatency = min;
		m_maxLatency = max;
		m_IC = ic;
	}// ctor
	
	/**
	 * getId
	 * 
	 * @return the device id of this device
	 */
	@Override
	public int getId() {
		return m_Id;
	}
	
	/**
	 * setId
	 * 
	 * sets the device id of this device
	 * 
	 * @param id
	 *            the new id
	 */
	@Override
	public void setId(int id) {
		m_Id = id;
	}
	
	/**
	 * isSharable
	 * 
	 * This device can be used simultaneously by multiple processes
	 * 
	 * @return true
	 */
	@Override
	public boolean isSharable() {
		return true;
	}
	
	/**
	 * isAvailable
	 * 
	 * this device is available if no requests are currently being processed
	 */
	@Override
	public boolean isAvailable() {
		return !m_request;
	}
	
	/**
	 * isReadable
	 * 
	 * @return whether this device can be read from (true/false)
	 */
	@Override
	public boolean isReadable() {
		return false;
	}
	
	/**
	 * isWriteable
	 * 
	 * @return whether this device can be written to (true/false)
	 */
	@Override
	public boolean isWriteable() {
		return true;
	}
	
	/**
	 * read
	 * 
	 * not implemented
	 * 
	 */
	@Override
	public int read(int addr /* not used */) {
		// This method should never be called
		return -1;
	}// read
	
	/**
	 * write
	 * 
	 * method records a request for service from the device and as such is
	 * analagous to setting a value in a register on the device's controller.
	 * As a result, the function does not check to make sure that the
	 * device is ready for this request (that's the OS's job).
	 */
	@Override
	public void write(int addr /* not used */, int data) {
		m_addr = addr;
		m_data = data;
		m_request = true;
	}
	
	/**
	 * run
	 * 
	 * This method represents the device + controller. It watches for requests
	 * (via m_request and m_data) and handles them. It also inserts a random
	 * latency to simulate the amount of time required.
	 * 
	 * (I have no idea whether the default latency setting (500-1000 ns) is at
	 * all realistic and, of course, the time spent calling System.out.println
	 * and Math.random() probably overshadows it. If someone has info on raw
	 * video latencies I will change this code to reflect that data.)
	 */
	@Override
	public void run() {
		// Device runs until program ends
		while (true) {
			// While there is no request to process, yield CPU to another thread
			while (!m_request) {
				Thread.yield();
			}
			
			// We've received a request. Sleep to simulate the latency
			try {
				int rn = (int) (Math.random() * 2147483647); // random #
				int latency = (rn % (m_maxLatency - m_minLatency))
						+ m_minLatency;
				Thread.sleep(latency / 1000, latency % 1000);
			}
			catch (InterruptedException e) {
			} // should never happen
			
			// print the data
			System.out.println("\nCONSOLE: " + m_data);
			
			// Notify the CPU of completed operation
			m_IC.putData(InterruptController.INT_WRITE_DONE, m_Id, m_addr, -999);
			
			// Make the device available for another request
			m_request = false;
		}// while
	}// run
	
}// class ConsoleDevice
