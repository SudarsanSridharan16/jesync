package jesync.net;

import java.util.Iterator;
import jesync.core.Lock;
import jesync.core.LockEngine;
import org.jboss.netty.channel.*;

/**
 * Class responsible for getting the messages from the client and invoking the
 * corresponding functionality of jesync.core package
 *
 * @author Julio Viera
 */
public final class ServerHandler extends SimpleChannelUpstreamHandler {

    ServerLockRequestTable lockRequests;
    LockEngine syncCore;

    public ServerHandler(LockEngine syncCore) {
        this.syncCore = syncCore;
        this.lockRequests = new ServerLockRequestTable();
    }

    @Override
    public final void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        String msg = (String) e.getMessage();
        String[] args = msg.split(" ");
        String command = args[0];

        if (command.compareTo("lock") == 0) {
            String lockKey = args[1];
            int maxConcurrent = 1;
            int timeout = -1;
            if (args.length > 2) {
                maxConcurrent = Integer.parseInt(args[2]);
            }
            if (args.length > 3) {
                timeout = Integer.parseInt(args[3]);
            }
            this.lock(e.getChannel(), lockKey, maxConcurrent,timeout);
        } else if (command.compareTo("release") == 0) {
            this.release(e.getChannel(), args[1]);
        } else if (command.compareTo("quit") == 0) {
            this.quit(e.getChannel());
        } else {
            e.getChannel().write("INVALID_COMMAND\n");
        }
    }

    @Override
    public final void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        super.channelDisconnected(ctx, e);
        this.releaseAllLocks();
    }

    final void writeResponse(Channel channel, String msg) {
        this.writeResponse(channel, msg, null);
    }

    final void writeResponse(Channel channel, String msg, String lockKey) {
        String response = msg;
        if (lockKey != null) {
            Lock lock = syncCore.getSyncLock(lockKey);
            response += " " + lock.getCurrentGrantedCount() + " " + lock.getCurrentRequestCount() + " " + lockKey;
        }
        channel.write(response+"\n");
    }

    /**
     * Releases all locks granted of the current ServerHandle. Used when a
     * client disconnects
     */
    private void releaseAllLocks() {
        //Release all lock requests
        for (Iterator it = this.lockRequests.keySet().iterator(); it.hasNext();) {
            String lockKey=(String) it.next();
            
            ServerLockRequest request = this.lockRequests.get(lockKey);
            
            Lock lock=this.syncCore.getSyncLock(lockKey);
            synchronized(lock){
                //Cancel the request in case it has not been granted yet
                lock.cancelRequest(request);
                
                //Release the lock in case it has been granted
                request.release();
            }
        }
    }

    /**
     * Returns a LockRequest for a particular channel/lock key
     *
     * @param channel Channel used to respond.
     * @param lockKey The lock key
     * @return If the LockRequest exists, it returns it, if not it creates a new
     * one
     */
    private ServerLockRequest getLockRequest(Channel channel, String lockKey) {
        Integer id;
        ServerLockRequest res = lockRequests.get(lockKey);

        if (res == null) {
            res = new ServerLockRequest(channel,this);
            lockRequests.put(lockKey, res);
        }
        return res;
    }

    //*************** SERVER COMMANDS ******************//
    private void lock(Channel channel, String lockKey, int maxConcurrent,int timeout) {
        ServerLockRequest request = getLockRequest(channel, lockKey);
        request.setMaxConcurrent(maxConcurrent);
        request.setTimeout(timeout);

        Lock l = syncCore.getSyncLock(lockKey);
        l.requestLock(request);
    }

    private void release(Channel channel, String lockKey) {
        ServerLockRequest request = getLockRequest(channel, lockKey);
        if (request != null && request.release()) {
            lockRequests.remove(lockKey);
            this.writeResponse(channel,"RELEASED", lockKey);

        } else {
            this.writeResponse(channel,"NOT_RELEASED", lockKey);
        }
    }

    private void quit(Channel channel) {
        this.releaseAllLocks();
        channel.close();
    }
}
