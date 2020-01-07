package Utility;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;

import Clients.Port;

public class Core {

	@SuppressWarnings("unused")
	public static void main(String[] args) {
		Port c = new Port();
		new File(Constants.BASE_LOCATION + "/chunks/").mkdirs();
		String clientConf = null;
		int totalFilesToRecv;
		ChunkFileObject rChunkObj;
		Integer[] a;
		
		try {
			c.readPortValues();
			c.TCPCliConnect(c.mainServport);
			clientConf = (String) c.inStream.readObject();
			totalFilesToRecv = (int) c.inStream.readObject();
			c.chunkList = Collections.synchronizedSet(new LinkedHashSet<Integer>());
			for (int i = 1; i <= totalFilesToRecv; i++)
				c.chunkList.add(i);
			int filesToReceive = (int) c.inStream.readObject();
			while (filesToReceive > 0) {
				rChunkObj = c.receiveChunk();
				if (rChunkObj != null)
					c.createChunkFile(Constants.CHUNKS_LOCATION, rChunkObj);
				else
					System.out.println("null chunk");
				filesToReceive--;
			}
			c.TCPCliDisconnect();
			/*
			---! TODO parallel execute !---
			String[] tokens = clientConf.split(" ");
			int port = Integer.parseInt(tokens[1]);
			int person_1 = Integer.parseInt(tokens[2]);
			int person_2 = Integer.parseInt(tokens[3]);
			c.TCPCliServconnect(21001);
			*/
			Thread thread = new Thread(new Runnable() {
				public void run() {
					c.TCPCliServconnect(c.clientServport);
				}
			});
			thread.start();
			c.TCPCliConnect(c.clientNeighborPort);
			System.out.println("client1 totalFilesToRecv:" + totalFilesToRecv);

			while (true) {
				System.out.println("client1 files to download:" + c.chunkList);
				if (!c.chunkList.isEmpty()) {
					a = c.chunkList.toArray(new Integer[c.chunkList.size()]);
					for (int i = 0; i < a.length; i++) {
						c.outStream.writeObject(a[i]);// itr.next());
						c.outStream.flush();
						rChunkObj = c.receiveChunk();
						if (rChunkObj != null) 
							c.createChunkFile(Constants.CHUNKS_LOCATION, rChunkObj);
					}
				} else {
					c.outStream.writeObject(-1);
					c.outStream.flush();
					break;
				}
				Thread.sleep(2000);
			}
			c.TCPCliDisconnect();
			c.combineChunks();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
