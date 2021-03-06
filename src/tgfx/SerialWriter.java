/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tgfx;

import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import tgfx.tinyg.TinygDriver;

/**
 *
 * @author ril3y
 */
public class SerialWriter implements Runnable {

    private static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(SerialWriter.class);
    private BlockingQueue queue;
    private boolean RUN = true;
    private boolean cleared  = false;
    private String tmpCmd;
    private int BUFFER_SIZE = 254;
    public int buffer_available = BUFFER_SIZE;
    private SerialDriver ser = SerialDriver.getInstance();
    private static final Object mutex = new Object();
    private static boolean throttled = false;

    //   public Condition clearToSend = lock.newCondition();
    public SerialWriter(BlockingQueue q) {
        this.queue = q;
        logger.setLevel(org.apache.log4j.Level.ERROR);
//        logger.setLevel(org.apache.log4j.Level.INFO);

    }

    public void resetBuffer() {
        //Called onDisconnectActions
        buffer_available = BUFFER_SIZE;
        notifyAck();  
    }

    public void setSerialBufferLenght(int bufflen) {
        buffer_available = bufflen;  //If the firmware build uses a different serial buffer len for whatever reason this will catch it.
    }

    public void clearQueueBuffer() {
        queue.clear();
        this.cleared = true; // We set this to tell teh mutex with waiting for an ack to send a line that it should not send a line.. we were asked to be cleared.
        try {
//            SerialDriver.getInstance().priorityWrite(CommandManager.CMD_APPLY_RESET);
            this.buffer_available = BUFFER_SIZE;
            this.setThrottled(false);
            this.notifyAck();
            
            
          
        } catch (Exception ex) {
            Logger.getLogger(SerialWriter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public boolean isRUN() {
        return RUN;
    }

    public void setRun(boolean RUN) {
        this.RUN = RUN;
    }

    public synchronized int getBufferValue() {
        return buffer_available;
    }

    public synchronized void setBuffer(int val) {
        buffer_available = val;
        logger.info("Got a BUFFER Response.. reset it to: " + val);
    }

    public synchronized void addBytesReturnedToBuffer(int lenBytesReturned) {
        buffer_available = (buffer_available + lenBytesReturned);
//        logger.info("Returned " + lenBytesReturned + " to buffer.  Buffer is now at " + buffer_available + "\n");
    }

    public void addCommandToBuffer(String cmd) {
        this.queue.add(cmd);
    }

    public boolean setThrottled(boolean t) {

        synchronized (mutex) {
            if (t == throttled) {
                logger.debug("Throttled already set");
                return false;
            }
            logger.debug("Setting Throttled " + t);
            throttled = t;
        }
        return true;
    }

    public void notifyAck() {
        //This is called by the response parser when an ack packet is recvd.  This
        //Will wake up the mutex that is sleeping in the write method of the serialWriter
        //(this) class.
        synchronized (mutex) {
            logger.debug("Notifying the SerialWriter we have recvd an ACK");
            mutex.notify();
        }
    }

    private void sendUiMessage(String str) {
        //Used to send messages to the console on the GUI
        String gcodeComment = "";
        int startComment = str.indexOf("(");
        int endComment = str.indexOf(")");
        for (int i = startComment; i <= endComment; i++) {
            gcodeComment += str.charAt(i);
        }
        Main.postConsoleMessage(" Gcode Comment << " + gcodeComment + "\n");
    }

    public void write(String str) {
        try {
            synchronized (mutex) {
                if (str.length() > getBufferValue()) {
                    setThrottled(true);
                } else {
                    buffer_available = (getBufferValue() - str.length());
                }

                while (throttled) {
                    if (str.length() > getBufferValue()) {
                        logger.debug("Throttling: Line Length: " + str.length() + " is smaller than buffer length: " + buffer_available);
                        setThrottled(true);
                    } else {
                        setThrottled(false);
                        buffer_available = (getBufferValue() - str.length());
                        break;
                    }
                    logger.debug("We are Throttled in the write method for SerialWriter");
                    //We wait here until the an ack comes in to the response parser
                    // frees up some buffer space.  Then we unlock the mutex and write the next line.
                    mutex.wait();
                    if(cleared){
                       //clear out the line we were waiting to send.. we were asked to clear our buffer
                        //includeing this line that is waiting to be sent.
                        cleared = false;  //Reset this flag now...
                        return;
                    }
                    logger.debug("We are free from Throttled!");
                }
            }
            if (str.contains("(")) {
                //Gcode Comment Push it back to the UI
                sendUiMessage(str);
            }

            ser.write(str);
            logger.info("Wrote Line --> " + str);
        } catch (Exception ex) {
            logger.error("Error in SerialDriver Write");
        }
    }

    @Override
    public void run() {
        System.out.println("[+]Serial Writer Thread Running...");
        while (RUN) {
            try {
                tmpCmd = (String) queue.take();  //Grab the line
                this.write(tmpCmd);
            } catch (Exception ex) {
                System.out.println("[!]Exception in SerialWriter Thread");
            }
        }
        System.out.println("[+]SerialWriter thread exiting...");
    }
}