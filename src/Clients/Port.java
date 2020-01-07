package Clients;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.Set;

import Utility.ChunkFileObject;
import Utility.Constants;

public class Port {

	public int mainServport;
	public int clientServport;
	public int clientNeighborPort;

	public ServerSocket receiveSocket;
	public Socket connSocket;
	public Socket cliSocket;
	public ObjectInputStream inStream;
	public ObjectOutputStream outStream;
	public Set<Integer> chunkList;

	public void TCPCliServconnect(int port) {
		try {
			int neighbourCount = 1;
			receiveSocket = new ServerSocket(port);
			System.out.println(this.getClass().getName() + ": Client-Server socket created...");
			while (true) {
				if (neighbourCount > 0) {
					neighbourCount--;
					connSocket = receiveSocket.accept();
					System.out.println("new client connection accepted: " + connSocket);
					new CliServerThread(connSocket, Constants.CHUNKS_LOCATION).start();
				} else {
					System.out.println("Cannot serve more clients!");
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void TCPCliConnect(int port) throws InterruptedException {
		boolean b = true;
		while (b) {
			try {
				b = false;
				cliSocket = new Socket("127.0.0.1", port);
				System.out.println(" client1 connected to :" + cliSocket);
				inStream = new ObjectInputStream(cliSocket.getInputStream());
				outStream = new ObjectOutputStream(cliSocket.getOutputStream());
			} catch (ConnectException e) {
				System.out.println("unable to connect to socket at: " + port + "... trying again...");
				Thread.sleep(5000);
				b = true;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public ChunkFileObject receiveChunk() {
		ChunkFileObject chunkObj = null;
		try {
			chunkObj = (ChunkFileObject) inStream.readObject();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return chunkObj;
	}

	public void TCPCliDisconnect() {
		try {
			inStream.close();
			cliSocket.close();
			System.out.println("client1 connection closed:" + cliSocket);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized void createChunkFile(String chunksLocation, ChunkFileObject rChunkObj) {
		try {
			FileOutputStream fileOutStream = new FileOutputStream(new File(chunksLocation, rChunkObj.getFileName()));
			BufferedOutputStream bufferOutStream = new BufferedOutputStream(fileOutStream);
			System.out.println("create back received chunk - " + rChunkObj.getFileName());
			bufferOutStream.write(rChunkObj.getFileData(), 0, rChunkObj.getChunksize());
			chunkList.remove(rChunkObj.getFileNum());
			bufferOutStream.flush();
			bufferOutStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void readPortValues() throws FileNotFoundException {
		String str = null;
		BufferedReader br = new BufferedReader(new FileReader(Constants.ROOT + "/config.txt"));
		try {
			str = br.readLine();
			String[] tokens = str.split(" ");
			mainServport = Integer.parseInt(tokens[1]);
			for (int i = 1; i <= 1; i++) 
				str = br.readLine();
			tokens = str.split(" ");
			clientServport = Integer.parseInt(tokens[1]);
			int clientNeighbor = Integer.parseInt(tokens[2]);
			br.close();
			BufferedReader br1 = new BufferedReader(new FileReader(Constants.ROOT + "/config.txt"));
			for (int i = 0; i <= clientNeighbor; i++)
				str = br1.readLine();
			tokens = str.split(" ");
			clientNeighborPort = Integer.parseInt(tokens[1]);
			br1.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void combineChunks() {
		String chunksLocation = Constants.BASE_LOCATION + "/chunks";
		File[] files = new File(chunksLocation).listFiles();
		byte[] chunk = new byte[102400];
		new File(Constants.BASE_LOCATION + "/out/").mkdirs();
		try {
			Random r = new Random();
			FileOutputStream fileOutStream = new FileOutputStream(
					new File(Constants.BASE_LOCATION + "/out/" + r.nextInt(500) + files[0].getName()));
			BufferedOutputStream bufferOutStream = new BufferedOutputStream(fileOutStream);
			for (File f : files) {
				FileInputStream fileInStream = new FileInputStream(f);
				BufferedInputStream bufferInStream = new BufferedInputStream(fileInStream);
				int bytesRead = 0;
				while ((bytesRead = bufferInStream.read(chunk)) > 0) {
					bufferOutStream.write(chunk, 0, bytesRead);
				}
				fileInStream.close();
			}
			fileOutStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

class CliServerThread extends Thread {
	private Socket socket;
	ObjectOutputStream outStream;
	ObjectInputStream inStream;
	String chunkLoc;
	CliServerThread(Socket s, String chunkLoc) {
		this.socket = s;
		this.chunkLoc = chunkLoc;
	}

	public void run() {
		try {
			outStream = new ObjectOutputStream(socket.getOutputStream());
			inStream = new ObjectInputStream(socket.getInputStream());
			while (true) {
				int ChunkNum = (int) inStream.readObject();
				if (ChunkNum < 0)
					break;
				File[] files = new File(chunkLoc).listFiles();
				String[] s;
				File currentFile = null;
				boolean haveFile = false;
				for (int i = 0; i < files.length; i++) {
					currentFile = files[i];
					s = files[i].getName().split("_");
					if (ChunkNum == Integer.parseInt(s[0])) {
						haveFile = true;
						break;
					}
				}
				ChunkFileObject sChunkObj;
				if (haveFile) {
					sChunkObj = constructChuckFileObject(currentFile, ChunkNum);
				} else {
					sChunkObj = constructChuckFileObject(null, -1);
				}
				sendChunkObject(sChunkObj);
			}
			TCPServDisconnect();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized ChunkFileObject constructChuckFileObject(File file, int chunkNum) throws IOException {
		ChunkFileObject chunkObj = null;
		if (chunkNum > 0) {
			byte[] chunk = new byte[102400];
			chunkObj = new ChunkFileObject();
			System.out.println("construct chunk object to send- " + file.getName());
			chunkObj.setFileNum(chunkNum);
			chunkObj.setFileName(file.getName());
			FileInputStream fileInStream = new FileInputStream(file);
			BufferedInputStream bufferInStream = new BufferedInputStream(fileInStream);
			int bytesRead = bufferInStream.read(chunk);
			chunkObj.setChunksize(bytesRead);
			chunkObj.setFileData(chunk);
			bufferInStream.close();
			fileInStream.close();
		}
		return chunkObj;
	}

	public void sendChunkObject(ChunkFileObject sChunkObj) {
		try {
			outStream.writeObject(sChunkObj);
			outStream.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized void TCPServDisconnect() {
		try {
			outStream.close();
			socket.close();
			System.out.println("Client1- server socket closed :" + socket);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
