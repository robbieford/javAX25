/*
 * Audio FSK modem for AX25 (1200 Baud, 1200/2200Hz).
 * 
 * Robert Campbell and Michael Harriman 2014 Adapted from S. Toledo
 * 
 *      This program is free software; you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation; either version 2 of the License, or
 *      (at your option) any later version.
 *
 *      This program is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with this program; if not, write to the Free Software
 *      Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package sivantoledo.ax25;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;


public class ThreadedGoertzelMaxClockingDemodulator extends PacketDemodulator // implements
																// HalfduplexSoundcardClient
{
	/**
	 * Class name items...
	 */
	private final static String PRECLOCKING = "GoertzelPreclockingDemodulator";
	public String getDemodulatorName() {
		return PRECLOCKING + "-w_emphasis_" + emphasis;
	}

	private float samplesPerBaud;
	private int maxPacketLengthInSamples;
    private int minPacketLengthInSamples;
    private int samplesSinceLastProcess;
	private ArrayList<Float> sampleArray;
	private static final int MINIMUM_PACKET_BYTES = 16;
	private static final int MAXIMUM_PACKET_BYTES = 256; // MTU for AX25
	private ExecutorService eservice;
	private List<Future> futuresList;

	public ThreadedGoertzelMaxClockingDemodulator(int sample_rate, int filter_length)
			throws Exception {
		this(sample_rate, filter_length, 6, null);
	}

	public ThreadedGoertzelMaxClockingDemodulator(int sample_rate, int filter_length,
			int emphasis, PacketHandler h) throws Exception {
		super(sample_rate, filter_length, emphasis, h);

		int nrOfProcessors = Runtime.getRuntime().availableProcessors();
		eservice = Executors.newFixedThreadPool(nrOfProcessors);

		sampleArray = new ArrayList<Float>();

		this.samplesPerBaud = (float) sample_rate / 1200.0f;
	    this.maxPacketLengthInSamples = Math.round(samplesPerBaud) * MAXIMUM_PACKET_BYTES * 8;
	    this.minPacketLengthInSamples = Math.round(samplesPerBaud) * MINIMUM_PACKET_BYTES * 8;
	    samplesSinceLastProcess = 0;
	    futuresList = new ArrayList<Future>();
	}

	/**
	 * 1.) Filter the data with the "emphasis" filter 2.) Capture a whole packet
	 * (look for flags) 3.) Post process the captured packet... a.) Derivative
	 * to find vvv (Removes DC bias problem and emphasis problem) b.) Zero
	 * crossing to see transitions c.) Figure out which sample corresponds to
	 * clocking d.) Use clocking to do correlation. (per baud)
	 */

	protected void addSamplePrivate(float sample) {
		sampleArray.add(sample);
		samplesSinceLastProcess++;

		if (sampleArray.size() > maxPacketLengthInSamples)
		{
			if(samplesSinceLastProcess > minPacketLengthInSamples)
			{
				processPacket(new ArrayList<Float>(sampleArray));
				samplesSinceLastProcess = 0;
			}
			sampleArray.remove(0);
		}
	}

	
	/**
	 * Once we have enough data actually try and demodulate the packet using this overly
	 * complicated method.
	 */
	private void processPacket(ArrayList<Float> samples) {
		for (int i = 0; i <= Math.round(samplesPerBaud); i ++)
		{
			futuresList.add(eservice.submit(new MaxClockingThread(samples, i, sample_rate, samplesPerBaud, handler)));
			//			MaxClockingThread maxThread = new MaxClockingThread(samples, i, sample_rate, samplesPerBaud, handler);
			//			maxThread.run();
//			byte[] taskResult;
//			for(Future future:futuresList) {
//				try {
//					taskResult = (byte[]) future.get();
//					handler.handlePacket(taskResult);
//					futuresList.remove(future);
//				}
//				catch (InterruptedException e) {}
//				catch (ExecutionException e) {}
//			}
		}
	}
}
