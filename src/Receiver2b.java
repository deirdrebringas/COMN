import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by deirdrebringas on 23/03/2017.
 */
public class Receiver2b {
    public static void receive(int port, String filename, int N) throws IOException {
        //create sockets for both receiver and sender
        DatagramSocket recSocket = new DatagramSocket(port);
        DatagramSocket sendSocket = new DatagramSocket();
        File file = new File(filename);
        FileOutputStream out = new FileOutputStream(file);
        ArrayList<Packet> BufferedPackets = new ArrayList<Packet>();
        short packNum = 0;
        boolean fileReceived = false;

        //initialised so that lastPackNum == pacKNum is false; number cannot be -1
        int lastPackNum = -1;
        int base = 0;
        int lastPack = -1;

        System.out.println("Receiving file...");

        while (!fileReceived) {
            byte[] buffer = new byte[1024];
            DatagramPacket recPacket = new DatagramPacket(buffer, buffer.length);
            recSocket.receive(recPacket);
            byte[] data = recPacket.getData();
            //adjust size of buffer
            int currPackSize = recPacket.getLength();

            //Real file starts at data[3] because of an offset of 3
            byte[] offset = Arrays.copyOfRange(data, 0, 2);
            packNum = ByteBuffer.wrap(offset).getShort();

            //check if the current packet is in the window
            if (packNum >= base && packNum <= (base + N - 1)) {
                //if current packet is next in sequence
                if (packNum == lastPackNum + 1) {
                    lastPack = (int) data[2];
                    out.write(data, 3, data.length - 3);
                    if (lastPack == 1) {
                        fileReceived = true;
                    }
                    lastPackNum++;
                }

                while (BufferedPackets.size() > 0) {
                    Packet currPack = BufferedPackets.get(0);
                    //check if any of the packets in buffer are next in the sequence
                    if (currPack.packetNumber == lastPackNum+1) {
                        BufferedPackets.remove(0);
                        lastPackNum = currPack.packetNumber;
                        out.write(currPack.data.getData(), 3, currPack.data.getLength()-3);
                    } else break;
                }
                base = lastPackNum + 1;
            } else {
                //out of order so buffer current packet if not already in buffer
                Packet p = new Packet(packNum, recPacket);
                if (!BufferedPackets.contains(p)) {
                    BufferedPackets.add(p);
                }
            }

            InetAddress recPackAddress = recPacket.getAddress();
            sendAck(packNum, port, sendSocket, recPackAddress);

            //resend acknowledgement
            if (packNum >= 0 && packNum < base) {
                recPackAddress = recPacket.getAddress();
                sendAck(packNum, port, sendSocket, recPackAddress);
            }

            if (lastPack == 1) {
                fileReceived = true;
            }
        }
        System.out.println("File received!");
        out.close();
        recSocket.close();

    }


    //new method to send acknowledgement to specified port to sender
    public static void sendAck(short packNum, int portNum, DatagramSocket socket, InetAddress add) throws IOException {
        try {
            ByteBuffer bb = ByteBuffer.allocate(2);
            byte[] sendByte = bb.putShort(packNum).array();
            DatagramPacket sendPack = new DatagramPacket(sendByte, sendByte.length, add, portNum + 1);
            socket.send(sendPack);
        } catch (IOException e) {}
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]);
        String filename = args[1];
        int N = Integer.parseInt(args[2]);

        receive(port, filename, N);
    }

    //Create Packet class that holds the information we need for acknowledgement
    private static class Packet {
        int packetNumber;
        DatagramPacket data;

        public Packet(int num, DatagramPacket data) {
            this.packetNumber = num;
            this.data = data;
        }

    }
}
