package jenkins.plugins.mattermost.workflow;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.stream.Collectors;

public class TestListener implements Runnable
{
	public final int port;
	public BlockingQueue<String> messages = new LinkedBlockingQueue<>();
	private String path = "/";

	public TestListener(int port)
	{
		this.port = port;
	}

	public TestListener(int port, String path)
	{
		this.port = port;
		this.path = path;
	}

	public static TestListener create(String path)
	{
		Random random = new Random();
		int port = (Math.abs(random.nextInt()) + 1024) % 65535;
		return new TestListener(port, path);
	}

	@Override
	public void run()
	{
		HttpServer server = null;
		try
		{
			server = HttpServer.create(new InetSocketAddress(port), 0);
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		assert server != null;

		server.createContext(path, new MyHandler());
		server.setExecutor(null); // creates a default executor
		server.start();
	}

	class MyHandler implements HttpHandler
	{
		@Override
		public void handle(HttpExchange t) throws IOException
		{
			String response = "This is the response";
			t.sendResponseHeaders(200, response.length());
			InputStream requestBody = t.getRequestBody();
			BufferedReader br = new BufferedReader(new InputStreamReader(requestBody, Charset.defaultCharset()));
			messages.addAll(br.lines().map(obj ->
			{
				try
				{
					return URLDecoder.decode(obj, "UTF-8");
				} catch (UnsupportedEncodingException ignored)
				{
				}
				return obj;
			})
					.collect(Collectors.toList()));
			OutputStream os = t.getResponseBody();
			os.write(response.getBytes());
			os.close();
		}
	}

}
