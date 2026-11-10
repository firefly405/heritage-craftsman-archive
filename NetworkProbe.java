public class NetworkProbe { public static void main(String[] args) throws Exception { try(var selector=java.nio.channels.Selector.open()) { System.out.println("OK"); } } }

