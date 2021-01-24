package util;

import org.nustaq.serialization.FSTConfiguration;

import java.net.InetSocketAddress;

/**
 * @author naison
 * @since 3/25/2020 17:25
 */
public class FSTUtil { // use for communication serialization

    private static final FSTConfiguration CONF = FSTConfiguration.createUnsafeBinaryConfiguration();

    public static FSTConfiguration getConf() {
        return CONF;
    }

    public static void main(String[] args) {
        byte[] jsonString = getConf().asByteArray(new InetSocketAddress("localhost", 12));
        Object o = getConf().asObject(jsonString);
        System.out.println(o.toString());
    }
}
