/*
 * Audio FSK modem for AX25 (1200 Baud, 1200/2200Hz).
 * 
 * Copyright (C) Sivan Toledo, 2012
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

public class WindowedZeroCrossingDemodulator
        extends PacketDemodulator //implements HalfduplexSoundcardClient 
{

	//Old Variables
    private int rate_index;
    
    private int data, bitcount;
    private Packet packet; // received packet
    private PacketHandler handler;

    private static enum State {
        WAITING,
        JUST_SEEN_FLAG,
        DECODING
    };
    
    private State state = State.WAITING;
    //TransmitController transmit_controller;
    private int filter_index;
    private int emphasis;
    
    //Move Variables from in front of the add Samples Private Method
    private int flag_count = 0;
    private boolean flag_separator_seen = false; // to process the single-bit separation period between flags
    private int decode_count = 0;
    
    /*
     * Diagnostic variables for estimating packet quality
     */
    private int f0_period_count, f1_period_count;
    private float f0_max, f1_min; // to collect average max, min in the filtered diff signal
    private float max_period_error;

    //New Variables for Zero Crossing (migrate old ones here as we realize we need them
    //-----------------------------------------------------------------------------------vvv
    private static final int DEBUG = 2;
    //Structure Declarations
    private enum Freq {
		f_1200,
		f_2200;
    };
    
    //Variables
    private long samplesReceived;
    private float samplesPerBit;

    private static final int _1200CrossingsInWindow = 2;
    private static final float WINDOW_SIZE_IN_PERCENTAGE = 0.90f;
    private int samplesInWindow;
    private ArrayList<Float> window;
	private int samplesSinceCrossingRecount;
	private int crossingRecountInterval;
    private int samplesSinceFreqTransition;
    private int minimumZeroXingSamples;
    
    private Freq lastFrequencySeen;

	private float samplesPer2200ZeroXing;
    
    //-----------------------------------------------------------------------------------^^^
    
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

    public WindowedZeroCrossingDemodulator(int sample_rate, int filter_length) throws Exception {
        this(sample_rate, filter_length, 6, null);
    }

    public WindowedZeroCrossingDemodulator(int sample_rate, int filter_length, int emphasis, PacketHandler h) throws Exception {
        super(sample_rate == 8000 ? 16000 : sample_rate);
    	
        this.samplesPerBit = (float) sample_rate / 1200.0f;
        
        samplesPer2200ZeroXing = (float) sample_rate /2200.0f/2.0f;
        minimumZeroXingSamples = (int) (samplesPer2200ZeroXing - 1);
        samplesReceived = 0;
        samplesSinceFreqTransition = 0;
        //samplesInWindow = Math.round(sample_rate / 1200.0f) - 2;
        samplesInWindow = Math.round(samplesPerBit * WINDOW_SIZE_IN_PERCENTAGE);
        window = new ArrayList<Float>(samplesInWindow);
        crossingRecountInterval = Math.round(samplesPer2200ZeroXing/2.0f);
        
        //End of new constructor code

        this.emphasis = emphasis;
        for (rate_index = 0; rate_index < Afsk1200Filters.sample_rates.length; rate_index++) {
            if (Afsk1200Filters.sample_rates[rate_index] == sample_rate) {
                break;
            }
        }
        if (rate_index == Afsk1200Filters.sample_rates.length) {
            throw new Exception("Sample rate " + sample_rate + " not supported");
        }

        handler = h;
        
        System.err.printf("samples per bit = %.3f\n", this.samplesPerBit);

        float[][][] tdf;
        switch (emphasis) {
            case 0:
                tdf = Afsk1200Filters.time_domain_filter_none;
                break;
            case 6:
                tdf = Afsk1200Filters.time_domain_filter_full;
                break;
            default:
                System.err.printf("Filter for de-emphasis of %ddB is not availabe, using 6dB\n",
                        emphasis);
                tdf = Afsk1200Filters.time_domain_filter_full;
                break;
        }

        for (filter_index = 0; filter_index < tdf.length; filter_index++) {
            System.err.printf("Available filter length %d\n", tdf[filter_index][rate_index].length);
            if (filter_length == tdf[filter_index][rate_index].length) {
                System.err.printf("Using filter length %d\n", filter_length);
                break;
            }
        }
        
        if (filter_index == tdf.length) {
            filter_index = tdf.length - 1;
            System.err.printf("Filter length %d not supported, using length %d\n",
                    filter_length,
                    tdf[filter_index][rate_index].length);
        }
    }
    
    private volatile boolean data_carrier = false;

    public boolean dcd() {
        return data_carrier;
    }
    
    protected boolean isZeroCrossing(float firstSample, float secondSample) {
    	return ((firstSample <= 0 && secondSample > 0) || //Going from below zero to above OR
		(firstSample >= 0 && secondSample < 0)) ;			// Going from above zer to below
    }
    
    protected boolean isBelowZero(float sample) {
    	return sample <=0;
    }
    
    protected boolean isAboveZero(float sample) {
    	return sample >=0;
    }

    protected void addSamplesPrivate(float[] s, int n) {
    	Freq freq;
    	
    	for (int i = 0; i < s.length; i ++) {
    		window.add(s[i]);
    		samplesReceived++;
    		samplesSinceFreqTransition++;
    		samplesSinceCrossingRecount++;

    		//We have enough samples, so lets do this!
    		if (window.size() == samplesInWindow){
    			if (samplesSinceCrossingRecount >  crossingRecountInterval){
    				int crossings = calculateCrossingsInWindow();
    				samplesSinceCrossingRecount = 0;
    				
    				if (crossings <= _1200CrossingsInWindow){
    					freq = Freq.f_1200;
    				} else {
    					freq = Freq.f_2200;
    				}
    				
    				if (lastFrequencySeen != freq){
    					int bits = Math.round(samplesSinceFreqTransition / samplesPerBit);
    					if (bits > 0) {
    						if (DEBUG > 1) {
    							System.out.println("\t" + samplesReceived + " " + bits + " -Switched Freq from " + lastFrequencySeen);
    						}
    						handleFrequencyTransition(bits);
    						samplesSinceFreqTransition = 0;
    						lastFrequencySeen = freq;
    					}
    					else {
    						if (DEBUG > 0) System.out.println("\t\tGot 0 bits, wait for one more sample - " + samplesReceived);
    						samplesSinceCrossingRecount = crossingRecountInterval + 1;
    					}
    				}
    			}

    			//Remove the last sample to get ready for the next one.
    			window.remove(0);
    		}
    	}
    }
    
    private int calculateCrossingsInWindow() {
    	int crossings = 0;
    	boolean isHigh = window.get(0) > 0;
    	
    	//Set this to something big
    	int samplesSinceLastXing = Math.round(samplesPerBit);
    	
		for (int i = 0; i < window.size(); i ++){
			if(samplesSinceLastXing >= minimumZeroXingSamples ) {
    			if((isHigh && isBelowZero(window.get(i))) || (!isHigh && isAboveZero(window.get(i))))
    			{
    				crossings++;
    				isHigh = !isHigh;
        		} 
    		}
		}
		return crossings;
	}

	private void handleFrequencyTransition(int bits) {
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
						packet.statistics(new float[]{emphasis, f0_max / -f1_min, max_period_error});

						if (DEBUG > 0) {
							System.out.println("Last Sample Count of Packet: " + samplesReceived);
						}

						if (handler != null) {
							handler.handlePacket(packet.bytesWithoutCRC());
						} else {
							System.out.println("" + (++decode_count) + ": " + packet);
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

					for (int k = 0; k < bits - 1; k++) { //only loop when multiple bits have been seen
						bitcount++;
					data >>= 1;
					data += 128;
					if (bitcount == 8) {
						if (packet == null) {
							packet = new Packet();
							if(DEBUG > 1) System.out.println("Created new Packet");
						}

						if (!packet.addByte((byte) data)) { //if the packet is too large
							state = State.WAITING;
						data_carrier = false;
						System.err.println("Packet too Large. Throwing out");
						}
						data = 0;
						bitcount = 0;
					}
					}
					if (bits - 1 != 5) { // the zero after the ones is not a stuffing
						bitcount++;
						data >>= 1;
						if (bitcount == 8) {
							if (packet == null) {
								packet = new Packet();
								if(DEBUG > 1) System.out.println("Created new Packet");
							}
							//if (data==0xAA) packet.terminate();
							if (!packet.addByte((byte) data)) {
								state = State.WAITING;
								data_carrier = false;
								System.err.println("Packet too Large. Throwing out");

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
