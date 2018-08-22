package test;

import socket.ScoutingSocket;

public class Test {
	public static final String  IP = "localhost";
	public static final int PORT = 2706;
	public static void main(String[] args) {
		ScoutingSocket socket = new ScoutingSocket(IP,PORT);
		socket.connect(() -> {
			System.out.println("connected!");
		});
		socket.on("data", (data) -> {
			System.out.println("Got data: " + data);
		});
		socket.listen();
		socket.emit("test","{\"type\": \"test\"}", (response) -> {
			System.out.println("Data confirmation received");
		});
	}
}