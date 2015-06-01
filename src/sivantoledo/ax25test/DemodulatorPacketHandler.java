package sivantoledo.ax25test;

import java.util.ArrayList;
import java.util.Queue;

import sivantoledo.ax25.Arrays;
import sivantoledo.ax25.Packet;
import sivantoledo.ax25.PacketDemodulator;
import sivantoledo.ax25.PacketHandler;

public class DemodulatorPacketHandler implements PacketHandler {
	
	//public boolean duplicateCheckOnStringRepresentation = false;
	public boolean ignoreSampleWaitTime = false;
	private int packetCount = 0;
	private PacketArray packets = new PacketArray();
	byte[] last;
	int dup_count = 0;
	boolean sufficientSamplesElapsed = true;
	boolean pokedOnce = false;
	
	//Using the two booleans this allows us to make sure that between 100-200 samples
	//have elapsed before we set sufficientSamplesElapsed to true. Assuming called
	//once every 100 samples. 
	public void poke()
	{
		if (pokedOnce == true)
		{
		sufficientSamplesElapsed  = true;
		pokedOnce = false;
		}
		else if (sufficientSamplesElapsed == false)
		{
			pokedOnce = true;
		}
	}
	
	private static final int DEBUG = 0;
	
	@Override
	public void handlePacket(byte[] packet) {
		if (DEBUG > 1)
			System.out.println(Packet.format(packet));

		if (last!=null && Arrays.equals(last, packet) && ignoreSampleWaitTime) {
			dup_count++;
			if (DEBUG > 2)
			System.out.printf("Duplicate, %d so far\n",dup_count);
		}
		else if (last!=null && Arrays.equals(last, packet) && !sufficientSamplesElapsed) {
			dup_count++;
			if (DEBUG > 2)
				System.out.printf("Duplicate, %d so far\n",dup_count);
		} else {
			packetCount++;
			if (DEBUG > 2)
				System.out.println("Packet Count:"+packetCount);
			packets.add(packet);
			last = packet;
			sufficientSamplesElapsed = false;
		}
	}

	public DemodulatorPacketHandler() {
		
	}

	public int getPacketCount() {
		return packetCount;
	}
	
	public PacketArray getAllPackets() {
		return packets;
	}
	
	public int getUniquePacketsDecoded(ArrayList<DemodulatorPacketHandler> allDemodPktHndlrs) {
		int uniquePackets = packets.size();
		
		PacketArray duplicatePacketsFound = new PacketArray();
		PacketArray othersPackets;
		
		for (DemodulatorPacketHandler demodPktHndlr : allDemodPktHndlrs){
			if (!demodPktHndlr.equals(this)){
				othersPackets = demodPktHndlr.getAllPackets();
				for (byte[] packet : othersPackets) {
					if (packets.contains(packet) && !duplicatePacketsFound.contains(packet)){
						duplicatePacketsFound.add(packet);
						uniquePackets--;
					}
				}
			}
		}
		
		PacketArray dupChecker = new PacketArray();
		for (byte[] packet : packets)
		{
			dupChecker.addWithReturn(packet);
		}
		int dupTotal = dupChecker.dupCount + dupChecker.packet_count;
		//System.out.println("Total Packets: " + packets.size() + " / " + dupTotal + " Unique: " + dupChecker.packet_count + " duplicates: " + dupChecker.dupCount);
		return uniquePackets - dupChecker.dupCount;
	}
}
