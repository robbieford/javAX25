package sivantoledo.ax25;

import java.util.ArrayList;
import java.util.concurrent.*;

import sivantoledo.ax25.PacketDemodulator.Frequency;

public class MaxClockingThread implements Callable<byte[]> {
	
	private ArrayList<Float> sampleArray;
	private int sampleRate;
	private float samplesPerBaud;
	private float normalized1200Freq;
    private float normalized2200Freq;
    private float coeff1200;
    private float coeff2200;
    private int combOffset;
	
	public MaxClockingThread(ArrayList<Float> data, int comb, float sampleRate, float samplesPerBaud, PacketHandler h)
	{
		this.sampleArray = data;
		this.sampleRate = Math.round(sampleRate);
		this.samplesPerBaud = samplesPerBaud;
		this.combOffset = comb;
	    this.normalized1200Freq = 1200f/sampleRate;
	    this.normalized2200Freq = 2200f/sampleRate;
	    this.coeff1200 = (float) (2*Math.cos(2*Math.PI*normalized1200Freq));
	    this.coeff2200 = (float) (2*Math.cos(2*Math.PI*normalized2200Freq));
	    this.handler = h;
	}
	
	public void run()
	{
		generatePacket(getFrequencySymbolList());
	}

	private class InternalPacketHandler implements PacketHandler
	{
		private byte[] packet = null;
		private CountDownLatch latch;

		public InternalPacketHandler()
		{
			latch = new CountDownLatch(1);
		}
		
		@Override
		public void handlePacket(byte[] packet) {
			this.packet = packet;
			latch.countDown();
		}
		public byte[] getResult() throws InterruptedException
		{
			latch.await();
			return packet;
		}
	}
	public byte[] call()
	{
		handler = new InternalPacketHandler();
		generatePacket(getFrequencySymbolList());
		
		while(true)
		{
			try
			{
				byte[] res = ((InternalPacketHandler)handler).getResult();
				return res;
			}
			catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	/**
	 * Taking a frequency list f_1200/f_2200 actually put together the packet...
	 * @param frequencySymbolList
	 */
	private void generatePacket(ArrayList<Frequency> frequencySymbolList) {
		Frequency lastFreq = frequencySymbolList.get(0);
		int bauds = 0;

		for (int i = 1; i < frequencySymbolList.size(); i++) {
			bauds++;
			if (frequencySymbolList.get(i) != lastFreq) {
//				System.out.println(bauds + "," + lastFreq);
				handleDemodulatedBits(bauds);
				bauds = 0;
				lastFreq = frequencySymbolList.get(i);
			}
		}
	}

	/**
	 * Using the calculated data compose a list of the best guess of frequencies
	 * for each bit period of this packet. 
	 * @param combOffset
	 * @param frequenciesFromDerivative
	 * @param derivativeCrossingPositions
	 * @return
	 */
	private ArrayList<Frequency> getFrequencySymbolList() {
		ArrayList<Frequency> frequencies = new ArrayList<Frequency>();

		for (Float i = new Float(combOffset); i < sampleArray.size(); i += samplesPerBaud) {
			Frequency freq = getFrequency(i, i + samplesPerBaud, sampleArray);
			if (freq != null) {
				frequencies.add(freq);
			}
		}

		return frequencies;
	}

	/**
	 * Get the average frequency for this bit period. Using zero crossings.
	 * 
	 * @param start
	 * @param end
	 * @param frequenciesFromDerivative
	 * @param derivativeCrossingPositions
	 * @return
	 */
	private Frequency getFrequency(float start, float end,
			ArrayList<Float> sampleArray) {
		int startIdx = Math.round(start);
		int endIdx = Math.round(end);

		float s1200 = 0;
		float s2200 = 0;
	    float s1200_prev = 0;
	    float s1200_prev2 = 0;
	    float s2200_prev = 0;
	    float s2200_prev2 = 0;
	    for (int i = startIdx; i <= endIdx && i < sampleArray.size(); i ++)
	    {
	       s1200 = sampleArray.get(i)+coeff1200 * s1200_prev - s1200_prev2;
	       s1200_prev2 = s1200_prev;
	       s1200_prev = s1200;
	       s2200 = sampleArray.get(i)+coeff2200 * s2200_prev - s2200_prev2;
	       s2200_prev2 = s2200_prev;
	       s2200_prev = s2200;
	    }
	    
	    float power = (s1200_prev2*s1200_prev2+s1200_prev*s1200_prev-coeff1200*s1200_prev*s1200_prev2) -
	    		(s2200_prev2*s2200_prev2+s2200_prev*s2200_prev-coeff2200*s2200_prev*s2200_prev2);
	    //System.out.println(power + "  ");
	    
	    Frequency freq = power > 0 ? Frequency.f_1200 : Frequency.f_2200;
	    
		return freq;
	}

	private enum State {
		WAITING, JUST_SEEN_FLAG, DECODING
	};

	// Packet Construction variables
	private int data, bitcount;
	private Packet packet; // received packet
	private PacketHandler handler;
	private State state = State.WAITING;
	protected int emphasis;
	private int flag_count = 0;
	private boolean flag_separator_seen = false; // to process the single-bit
													// separation period between
													// flags
	private int decode_count = 0;

	// Diagnostic variables for estimating packet quality
	private int f0_period_count, f1_period_count;
	private float f0_max, f1_min; // to collect average max, min in the filtered
	private float max_period_error;
	private boolean data_carrier;

	private void statisticsInit() {
		f0_period_count = 0;
		f1_period_count = 0;
		f0_max = 0.0f;
		f1_min = 0.0f;
		max_period_error = 0.0f;
	}

	private void statisticsFinalize() {
		f0_max = f0_max / f0_period_count;
		f1_min = f1_min / f1_period_count;
	}

	/**
	 * Once bits have been demodulated pass to this function to construct the
	 * packet.
	 * 
	 * @param bits
	 *            - number of bits at this symbol
	 */
	protected void handleDemodulatedBits(int bits) {
		if (bits == 0 || bits > 7) {
			state = State.WAITING;
			data_carrier = false;
			flag_count = 0;
		} else {
			if (bits == 7) {
				flag_count++;
				flag_separator_seen = false;

				data = 0;
				bitcount = 0;
				switch (state) {
				case WAITING:
					state = State.JUST_SEEN_FLAG;
					data_carrier = true;

					statisticsInit(); // start measuring a new packet
					break;
				case JUST_SEEN_FLAG:
					break;
				case DECODING:
					if (packet != null && packet.terminate()) {
						statisticsFinalize();
						packet.statistics(new float[] { emphasis,
								f0_max / -f1_min, max_period_error });

						if (handler != null) {
							handler.handlePacket(packet.bytesWithoutCRC());
						} else {
							System.out.println("" + (++decode_count) + ": "
									+ packet);
						}
					}
					packet = null;
					state = State.JUST_SEEN_FLAG;
					break;
				}
			} else {
				switch (state) {
				case WAITING:
					break;
				case JUST_SEEN_FLAG:
					state = State.DECODING;
					break;
				case DECODING:
					break;
				}
				if (state == State.DECODING) {
					if (bits != 1) {
						flag_count = 0;
					} else {
						if (flag_count > 0 && !flag_separator_seen) {
							flag_separator_seen = true;
						} else {
							flag_count = 0;
						}
					}

					for (int k = 0; k < bits - 1; k++) { // only loop when
															// multiple bits
															// have been seen
						bitcount++;
						data >>= 1;
						data += 128;
						if (bitcount == 8) {
							if (packet == null) {
								packet = new Packet();
							}

							if (!packet.addByte((byte) data)) { // if the packet
																// is too large
								state = State.WAITING;
								data_carrier = false;
							}
							data = 0;
							bitcount = 0;
						}
					}
					if (bits - 1 != 5) { // the zero after the ones is not a
											// stuffing
						bitcount++;
						data >>= 1;
						if (bitcount == 8) {
							if (packet == null) {
								packet = new Packet();
							}
							// if (data==0xAA) packet.terminate();
							if (!packet.addByte((byte) data)) {
								state = State.WAITING;
								data_carrier = false;

							}
							data = 0;
							bitcount = 0;
						}
					}
				}
			}
		}
	}
}
