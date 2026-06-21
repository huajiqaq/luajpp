package com.androlua.network;

import com.androlua.core.LuaContext;
import com.androlua.core.LuaGcable;
import com.androlua.internal.LuaLog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lua TCP 服务器
 */
@SuppressWarnings("unused")
public class LuaServer implements LuaGcable {

    private ServerSocket mServerSocket;
    private final Set<ClientConnection> mClients = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();
    private OnReadLineListener mReadLineListener;
    private volatile boolean mIsGced;
    private volatile boolean mRunning;

    public LuaServer(LuaContext context) {
        if (context != null) context.regGc(this);
    }

    public LuaServer() {
    }

    /**
     * 启动服务器
     */
    public boolean start(int port) {
        if (mServerSocket != null) return false;
        try {
            mServerSocket = new ServerSocket(port);
            mRunning = true;
            mExecutor.submit(this::acceptLoop);
            return true;
        } catch (Exception e) {
            LuaLog.getInstance().addError("LuaServer", e);
            return false;
        }
    }

    /**
     * 停止服务器
     */
    public boolean stop() {
        if (mServerSocket == null) return false;
        mRunning = false;
        try {
            mServerSocket.close();
        } catch (Exception e) {
            LuaLog.getInstance().addError("LuaServer", e);
        }
        mServerSocket = null;
        for (ClientConnection client : mClients) client.close();
        mClients.clear();
        return true;
    }

    public void setOnReadLineListener(OnReadLineListener listener) {
        mReadLineListener = listener;
    }

    public boolean isRunning() {
        return mServerSocket != null && !mServerSocket.isClosed();
    }

    public Set<ClientConnection> getClients() {
        return Collections.unmodifiableSet(mClients);
    }

    /**
     * 广播消息给所有客户端
     */
    public void broadcast(String line) {
        for (ClientConnection client : mClients) {
            client.sendLine(line);
        }
    }

    @Override
    public void gc() {
        stop();
        mExecutor.shutdownNow();
        mIsGced = true;
    }

    @Override
    public boolean isGc() {
        return mIsGced;
    }

    // ==================== 内部逻辑 ====================

    private void acceptLoop() {
        while (mRunning && mServerSocket != null && !mServerSocket.isClosed()) {
            try {
                Socket socket = mServerSocket.accept();
                var client = new ClientConnection(socket);
                mClients.add(client);
                mExecutor.submit(client::readLoop);
            } catch (SocketException e) {
                // 服务器关闭时正常退出
                break;
            } catch (Exception e) {
                LuaLog.getInstance().addError("LuaServer", e);
                break;
            }
        }
    }

    // ==================== 客户端连接 ====================

    public class ClientConnection implements AutoCloseable {
        private final Socket mSocket;
        private BufferedWriter mWriter;
        private volatile boolean mClosed;

        public ClientConnection(Socket socket) {
            mSocket = socket;
        }

        private void readLoop() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()))) {
                mWriter = new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream()));
                String line;
                while (!mClosed && (line = reader.readLine()) != null) {
                    if (mReadLineListener != null) {
                        mReadLineListener.onReadLine(LuaServer.this, this, line);
                    }
                }
            } catch (Exception e) {
                if (!mClosed) LuaLog.getInstance().addError("LuaServer", e);
            } finally {
                close();
            }
        }

        public boolean write(String text) {
            if (mWriter == null || mClosed) return false;
            try {
                mWriter.write(text);
                return true;
            } catch (Exception e) {
                LuaLog.getInstance().addError("LuaServer", e);
                return false;
            }
        }

        public boolean flush() {
            if (mWriter == null || mClosed) return false;
            try {
                mWriter.flush();
                return true;
            } catch (Exception e) {
                LuaLog.getInstance().addError("LuaServer", e);
                return false;
            }
        }

        public boolean newLine() {
            if (mWriter == null || mClosed) return false;
            try {
                mWriter.newLine();
                mWriter.flush();
                return true;
            } catch (Exception e) {
                LuaLog.getInstance().addError("LuaServer", e);
                return false;
            }
        }

        public boolean sendLine(String line) {
            if (mWriter == null || mClosed) return false;
            try {
                mWriter.write(line);
                mWriter.newLine();
                mWriter.flush();
                return true;
            } catch (Exception e) {
                LuaLog.getInstance().addError("LuaServer", e);
                return false;
            }
        }

        @Override
        public void close() {
            if (mClosed) return;
            mClosed = true;
            mClients.remove(this);
            try {
                mSocket.close();
            } catch (Exception e) {
                LuaLog.getInstance().addError("LuaServer", e);
            }
        }

        public boolean isConnected() {
            return !mClosed && mSocket.isConnected() && !mSocket.isClosed();
        }
    }

    // ==================== 接口 ====================

    public interface OnReadLineListener {
        void onReadLine(LuaServer server, ClientConnection client, String line);
    }
}