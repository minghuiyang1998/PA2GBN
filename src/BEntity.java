import java.util.*;

public class BEntity {
    private final int windowSize;
    private final int limitSeqNumb;
    private final Checksum checksum;
    private final Queue<Packet> sackQueue;
    private int next;
    private int countACK = 0;
    private int countTo5 = 0;
    private final int sackSize;

    BEntity(int windowSize, int limitSeqNumb, int sackSize) {
        this.windowSize = windowSize;
        this.limitSeqNumb = limitSeqNumb;
        this.checksum = new Checksum();
        this.next = 0;
        this.sackQueue = new LinkedList<>();
        this.sackSize = sackSize;
    }

    private void addToSack(Packet packet) {
        if (sackQueue.size() >= sackSize) {
            sackQueue.poll();
            sackQueue.offer(packet);
        } else {
            sackQueue.offer(packet);
        }
    }

    private boolean isInSack(int seqNumb) {
        boolean result = false;
        int sz = sackQueue.size();
        for (int i = 0; i < sz; i++) {
            Packet pkt = sackQueue.poll();
            sackQueue.offer(pkt);
            if (pkt.getSeqnum() == seqNumb) {
                result = true;
                break;
            }
        }
        return result;
    }

    private boolean isInWindow(int seqNumb) {
        Queue<Integer> seqNumbInWindow = new LinkedList<>();
        for (int i = 0; i < windowSize; i++) {
            int temp = next + i >= limitSeqNumb ? next + i - limitSeqNumb : next + i;
            seqNumbInWindow.offer(temp);
        }
        return seqNumbInWindow.contains(seqNumb);
    }

    public int getCountTo5() {
        return countTo5;
    }

    public int getCountACK() {
        return countACK;
    }

    private void sendCumulativeACK() {
        final int ID = 1;
        int seqNumb = 0;
        int ackNumb = next;
        String payload = "";

        int[] sack = new int[sackSize];
        for (int i = 0; i < sackQueue.size(); i++) {
            Packet p = sackQueue.poll();
            sack[i] = p.getSeqnum();
            sackQueue.offer(p);
        }
        int check = checksum.calculateChecksum(seqNumb, ackNumb, payload, sack);
        NetworkSimulator.toLayer3(ID, new Packet(seqNumb, ackNumb, check, payload, sack));
        countACK += 1;
    }

    private void dealWithInOrder(Packet packet) {
        String payload = packet.getPayload();
        // send all this consecutive to layer5
        NetworkSimulator.toLayer5(payload);
        int seqNumb = packet.getSeqnum();
        if (!sackQueue.contains(seqNumb)) {
            addToSack(packet);
        }
        countTo5 += 1;
        next = next >= limitSeqNumb - 1 ? 0 : next + 1;
    }

    private boolean dealwithSack(Packet packet) {
        boolean isNextUpdate = false;
        // check out of buffer, if there are consecutive, addToInOrder()
        int sz = sackQueue.size();
        for (int i = 0; i < sz; i++) {
            Packet p = sackQueue.poll();
            int seq = p.getSeqnum();
            if (seq == next) {
                System.out.println("seq: " + seq);
                isNextUpdate = true;
                dealWithInOrder(p);
            } else {
                sackQueue.offer(p);
            }
        }
        return isNextUpdate;
    }

    // called by simulator
    public void input(Packet packet) {
        System.out.println(" B received packet");
        System.out.println(packet.toString());
        // 1. Check if the packet is corrupted and drop it
        int seqNumb = packet.getSeqnum();
        int checkSum = checksum.calculateChecksum(packet);
        if (checkSum != packet.getChecksum()) {
//            System.out.println("B corrupt");
            return;
        }

        // 3. If the data packet in-order, deliver the data to layer5 and send ACK to A. Note
        //that you might have subsequent data packets waiting in the buffer at B that also need to be
        //delivered to layer5
        if (seqNumb == next) {
            dealWithInOrder(packet);
            boolean isNextUpdate = true;
            while (isNextUpdate) {
                isNextUpdate = dealwithSack(packet);
            }
            sendCumulativeACK();
        } else {
            // in window, out of order
            if (isInWindow(seqNumb)) {
                //3. If the data packet is out of order, buffer the data packet and send an ACK
                if (!isInSack(seqNumb)) {
                    System.out.println("B recieved out of order, not duplicate");
                    addToSack(packet);
                } else {
                    System.out.println("B recieved out of order, duplicate");
                }
                sendCumulativeACK();
            } else {
                // out of window, duplicate
                System.out.println("B received duplicate");
                sendCumulativeACK();
            }
        }
    }
}
