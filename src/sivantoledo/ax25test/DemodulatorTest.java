/*
 * Test program for the Afsk1200 demodulator classes.
 * For examples, see test.bat
 * 
 * Robert Campbell KJ6RRX 2014
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
package sivantoledo.ax25test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import com.sun.net.ssl.internal.www.protocol.https.Handler;

import sivantoledo.ax25.Afsk1200Demodulator;
import sivantoledo.ax25.Packet;
import sivantoledo.ax25.PacketDemodulator;
import sivantoledo.ax25.PreClockingDemodulator;
import sivantoledo.ax25.StrictZeroCrossingDemodulator;
import sivantoledo.ax25.WindowedZeroCrossingDemodulator;
import sivantoledo.ax25.ZeroCrossingDemodulator;

public class DemodulatorTest {
	
	private int packet_count;
	private int dup_count;
	
	public int getUniqPacketCount() {
		return packet_count;
	}
	
	public int getDupPacketCount() {
		return dup_count;
	}
		
	/*
	 * main program for testing the Afsk1200 demodulator classes.
	 */
	public static void main(String[] args) {
		//Get timestamp for this test to make output folders
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmm").format(Calendar.getInstance().getTime());
		String prefix = "testoutput\\" + timeStamp;
		File outDir = new File(prefix);
		outDir.mkdir();
		
		//Path to all of the audio files we want to use in this test
		ArrayList<String> audioFilePaths = new ArrayList<String>();
		audioFilePaths.add("./audio/generated200packets_48000.wav");
		audioFilePaths.add("./audio/ot3test48000Mono.wav");
		audioFilePaths.add("./audio/ot3testwnoise48000Mono.wav");
		audioFilePaths.add("./nogit/01Track1_48000.wav");
		audioFilePaths.add("./nogit/02Track2_48000.wav");

		ArrayList<PacketDemodulator> demodulators = new ArrayList<PacketDemodulator>();
		HashMap<String, DemodulatorPacketHandler> packetHandlers = new HashMap<String, DemodulatorPacketHandler>();
		
		int filterLength = 80, rate;
		//Counter for audio files
		int testNum = 1;
		
		//For each audio file test each demodulator
		for (String fileName : audioFilePaths) {
			System.out.println();
			System.out.println("----------------------------------------------------------------------------");
			System.out.println("----------------------------------------------------------------------------");
			System.out.println("Begin of test " + testNum + " of " + audioFilePaths.size());
			System.out.println("File: " + fileName);
			
			//Open the audio file
			AudioInputStream ios = null;
			try {
				ios = AudioSystem.getAudioInputStream(new File(fileName));
			} catch (IOException ioe) {
				System.err.println("IO Error: "+ioe.getMessage());
				System.exit(1);
			} catch (UnsupportedAudioFileException usafe) {
				System.err.println("Audio file format not supported: "+usafe.getMessage());
				System.exit(1);
			}
			AudioFormat fmt = ios.getFormat();
			System.out.printf("Audio rate is %d, %d channels, %d bytes per frame, %d bits per sample\n",
					(int) fmt.getSampleRate(),
					fmt.getChannels(),
					fmt.getFrameSize(),
					fmt.getSampleSizeInBits());

			System.out.println("............................................................................");
			rate = (int) fmt.getSampleRate();
			
			//Raw data array
			byte[] raw = new byte[fmt.getFrameSize()];
			
			//Actual sample passed to the algorithms (Why an array? Who knows!)
			float[] f = new float[1];
			
			//Byte buffer for the incoming data
			ByteBuffer bb;
			if (fmt.isBigEndian())
				bb = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
			else
				bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
			
			//Scaling
			float scale = 0.0f;
			switch (fmt.getSampleSizeInBits()){
			case 32: scale = 1.0f / ((float) fmt.getChannels() * 2147483648.0f ); break;
			case 16: scale = 1.0f / ((float) fmt.getChannels() *      32768.0f ); break;
			case  8: scale = 1.0f / ((float) fmt.getChannels() *        256.0f ); break;
			}
			
			DemodulatorTest t = new DemodulatorTest();
			PacketDemodulator demodulator = null;
			DemodulatorPacketHandler packetHandler = null;
			ArrayList<DemodulatorPacketHandler> allDemodPktHndlrs = new ArrayList<DemodulatorPacketHandler>();
			
			demodulators.clear();
			try {
				packetHandler = new DemodulatorPacketHandler();
				demodulator = new Afsk1200Demodulator(rate,filterLength,0,packetHandler);
				demodulators.add(demodulator);
				packetHandlers.put(demodulator.getDemodulatorName(), packetHandler);
				allDemodPktHndlrs.add(packetHandler);
				System.out.println("Created demodulator: " + demodulator.getDemodulatorName());
				
				packetHandler = new DemodulatorPacketHandler();
				demodulator = new Afsk1200Demodulator(rate,filterLength,6,packetHandler);
				demodulators.add(demodulator);
				packetHandlers.put(demodulator.getDemodulatorName(), packetHandler);
				allDemodPktHndlrs.add(packetHandler);
				System.out.println("Created demodulator: " + demodulator.getDemodulatorName());
				
				packetHandler = new DemodulatorPacketHandler();
				demodulator = new ZeroCrossingDemodulator(rate,filterLength,3, packetHandler);
				demodulators.add(demodulator);
				packetHandlers.put(demodulator.getDemodulatorName(), packetHandler);
				allDemodPktHndlrs.add(packetHandler);
				System.out.println("Created demodulator: " + demodulator.getDemodulatorName());
				
				packetHandler =  new DemodulatorPacketHandler();
				demodulator = new StrictZeroCrossingDemodulator(rate,filterLength,3,packetHandler);
				demodulators.add(demodulator);
				packetHandlers.put(demodulator.getDemodulatorName(), packetHandler);
				allDemodPktHndlrs.add(packetHandler);
				System.out.println("Created demodulator: " + demodulator.getDemodulatorName());
				
				packetHandler = new DemodulatorPacketHandler();
				demodulator = new WindowedZeroCrossingDemodulator(rate, filterLength, 3, packetHandler);
				demodulators.add(demodulator);
				packetHandlers.put(demodulator.getDemodulatorName(), packetHandler);
				allDemodPktHndlrs.add(packetHandler);
				System.out.println("Created demodulator: " + demodulator.getDemodulatorName());
				
				packetHandler = new DemodulatorPacketHandler();
				demodulator = new PreClockingDemodulator(rate, filterLength, 3, packetHandler);
				demodulators.add(demodulator);
				packetHandlers.put(demodulator.getDemodulatorName(), packetHandler);
				allDemodPktHndlrs.add(packetHandler);
				System.out.println("Created demodulator: " + demodulator.getDemodulatorName());
				
			} catch (Exception e) {
				System.out.println("Exception trying to create one of the demodulator objects: "+e.getMessage());
				System.exit(1);
			}
			
			/*** Read from the file and pass to the demodulators ***/
			boolean readingAudioFile = true;
			int cursorPosition = 0, samplesSinceLastDot = 0;
			while (readingAudioFile){
				int n = 0;
				try {
					n = ios.read(raw);
				} catch (IOException e) {
					System.err.println("IO Error reading audio file!!");
					e.printStackTrace();
				}
				
				if (n != raw.length) {
					System.out.println("\nNot enough data left in file, end of test...");
					System.out.println();
					readingAudioFile = false;								
				}
				
				if (readingAudioFile) {
					bb.rewind();
					f[0] = 0.0f;
					// we average over channels (stereo)
					for (int i=0; i<fmt.getChannels(); i++) {
						switch (fmt.getSampleSizeInBits()){
						case 32: f[0] += (float) bb.getInt();   break;
						case 16: f[0] += (float) bb.getShort(); break;
						case  8: f[0] += (float) bb.get();      break;
						default:
							System.err.printf("Can't process files with %d bits per sample\n",fmt.getSampleSizeInBits());
							System.exit(1);
						}
					}
					f[0] = scale*f[0];
					
					//Something to show that progress is being made with the tests
					samplesSinceLastDot++;
					if (samplesSinceLastDot == 100000){
						samplesSinceLastDot = 0;
						if (cursorPosition == 76) {
							System.out.print("\n");
							cursorPosition = 0;
						} else {
							System.out.print("*");
							cursorPosition++;
						}
					}
					
					for (PacketDemodulator pktDemod : demodulators) {
						pktDemod.addSamples(f, 1);
					}
				}
			
			}
			
			System.out.println("Demodulator Name                        Packets Decoded     # Unique");
			for (PacketDemodulator demod : demodulators) {
				int n = 46 - demod.getDemodulatorName().length();
				String filler = "";
				for (int i = 0; i < n; i ++) {
					filler = filler + ".";
				}
				
				packetHandler = packetHandlers.get(demod.getDemodulatorName());
				System.out.println(demod.getDemodulatorName() + filler +
						packetHandler.getPacketCount() +
						"............" + packetHandler.getUniquePacketsDecoded(allDemodPktHndlrs));
			}
			testNum++;

			try {
				//Lets output all of the packets to a file...
				//First create the output folders
				String shortFileName = fileName.substring(8, fileName.length() - 5);
				File indAudioDir = new File(prefix + "\\" + shortFileName);
				indAudioDir.mkdir();
				
				String indPrefix = prefix + "\\" + shortFileName + "\\individualdemodulators";
				outDir = new File(indPrefix);
				outDir.mkdir();
				File individualOut;
				BufferedWriter indWrite;
				
				ArrayList<String> allFormattedPackets = new ArrayList<String>();
				String formattedPacket = "";
				
				for (PacketDemodulator demod : demodulators) {
					individualOut = new File(indPrefix + "\\" + demod.getDemodulatorName() + ".txt");
				    indWrite = new BufferedWriter(new FileWriter(individualOut));
					
					packetHandler = packetHandlers.get(demod.getDemodulatorName());
					for (byte[] packet : packetHandler.getAllPackets()) {
						formattedPacket = Packet.format(packet);
						indWrite.write(formattedPacket + "\n");
						if (!allFormattedPackets.contains(formattedPacket)){
							allFormattedPackets.add(formattedPacket);
						}
					}
					indWrite.close();
				}
				System.out.println("Found a total of " + allFormattedPackets.size() + " packets with all demodulators");
				
				//Copied from stack overflow
				//I know that this is bad exception handling...
			    File outFile=new File(prefix + "\\" + shortFileName +"\\CompletePacketOutput.txt");
	
			    BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));
			    for (String pkt : allFormattedPackets)
			    {
			    	writer.write(pkt + "\n");
			    }
	
			    //Close writer
			    writer.close();
			}
			catch (Exception e)
			{
				//Well this is too bad...
				e.printStackTrace();
			}
		}
		System.out.println("Reached the end of the last audio file, exiting cleanly...");
	}

}
