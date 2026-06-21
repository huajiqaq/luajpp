package com.androlua.network;

import com.androlua.core.LuaContext;
import com.androlua.core.LuaGcable;
import com.androlua.internal.LuaConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

/**
 * Lua TCP 客户端
 * 用于在 Lua 脚本中进行 Socket 网络通信
 * <p>
 * Created by Administrator on 2017/10/20 0020.
 */

@SuppressWarnings("unused")
public class LuaClient implements LuaGcable {
    private OnReadLineListener mOnReadLineListener;
    private Socket mSocket;
    private BufferedReader in;
    private BufferedWriter out;
    private boolean mGc;

    public LuaClient(LuaContext context) {
        if (context != null) {
            context.regGc(this);
        }
    }

    public LuaClient() {
    }

    public boolean start(String dstName, int dstPort) {
        if (mSocket != null && mSocket.isConnected())
            return false;

        try {
            mSocket = new Socket(dstName, dstPort);
            in = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream()));
            new SocketThread().start();
            return true;
        } catch (IOException e) {
            LuaConfig.logError("LuaClient", e);
            return false;
        }
    }

    public boolean stop() {
        if (mSocket == null)
            return false;
        try {
            mSocket.close();
            return true;
        } catch (Exception e) {
            LuaConfig.logError("LuaClient", e);
            return false;
        }
    }

    public void setOnReadLineListener(OnReadLineListener listener) {
        mOnReadLineListener = listener;
    }

    @Override
    public void gc() {
        stop();
        mGc = true;
    }

    @Override
    public boolean isGc() {
        return mGc;
    }

    public boolean write(String text) {
        if (out == null) return false;
        try {
            out.write(text);
            return true;
        } catch (Exception e) {
            LuaConfig.logError("LuaClient", e);
            return false;
        }
    }

    public boolean flush() {
        if (out == null) return false;
        try {
            out.flush();
            return true;
        } catch (Exception e) {
            LuaConfig.logError("LuaClient", e);
            return false;
        }
    }

    public boolean newLine() {
        if (out == null) return false;
        try {
            out.newLine();
            out.flush();
            return true;
        } catch (Exception e) {
            LuaConfig.logError("LuaClient", e);
            return false;
        }
    }

    public boolean isConnected() {
        return mSocket != null && mSocket.isConnected() && !mSocket.isClosed();
    }

    private class SocketThread extends Thread {
        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (mOnReadLineListener != null)
                        mOnReadLineListener.onReadLine(line);
                }
            } catch (Exception e) {
                LuaConfig.logError("LuaClient", e);
                if (mOnReadLineListener != null)
                    mOnReadLineListener.onReadLine(e.toString());
            }
        }
    }

    public interface OnReadLineListener {
        void onReadLine(String line);
    }
}
