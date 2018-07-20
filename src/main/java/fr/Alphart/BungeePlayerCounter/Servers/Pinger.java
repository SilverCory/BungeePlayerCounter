package fr.Alphart.BungeePlayerCounter.Servers;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;

import com.google.gson.JsonElement;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import com.google.gson.Gson;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;

import fr.Alphart.BungeePlayerCounter.BPC;
import fr.Alphart.BungeePlayerCounter.Servers.Pinger.VarIntStreams.VarIntDataInputStream;
import fr.Alphart.BungeePlayerCounter.Servers.Pinger.VarIntStreams.VarIntDataOutputStream;

public class Pinger implements Runnable {
    private static final Gson gson = new Gson();
    private InetSocketAddress address;
    private String parentGroupName;
    private boolean online = false;
    private int maxPlayers = -1;

    public Pinger(final String parentGroupName, final InetSocketAddress address) {
        this.parentGroupName = parentGroupName;
        this.address = address;
    }

    public boolean isOnline() {
        return online;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    @Override
    public void run() {
        try {
            final PingResponse response = ping(address, 1000);
            online = true;
            maxPlayers = response.getPlayers().getMax();
            BPC.debug("Successfully pinged " + parentGroupName + " group, result : " + response);
        } catch (IOException e) {
            if (!(e instanceof ConnectException) && !(e instanceof SocketTimeoutException)) {
                BPC.severe("An unexcepted error occured while pinging " + parentGroupName + " server", e);
            }
            online = false;
        }
    }

    public static PingResponse ping(final InetSocketAddress host, final int timeout) throws IOException {
        Socket socket = null;
        try {
            socket = new Socket();
            OutputStream outputStream;
            VarIntDataOutputStream dataOutputStream;
            InputStream inputStream;
            InputStreamReader inputStreamReader;

            socket.setSoTimeout(timeout);

            socket.connect(host, timeout);

            outputStream = socket.getOutputStream();
            dataOutputStream = new VarIntDataOutputStream(outputStream);

            inputStream = socket.getInputStream();
            inputStreamReader = new InputStreamReader(inputStream);

            // Write handshake, protocol=4 and state=1
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            VarIntDataOutputStream handshake = new VarIntDataOutputStream(b);
            handshake.writeByte(0x00);
            handshake.writeVarInt(4);
            handshake.writeVarInt(host.getHostString().length());
            handshake.writeBytes(host.getHostString());
            handshake.writeShort(host.getPort());
            handshake.writeVarInt(1);
            dataOutputStream.writeVarInt(b.size());
            dataOutputStream.write(b.toByteArray());

            // Send ping request
            dataOutputStream.writeVarInt(1);
            dataOutputStream.writeByte(0x00);
            VarIntDataInputStream dataInputStream = new VarIntDataInputStream(inputStream);
            dataInputStream.readVarInt();
            int id = dataInputStream.readVarInt();
            if (id == -1) {
                throw new IOException("Premature end of stream.");
            }
            if (id != 0x00) {
                throw new IOException(String.format("Invalid packetID. Expecting %d got %d", 0x00, id));
            }
            int length = dataInputStream.readVarInt();
            if (length == -1) {
                throw new IOException("Premature end of stream.");
            }

            if (length == 0) {
                throw new IOException("Invalid string length.");
            }

            // Read ping response
            byte[] in = new byte[length];
            dataInputStream.readFully(in);
            String json = new String(in);

            // Send ping packet (to get ping value in ms)
            long now = System.currentTimeMillis();
            dataOutputStream.writeByte(0x09);
            dataOutputStream.writeByte(0x01);
            dataOutputStream.writeLong(now);

            // Read ping value in ms
            dataInputStream.readVarInt();
            id = dataInputStream.readVarInt();
            if (id == -1) {
                throw new IOException("Premature end of stream.");
            }
            if (id != 0x01) {
                throw new IOException(String.format("Invalid packetID. Expecting %d got %d", 0x01, id));
            }
            long pingtime = dataInputStream.readLong();

            synchronized (gson) {
                final PingResponse response = gson.fromJson(json, PingResponse.class);
                response.setTime((int) (now - pingtime));
                dataOutputStream.close();
                outputStream.close();
                inputStreamReader.close();
                inputStream.close();
                socket.close();
                return response;
            }
        } catch (final IOException e) {
            throw e;
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @ToString
    public class PingResponse {
        private JsonElement description;
        @Getter
        private Players players;
        @Getter
        private Version version;
        @Getter
        private String favicon;
        @Setter
        @Getter
        private int time;

        public JsonElement getRawDescription() {
            return description;
        }

        public String getDescription() {
            return new TextComponent(getFancyDescription()).toLegacyText();
        }

        public BaseComponent[] getFancyDescription() {
            return ComponentSerializer.parse(description.toString());
        }

        public boolean isFull() {
            return players.max <= players.online;
        }

        @Getter
        @ToString
        public class Players {
            private int max;
            private int online;
            private List<Player> sample;

            @Getter
            public class Player {
                private String name;
                private String id;

            }
        }

        @Getter
        @ToString
        public class Version {
            private String name;
            private String protocol;
        }
    }

    static class VarIntStreams {
        /**
         * Enhanced DataIS which reads VarInt type
         */
        public static class VarIntDataInputStream extends DataInputStream {

            public VarIntDataInputStream(final InputStream is) {
                super(is);
            }

            public int readVarInt() throws IOException {
                int i = 0;
                int j = 0;
                while (true) {
                    int k = readByte();
                    i |= (k & 0x7F) << j++ * 7;
                    if (j > 5)
                        throw new RuntimeException("VarInt too big");
                    if ((k & 0x80) != 128)
                        break;
                }
                return i;
            }

        }

        /**
         * Enhanced DataOS which writes VarInt type
         */
        public static class VarIntDataOutputStream extends DataOutputStream {

            public VarIntDataOutputStream(final OutputStream os) {
                super(os);
            }

            public void writeVarInt(int paramInt) throws IOException {
                while (true) {
                    if ((paramInt & 0xFFFFFF80) == 0) {
                        writeByte(paramInt);
                        return;
                    }

                    writeByte(paramInt & 0x7F | 0x80);
                    paramInt >>>= 7;
                }
            }
        }
    }

}