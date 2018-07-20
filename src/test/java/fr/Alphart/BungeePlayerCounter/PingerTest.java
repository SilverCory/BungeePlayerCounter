package fr.Alphart.BungeePlayerCounter;

import fr.Alphart.BungeePlayerCounter.Servers.Pinger;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.google.common.base.Preconditions;

/**
 * Created by Cory Redmond on 06/03/2016.
 *
 * @author Cory Redmond <ace@ac3-servers.eu>
 */
public class PingerTest {

    @Test
    public void doPing() throws IOException {

        Pinger.PingResponse response = Pinger.ping(new InetSocketAddress("mc.hypixel.net", 25565), 20000);
        Preconditions.checkNotNull(response);

        System.out.println("Description: " + response.getDescription());
        System.out.println("Version: " + response.getVersion());
        System.out.println("Playercount: " + response.getPlayers().getOnline() + "/" + response.getPlayers().getMax());

    }

}
