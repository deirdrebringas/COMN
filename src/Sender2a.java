import com.sun.net.httpserver.*;
import com.sun.net.httpserver.Authenticator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Created by s1368635 on 14/03/2017.
 */

//allowed to transmit multiple packets (when available) without waiting for an acknowledgment
//maximum of N unacknowledged packets
public class Sender2a {

    //Used for the while loop in Ack class to stop the run method
    private static boolean StopAckRun = false;

    public static void send(InetAddress address, int portNum, String filename, int RetryTimeout, int N) throws IOException {
        long starttime = System.currentTimeMillis();
        int timeout = RetryTimeout;
        DatagramSocket socket = new DatagramSocket();
        System.out.println("Socket connected");
        //An ArrayList to keep track of the packets that have been ack'ed
        ArrayList<Packet> ackedPackets = new ArrayList<>();
        int ackedPackNum = -1;
        long timer;

        byte[] buffer = new byte[1024];
        int numBytes = 0;

        try {
            File file = new File(filename);
            FileInputStream in = new FileInputStream(file);
            //Some variables for the while loop
            short packNum = 0;
            int packLen;
            int dataLeft;

            boolean last = false;
            boolean retransmit = false;
            boolean loop = true;

            //Starting threat
            Acknowledge Ack = new Acknowledge(portNum, timeout);
            System.out.println("Sending packets...");

            while (loop) {

                //if the packet number has been acknowledged, remove that and the previous ones that have been
                //acknowledged before it so we can "shift" the window
                while (ackedPackets.size() > 0 && ackedPackets.get(0).packNum <= ackedPackNum) {
                    ackedPackets.remove(0);

                }

                //check if we are still within the window size for the list of acknowledged packets
                while (!last && ackedPackets.size() < N) {
                    dataLeft = in.available();
                    if (dataLeft > buffer.length) {
                        packLen = buffer.length;
                    } else {
                        //Check if last packet
                        packLen = dataLeft;
                        last = true;
                        //Start timer after finishing last iteration of loop
                        timer = System.currentTimeMillis();
                    }
                    numBytes += packLen;

                    //Full size of the file including the header
                    byte[] bytes = new byte[1027];
                    in.read(bytes, 3, packLen);

                    //get the header of the current packet
                    byte[] header = ByteBuffer.allocate(2).putShort(packNum).array();
                    bytes[0] = header[0];
                    bytes[1] = header[1];

                    //If this is the last packet then make sure the flag is 1, if not then flag is 0
                    //This is so the receiver knows that this is the last packet being sent
                    if (last) {
                        bytes[2] = 1;
                    } else {
                        bytes[2] = 0;
                    }

                    DatagramPacket sendPacket = new DatagramPacket(bytes, bytes.length, address, portNum);
                    socket.send(sendPacket);

                    //add packets sent to acknowledged list
                    ackedPackets.add(new Packet(packNum, sendPacket, System.currentTimeMillis()));
                    packNum++;
                }

                //check whether we need to retransmit packets
                //If a timeout occurs, the sender resends all packets that have been previously
                //sent but that have not yet been acknowledged.
                retransmit = ackedPackets.get(0).timeSent + timeout <= System.currentTimeMillis();
                if (ackedPackets.size() > 0 && retransmit) {
                    for (int i = 0; i < ackedPackets.size(); i ++) {
                        Packet currPack = ackedPackets.get(i);
                        //resend data and store back into array list
                        socket.send(currPack.data);
                        currPack.timeSent = System.currentTimeMillis();
                        ackedPackets.set(i, currPack);
                    }
                }
                if (last) {
                    loop = false;
                    StopAckRun = false;
                    socket.close();
                }
            }

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public static void main(String[] args) throws IOException {
        final InetAddress host = InetAddress.getByName(args[0]); //localhost
        int port = Integer.parseInt(args[1]);
        String filename = args[2];
        int timeout = Integer.parseInt(args[3]);
        int N = Integer.parseInt(args[4]);

        send(host, port, filename, timeout, N);

    }


    //Create Ack class that implements Runnable interface to create thread
    private static class Acknowledge implements Runnable {
        DatagramSocket recSocket;
        int timeout;
        Thread t;

        public Acknowledge(int portNum, int timeout) throws SocketException {
            //Create different socket for receiver in different port
            //strange error when using same port number so add 1 to change port num
            this.recSocket = new DatagramSocket(portNum+1);
            this.timeout = timeout;
            this.t = new Thread(this);
        }
        @Override
        public void run() {
            t.start();
            while (!StopAckRun) {
                //Allocate two bytes
                byte[] recBuf = new byte[2];
                DatagramPacket recPacket = new DatagramPacket(recBuf, recBuf.length);
                //Set how many milliseconds until the socket times out
                try {
                    recSocket.setSoTimeout(timeout);
                    recSocket.receive(recPacket);
                    byte[] recData = recPacket.getData();
                } catch (SocketTimeoutException e){
                    System.out.println(e);
                } catch (IOException e) {
                    System.out.println(e);
                }
                //yield its current use of a processor
                t.yield();
            }

        }
    }

    //Create Packet class that holds the information we need for acknowledgement
    private static class Packet {
        int packNum;
        DatagramPacket data;
        long timeSent;
        public Packet(int num, DatagramPacket data, long time) {
            this.packNum = num;
            this.data = data;
            this.timeSent = time;
        }
    }
}
