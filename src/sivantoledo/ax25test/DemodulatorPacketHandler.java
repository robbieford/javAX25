package sivantoledo.ax25test;

import java.util.ArrayList;

import sivantoledo.ax25.Arrays;
import sivantoledo.ax25.Packet;
import sivantoledo.ax25.PacketDemodulator;
import sivantoledo.ax25.PacketHandler;

public class DemodulatorPacketHandler implements PacketHandler {
	
	private int packetCount;
	private PacketArray packets = new PacketArray();
	
	private static final int DEBUG = 0;
	
	@Override
	public void handlePacket(byte[] packet) {
		if (DEBUG > 1)
			System.out.println(Packet.format(packet));
		
		if (!packets.contains(packet))
		{
			packetCount++;
			packets.add(packet);
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
		int uniquePackets = packetCount;
		
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
		
		return uniquePackets;
	}
}
