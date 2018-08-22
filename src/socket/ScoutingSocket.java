package socket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.function.Consumer;

import com.google.gson.JsonParser;

public class ScoutingSocket {

	private final int RECONNECT_TIME = 1000;
	private final int OUTPUT_TIME = 1000;

	private ArrayList<CallbackListener> receiveListeners;
	private ArrayList<ConnectListener> connectListeners;
	private ArrayList<Emitter> emitters;
	private String ip;
	private int port;

	public ScoutingSocket(String ip, int port) {
		receiveListeners = new ArrayList<CallbackListener>();
		connectListeners = new ArrayList<ConnectListener>();
		emitters = new ArrayList<Emitter>();
		this.ip = ip;
		this.port = port;
	}

	public void connect(Runnable callback) {
		connectListeners.add(new ConnectListener(callback));
	}

	public void on(String topic, Consumer<String> callback) {
		receiveListeners.add(new CallbackListener(topic, callback));
	}

	public void emit(String topic, String data, Consumer<String> callback) {
		emitters.add(new Emitter(topic, data, callback));
	}

	public void emit(String topic, String data) {
		emitters.add(new Emitter(topic, data));
	}

	public void listen() {
		Runnable connectLoop = () -> {
			Socket socket = null;
			Thread inputThread = null;
			Thread outputThread = null;
			while (true) {
				try {
					if (socket == null || socket.isClosed()) {
						socket = new Socket(ip, port);
						connected();
						inputThread = getInput(socket);
						outputThread = pushOutput(socket);
						inputThread.start();
						outputThread.start();
					}
				} catch (IOException e) {
					System.out.println("Cannot connect to socket at " + ip + ":" + port);
					if (socket != null) {
						try {
							socket.close();
							inputThread.interrupt();
							outputThread.interrupt();
						} catch (IOException e1) {
							e1.printStackTrace();
						}
						socket = null;
					}
				} finally {
					try {
						Thread.sleep(RECONNECT_TIME);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		new Thread(connectLoop).start();
	}

	private void connected() {
		for (ConnectListener c : connectListeners) {
			c.callback.run();
		}
	}

	private void error(Socket socket) {
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Thread getInput(Socket socket) {
		return new Thread(() -> {
			while (true) {
				if(socket == null) {
					break;
				}
				try {
					System.out.println("reading");
					BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					String input = "";
					String currentLine;
					while ((currentLine = br.readLine()) != null) {
						input += currentLine;
					}
					String topic = new JsonParser().parse(input).getAsJsonObject().get("type").getAsString();
					for(CallbackListener c : receiveListeners) {
						if(c.topic == topic) {
							c.callback.accept(input);
						}
					}
					// TODO: Get topic and return to listeners of that topic
					// TODO: Emitters need to be deleted here if their callback
					// func is called
					br.close();
				} catch (IOException e) {
					error(socket);
					break;
				}
			}
		});
	}

	private Thread pushOutput(Socket socket) {
		return new Thread(() -> {
			while (true) {
				if(socket == null) {
					break;
				}
				try {
					
					for (Emitter e : emitters) {
						//if(!e.emitted) {
							PrintWriter out = new PrintWriter(socket.getOutputStream());
							// TODO: emit a topic with this
							out.println(e.data);
							out.flush();
							e.emitted = true;
							System.out.println("writing");
					//	}
					}
				} catch (IOException e) {
					error(socket);
					break;
				} finally {
					try {
						Thread.sleep(OUTPUT_TIME);
					} catch (InterruptedException e) {
					}
				}
			}
		});
	}

	class CallbackListener {
		String topic;
		Consumer<String> callback;

		public CallbackListener(String topic, Consumer<String> callback) {
			this.topic = topic;
			this.callback = callback;
		}
	}

	class ConnectListener {
		Runnable callback;

		public ConnectListener(Runnable callback) {
			this.callback = callback;
		}
	}

	class Emitter {
		String topic;
		String data;
		Consumer<String> callback;
		boolean emitted = false;

		public Emitter(String topic, String data, Consumer<String> callback) {
			this.topic = topic;
			this.data = data;
			this.callback = callback;
		}

		public Emitter(String topic, String data) {
			this.topic = topic;
			this.data = data;
			this.callback = null;
		}
	}
}
